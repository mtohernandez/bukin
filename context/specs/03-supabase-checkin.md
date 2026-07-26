# Spec 03 — Supabase, Check-In, Roster, Manual Registration

**Session 3 of 3.** Closes the loop: the detected code becomes a verified row in
Postgres, and the demo is complete.

## Objective

A collaborator taps Check In, the observed code is validated server-side, and an
attendance row lands in Postgres. The host sees them appear on a live roster. If someone
can't participate, the host registers them manually.

## Read first

`CLAUDE.md` → `context/progress-tracker.md` (sessions 1–2 results, especially which
phone hosts) → `context/architecture.md` (the auth/access model and invariants) →
`context/code-standards.md` (the Supabase and security sections).

## Skills

`ponytail` before implementing. `kotlin-flow-state-event-modeling` for the roster stream
and one-shot events. `compose-side-effects` for the check-in submission.

## Non-negotiable facts

### Schema

Follows `docs/overview.md` §6, with two changes: `instancia` gains `instance_key bytea`
(the per-instance HMAC key), and `Outbox Event` is **omitted** — it stays in the
architecture document, not the demo database.

```
curso        (curso_id, nombre, duracion_minutos, modalidad)
instancia    (instancia_id, curso_id, fecha_inicio, fecha_fin, fecha_extension,
              estado, instance_key bytea)
colaborador  (colaborador_id, nombre_completo, email, rut)
inscripcion  (inscripcion_id, instancia_id, colaborador_id, aprobado, asistencia,
              fecha_aprobacion, origen, fecha_llegada, fecha_salida,
              metodo_confirmacion, atestiguado_por_id)
```

`estado` ∈ `PROGRAMADO | ABIERTO | CERRADO`. `origen` ∈ `PRE_INSCRITO | WALK_IN`.
`metodo_confirmacion` ∈ `AUTO | BLE | MANUAL`.

`codigo_ble` from the original ER model is **dropped**. It described a single static code
per instance, which is precisely the design that made screenshot-sharing possible.
`instance_key` plus the 30-second rotation replaces it.

**Critical:** `UNIQUE (colaborador_id, instancia_id)` on `inscripcion`. This constraint
is what makes idempotency and concurrency work — it is not optional.

### Key distribution — the key travels UP, never down

**No endpoint may ever return `instance_key`.** With no authentication, an RPC that hands
out the key to whoever asks would let anyone generate valid codes from anywhere, which
destroys the entire security model — worse than the relay attack, because it needs no
physical presence at all.

So the key originates on the host device and flows one way:

```
HOST                                    SERVER
────                                    ──────
"Abrir sesión" tapped
  key = 16 random bytes          ─────▶ abrir_instancia(instancia_id, key)
  (SecureRandom)                          store key, set estado = 'ABIERTO'
  keep in memory only            ◀─────   return server_now
  clock_offset = server_now - now
```

The host holds the key in memory for the session and never persists or displays it. An
attacker cannot obtain the key for a session someone else is hosting — they can only
open their own, which registers nothing against the real instance.

### `abrir_instancia`

`abrir_instancia(p_instancia_id, p_key bytea) returns timestamptz`, `SECURITY DEFINER`.
Stores the key, sets `estado = 'ABIERTO'`, returns `now()`.

This closes a gap: `docs/overview.md` §2.2 assumes a background job flips
`PROGRAMADO → ABIERTO` ten minutes before start. **There is no background worker in this
demo**, so without this RPC nothing ever opens an instance and every check-in fails the
window test. The host's "start session" action is what opens it.

Returning server time also lets the host correct its own clock — see below.

### Clock offset

The host derives codes from its own clock, so a host phone with a wrong clock generates
codes the server rejects *every time*, with no symptom a user could diagnose. Android
phones drift 0.5–2 s/day and sync over NTP by default, so normal devices are fine — but
a phone with automatic time disabled can be minutes off.

The host stores `clock_offset = server_now - device_now` from `abrir_instancia` and
applies it when computing the counter. One subtraction, and an entire class of silent
total failure disappears.

Show the offset on the diagnostics screen. If it exceeds one window, say so plainly.

### The RPC

`confirmar_asistencia(p_instancia_id, p_colaborador_id, p_code bytea)`,
`SECURITY DEFINER`, `SET search_path = public, extensions`.

1. Load the instance. Reject unless `estado = 'ABIERTO'` and now is inside the window
   (`fecha_inicio - 10 min` to `coalesce(fecha_extension, fecha_fin) + 20 min`) → 409.
2. Recompute the code for `counter - 1`, `counter`, **and `counter + 1`**. Mismatch → 403.
   RFC 6238 §5.2 recommends accepting ±1 time step; a host whose clock runs slightly
   fast produces `counter + 1` codes, and rejecting those would fail legitimate
   check-ins for no visible reason.
3. Idempotent upsert:

```sql
INSERT INTO inscripcion (instancia_id, colaborador_id, origen,
                         fecha_llegada, metodo_confirmacion, asistencia)
VALUES (p_instancia_id, p_colaborador_id, 'WALK_IN', now(), 'BLE', true)
ON CONFLICT (colaborador_id, instancia_id) DO UPDATE
  SET fecha_llegada = now(),
      metodo_confirmacion = 'BLE',
      asistencia = true
  WHERE inscripcion.fecha_llegada IS NULL;
```

The INSERT branch handles walk-ins (R2); the UPDATE branch handles pre-enrolled
attendees. The `WHERE fecha_llegada IS NULL` guard means a second confirmation touches
nothing and still returns success (R4) — **no error is ever shown to the user for
double-tapping.**

Code recomputation in SQL:

```sql
substring(
  extensions.hmac(
    <instancia_id bytes> || <counter bytes>,
    v_instance_key,
    'sha256'
  ) from 1 for 8
)
```

`pgcrypto` ships enabled on Supabase. The byte serialization of `instancia_id` and
`counter` **must match the Kotlin implementation exactly** — big-endian, same widths.
This is the most likely bug in the session; test it against the known vector from spec
02 before wiring any UI.

### RLS posture

The anon key ships inside the APK and is public. Therefore:

- `ALTER TABLE … ENABLE ROW LEVEL SECURITY` on every table
- **No policies at all** — deny-all
- `REVOKE ALL ON <tables> FROM anon`
- `GRANT EXECUTE ON FUNCTION confirmar_asistencia… TO anon`

Every read and write goes through validated functions. If a direct table query works
from the client, the migration is wrong.

Reads the app needs (the instance, the roster, the collaborator list) each get their own
`SECURITY DEFINER` function rather than a table grant.

### Client

`supabase-kt` — Supabase BOM, `postgrest-kt`, Ktor Android engine. Requires minSdk 26.
Add `<uses-permission android:name="android.permission.INTERNET" />`.

## File manifest

```
supabase/
  migrations/0001_schema.sql
  migrations/0002_functions.sql          ← RPCs
  migrations/0003_rls.sql                ← enable RLS, revoke, grant execute
  seed.sql                               ← one curso, one instancia, ~8 colaboradores
core/data/
  build.gradle.kts
  src/main/kotlin/.../SupabaseClient.kt
  src/main/kotlin/.../dto/                ← DTOs stay in this module
  src/main/kotlin/.../AsistenciaRepository.kt
  src/main/kotlin/.../InstanciaRepository.kt
domain/
  src/main/kotlin/.../usecase/ConfirmarAsistencia.kt
  src/main/kotlin/.../model/ResultadoConfirmacion.kt   ← sealed: Ok | YaRegistrado | CodigoInvalido | FueraDeVentana | SinRed
features/checkin/
  ...CheckInViewModel.kt                 ← submit on tap, map result to state
features/host/
  ...HostRosterScreen.kt                 ← live arrivals
  ...ManualRegistrationScreen.kt         ← search + register
```

## What to build

### Migrations and seed

Three append-only migrations plus a seed with one course ("Manejo de alimentos",
2 horas), one instance in `ABIERTO` with a generated `instance_key`, and about eight
collaborators — some pre-enrolled, some not, so both upsert branches are demonstrable.

Verify the RPC directly in the SQL editor before touching Kotlin: a valid code, a
one-window-stale code (accepted), a two-window-stale code (rejected), a wrong code
(rejected), and a double call (one row, success both times).

### Repositories

`:core:data` owns the client. DTOs never cross the module boundary — repositories return
`:domain` types.

### Wire check-in

On tap: submit **the most recently observed code**, not the one first detected. A person
can see the host, get distracted, and tap a minute later — the original code would be
two windows stale and rejected. The scanner from session 2 keeps the latest observation;
read it at submission time.

If the last observation is older than one rotation window, treat it as no longer present:
return to SCANNING rather than submitting a code that will certainly be rejected.

Map the result
to screen state — `Ok` and `YaRegistrado` both render SUCCESS (the user does not care
about the difference, and the assessment explicitly wants no error on a duplicate).
`CodigoInvalido` and `FueraDeVentana` get their own clear Spanish messages. `SinRed`
gets the offline treatment.

Disable the button while in flight. Never let a double tap fire two requests.

### Host roster

Poll the roster every few seconds while the session is open — polling is the right
choice here over Realtime: fewer moving parts, no extra dependency, and the demo lasts
minutes. Show names as they arrive, with arrival time and method.

Keep the query narrow — select only what the list renders. This is the one query whose
cost grows with attendance: at 300 people it returns 300 rows every few seconds, and
it is the only part of the backend that notices the difference between a small class
and a full auditorium.

### Manual registration

Search the collaborator list, register presence with `metodo_confirmacion = 'MANUAL'`
and `atestiguado_por_id` set to the host for audit. This is the documented fallback for
a dead or incompatible phone and closes the biggest hole in a BLE-only design.

Its own RPC, same `SECURITY DEFINER` and RLS treatment.

### Collaborator selection

With no auth, the collaborator picks themselves from a list on first run and it is
remembered. Keep it plain — it is scaffolding, not a feature.

## Acceptance criteria

1. The SQL known-vector test agrees with the Kotlin implementation from spec 02.
2. Two phones: host opens a session, collaborator goes SCANNING → READY → tap →
   SUCCESS, and the row is in Postgres with `metodo_confirmacion = 'BLE'`.
3. Tapping Check In twice yields **one row and no error screen**.
4. A collaborator with no prior enrollment gets a row with `origen = 'WALK_IN'`.
5. A stale code (older than two windows) is rejected with a clear message.
6. A direct table query with the anon key is denied.
7. The host roster shows the arrival within a few seconds.
8. Manual registration writes `MANUAL` with `atestiguado_por_id` set.
9. Airplane mode on the collaborator's phone produces the offline state, not a crash.

## Verification

```bash
./gradlew testDebugUnitTest
./gradlew installDebug   # both phones
```

Full rehearsal in one room, then confirm the rows in the Supabase dashboard. Run the
replay check: capture a code, wait a minute, submit it — it must be rejected. That
single test is the proof the security model works, and it is worth demonstrating live.

## Out of scope — do not build

Check-out / "Me retiro". Outbox, queue, DLQ. JWT auth. BLE offline relay. Admin panel,
reporting, certificates. The web download page. Realtime subscriptions — polling is
enough.

## On completion

Update `context/progress-tracker.md` to Complete, record the demo results, and note any
limitation worth stating out loud in the presentation — particularly the relay-attack
limit from `architecture.md`, which is stronger disclosed than discovered.

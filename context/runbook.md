# Runbook

How to bring the whole thing up and re-run every verification. Everything here was executed
on 2026-07-26 and the output quoted is real. Read `hardware-constraints.md` first for what
the hardware is and what a claim about it requires.

## Secrets — where they are not

Nothing in this repository contains a credential except the Supabase **publishable** key,
which is in `core/data/.../BukInBackend.kt` on purpose: it ships inside the APK and is public
by design, and is safe only because every table is deny-all RLS behind validated functions
(`CLAUDE.md` hard rule 3).

Two things are needed and must come from the human, per session:

| Value | Used for | Never |
| --- | --- | --- |
| Database password | `psql`, `supabase db push` | committed, or put in `.env` |
| `sb_secret_…` | not used by anything here | committed, or shipped in the APK |

The publishable key was confirmed to be enough for every client path, so nothing in the app
ever needs the secret one.

```bash
export PGPASSWORD='<db password>'
export BUKIN_PG="postgresql://postgres.nfysenajrfquusawyotc@aws-1-us-west-2.pooler.supabase.com:5432/postgres?sslmode=require"
```

**The pooler host is `aws-1-us-west-2`.** The project region reads "West US (Oregon)", so
`us-west-1` looks right and is not — it and `aws-0-us-west-2` both answer
`FATAL: (ENOTFOUND) tenant/user postgres.nfysenajrfquusawyotc not found`. Direct
`db.<ref>.supabase.co` resolves to IPv6 only and is unreachable from this machine. Half an
hour was lost to this; do not rediscover it.

## Database

```bash
supabase link --project-ref nfysenajrfquusawyotc     # SUPABASE_DB_PASSWORD in the env
supabase db push                                     # migrations are append-only
psql "$BUKIN_PG" -f supabase/seed.sql                # `db push` does NOT run the seed
psql "$BUKIN_PG" -f supabase/tests/rpc_test.sql      # 19 cases, rolls back, leaves nothing
```

The test file is the regression gate for the RPCs and runs safely against the live project —
it does all its work inside a transaction it rolls back. Expect:

```
 pasaron | fallaron | veredicto
---------+----------+------------
      19 |        0 | TODO VERDE
```

Seed times are relative to `now()`, so re-seeding always produces one instance inside its
window, one later today, and one that finished yesterday. That is what makes "availability is
decided by the clock" visible rather than something to take on trust.

**Re-seeding orphans every installed phone.** Each one holds a `colaborador_id` that no
longer exists. The app heals this itself now — the session list re-issues the remembered name
through `identificar_colaborador` before it lists anything — but if a check-in ever fails
with a foreign key violation, this is why.

## Deploy to the phone

```bash
export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"
adb devices -l
export ANDROID_SERIAL=adb-RZCWA02ZSHV-0v4jBa._adb-tls-connect._tcp   # or <ip>:<port>
./gradlew installDebug
```

The phone shows up **twice** — once as `192.168.111.163:35487` and once under its mDNS name —
and Gradle fails on the ambiguity. Pin one with `ANDROID_SERIAL`. The mDNS name survives the
port changing; the IP form does not.

Read the screen with `adb exec-out uiautomator dump /dev/tty`, not `android screen capture`
(which produced no file over the wireless device). Screenshots: `adb exec-out screencap -p`.

## The Mac beacon

```bash
cd tools/mac-ble
swiftc -O BukInProtocol.swift beacon.swift -o beacon
./beacon --instancia <id> --key 000102030405060708090a0b0c0d0e0f
```

It self-checks the known vector against `:domain` on every start and exits 10 if it no longer
matches, so a mismatch cannot be missed:

```
self-check OK — known vector 67e94bf8a08959ea matches :domain
```

A beacon is useless until the server holds the same key for that instance, because
`confirmar_asistencia` verifies against `instance_key`. Either open the room from the phone
(which generates its own key and uploads it), or open it by hand with the vector key:

```bash
psql "$BUKIN_PG" -c "select abrir_instancia(<id>, '000102030405060708090a0b0c0d0e0f');"
```

## Re-running the verifications

| Claim | Command | Expected |
| --- | --- | --- |
| Codec correct | `./gradlew test` | 26 tests, 0 failures. **Never `testDebugUnitTest`** — `:domain` is `kotlin-jvm`, has no such task, and the command exits successfully having run nothing |
| Compiles | `./gradlew assembleDebug` | — |
| RPCs correct | `psql "$BUKIN_PG" -f supabase/tests/rpc_test.sql` | `19 / 0 / TODO VERDE` |
| SQL agrees with Kotlin | test 1 of that file | `67e94bf8a08959ea` |
| Tables unreachable | see below | `42501` on all four |

```bash
U=https://nfysenajrfquusawyotc.supabase.co
K=sb_publishable_E9INM8PvMJpKKKOjaqTP8Q_HWk0_tqS
for t in curso instancia colaborador inscripcion; do
  curl -s "$U/rest/v1/$t?select=*" -H "apikey: $K"; echo
done                       # every one: 42501 permission denied
curl -s -X POST "$U/rest/v1/rpc/ventana_activa" -H "apikey: $K" \
  -H "Content-Type: application/json" -d '{}'   # ungranted helper: also denied
curl -s -X POST "$U/rest/v1/rpc/listar_instancias" -H "apikey: $K" \
  -H "Content-Type: application/json" -d '{}'   # granted RPC: returns rows
```

### The live cross-check, which is stronger than the fixed vector

With the phone hosting a room it opened itself — key from `SecureRandom`, uploaded by
`abrir_instancia`, never fetched back — compare what the screen shows against what Postgres
derives from the stored key. It must match, and it did:

```bash
PHONE=$(adb exec-out uiautomator dump /dev/tty | grep -o 'text="[0-9a-f]\{16\}"' | head -1 | sed 's/text="//;s/"//')
SQL=$(psql "$BUKIN_PG" -tAc "select encode(substring(extensions.hmac(
  int4send(<id>) || int8send(floor(extract(epoch from now())/30)::bigint),
  instance_key,'sha256') from 1 for 8),'hex') from instancia where instancia_id=<id>;")
[ "$PHONE" = "$SQL" ] && echo MATCH
```

Do it inside one 30-second window or it will compare across a rotation. Note that
`tr -d 'text="'` deletes every `e` in the hex — use `sed`.

### Replay, the one worth demonstrating live

Take a code from the beacon log, submit it (accepted), wait past two windows, submit the same
code again as a different person. Second attempt returns `CODIGO_INVALIDO` and writes no row.
Observed: accepted 14:22:53, rejected 14:24:37.

### The offline test costs you the debugger

**`adb` runs over the same Wi-Fi the test disables.** `svc wifi disable` drops the connection
mid-command and the phone cannot be observed while it is offline. Script the whole sequence
on-device and read the result once it comes back:

```bash
adb shell svc data disable        # or Wi-Fi off still leaves mobile data
adb shell "nohup sh -c 'svc wifi disable; sleep 6; input tap 540 1372; \
  sleep 18; svc wifi enable; sleep 22; input tap 540 1372' >/dev/null 2>&1 &" &
sleep 95
adb connect 192.168.111.163:35487        # Samsung re-associates on its own; mDNS finds it
adb shell svc data enable
```

That taps Check In while offline, restores Wi-Fi, and taps again. Expected: the "Sin conexión"
card appears, **clears itself** when the network validates, and the second tap writes the row.
The end state is observable; the transition is not.

## Current state of the demo database

Instances 1–3 from the seed plus anything created since. Instance 1 ("Manejo de alimentos")
has five enrolments, two of them marked present over BLE from real device runs. Test
identities from verification runs were removed; the `atestiguado_por_id` foreign key blocks
deleting anyone who witnessed a manual registration, which is the audit trail working — reset
the witnessed row before deleting the witness.

## Where the remaining gaps are

Recorded so they are not rediscovered as surprises. Full list in `progress-tracker.md`.

- Android host → Android collaborator has never been exercised end to end. Both radios are
  never Android at once.
- Range and crowd behaviour are not measurable on this hardware. Do not claim them.
- `NET_CAPABILITY_VALIDATED` is the right signal for a captive portal but was not tested
  against one.
- No load test. Concurrency is safe by construction — `UNIQUE (colaborador_id, instancia_id)`
  means 300 upserts contend for nothing — but that is an argument, not a measurement.

# Architecture Context

## Stack

| Layer      | Technology                                | Role                                          |
| ---------- | ----------------------------------------- | --------------------------------------------- |
| Language   | Kotlin                                    | Everything on device                          |
| UI         | Jetpack Compose + Material 3              | All screens; no XML layouts                   |
| Navigation | Navigation 3                              | Back stack, screen routing                    |
| Build      | Gradle multi-module + version catalog     | Module boundaries, build cache                |
| Proximity  | `BluetoothLeAdvertiser` / `BluetoothLeScanner` | Physical presence proof                  |
| Backend    | Supabase — Postgres + PostgREST           | Storage and code verification. No server code.|
| Crypto     | `javax.crypto.Mac` on device, `pgcrypto` in Postgres | Rotating code generation and validation |

Verified versions (July 2026): AGP 9.2.0 · Kotlin 2.3.21 · Compose BOM 2026.06.01
(Material3 1.4.0, compose-ui 1.11.4) · Navigation 3 stable 1.0. The Compose Compiler
plugin version tracks the Kotlin version. Scaffold with the `android` CLI and let it
resolve transitive versions rather than hand-pinning.

## Module graph

Strictly top-down. A module may only depend on modules below it. No cycles, no
cross-feature imports.

```
:app                        DI wiring, root navigation, manifest. Glue only.
  │
  ├── :features:onboarding  3-screen intro
  ├── :features:checkin     collaborator flow (the four states)
  └── :features:host        session control, live roster, manual registration
        │
        ├── :domain         pure Kotlin. Models, the rotating-code codec, use cases.
        │                   No Android imports. Unit-testable on the JVM.
        │
        └── :core:ble       advertiser, scanner, permission preflight, FG service
            :core:data      Supabase client, DTOs, repositories
            :core:designsystem  theme, tokens, TicketCard, shared composables
```

Rules:

- `:domain` has **no Android dependencies**. The HMAC codec lives here so it is testable
  without a device.
- Features never import each other. Shared UI goes to `:core:designsystem`, shared
  models to `:domain`.
- `:app` contains no business logic.

The original architecture note in `docs/architecture.md` describes splitting each
feature into `public-api` + `implementation` modules. That is the right pattern for a
large team and the wrong one for a three-session demo — it doubles the module count for
boundaries a single developer can hold in their head. **Not adopted.** Revisit if the
app grows past these three features.

## The proximity mechanism

```
HOST                                    COLLABORATOR
────                                    ────────────
every 30s:                              BluetoothLeScanner
  counter = unix_seconds / 30             filter: UUID prefix "BUKN" + mask
  code = HMAC-SHA256(key,                 ↓
         instancia_id || counter)[0..7]   parse instancia_id + code
  advertise service UUID:                 ↓
    BUKN | instancia_id | code  ──────▶   READY state, button unlocks
    (16 bytes, one 128-bit UUID)          ↓
                                        POST rpc/confirmar_asistencia
                                          ↓
                                        POSTGRES recomputes code for
                                        counter and counter-1.
                                        mismatch → reject. match → upsert.
```

The host holds the per-instance key; the collaborator never sees it and only relays an
opaque 8-byte code. Verification happens in the database, not on either phone.

The payload rides **inside the 128-bit service UUID** rather than in a service-data
field. That is not arbitrary — see the iOS section below.

## Scale

### Radio

The host broadcasts; it never connects. Scanners are passive receivers and transmit
nothing back, so the number of listeners is irrelevant to the host's radio — 5 phones
and 300 phones cost it identically. Broadcast is the reason this design reaches
auditorium size at all.

This is why GATT was rejected. Android caps concurrent GATT connections at roughly
seven; a connection-based design fails at 8 people, let alone 300. It is also why the
advertisement must stay **non-connectable with no scan response** (`ADV_NONCONN_IND`):
adding a scan response would invite a `SCAN_REQ` from every active scanner in range,
turning 300 silent listeners into 300 transmitters competing on three advertising
channels.

The binding constraint is **range, not capacity.** BLE reaches roughly 30–50 m indoors
at high TX power, but each human body in the path costs 3–5 dB and a pocketed phone
costs more. A packed 300-person hall can exceed that budget before it exceeds anything
in the architecture.

The mitigation is operational and already implied by the design: **any device holding
the instance key can advertise the same code.** A co-host at the back of the room is a
second beacon, and a collaborator cannot tell which one it heard. No protocol change,
no code change on the collaborator side.

### Database

300 confirmations arriving over a few minutes is roughly 1 request/second with a burst
at the start — far below anything the free tier strains at (60 direct connections, 200
pooler connections, and PostgREST maintaining its own internal pool). Payloads are a few
hundred bytes; 300 of them is negligible against 5 GB of egress.

Concurrency is safe by construction, which is what R6 asks for: every confirmation
touches a different row, uniqueness is enforced per row by
`UNIQUE (colaborador_id, instancia_id)`, and there is no shared counter or aggregate to
serialize on. 300 simultaneous upserts contend for nothing.

The one thing that grows with attendance is the host's roster poll returning 300 rows
every few seconds. Keep that query narrow.

## iOS portability

The collaborator role ports cleanly. iOS scans for service UUIDs and reads them from an
Android advertiser without difficulty, and background scanning works when the service
UUID is specified — which this design does.

The host role is where iOS is restrictive, and it dictated the payload format.
`CBPeripheralManager` accepts only `CBAdvertisementDataLocalNameKey` and
`CBAdvertisementDataServiceUUIDsKey`, and
[errors on any other key](https://developer.apple.com/documentation/corebluetooth/cbperipheralmanager).
An iPhone **cannot advertise service data or manufacturer data at all.** Had the payload
lived in a service-data field, the host role would have been permanently Android-only.
Encoding it in the service UUID costs nothing on Android and keeps the door open.

One limitation remains and cannot be engineered away: when an iOS app is backgrounded,
its service UUIDs move to an undocumented Apple "overflow area" that
[non-Apple devices cannot decode](https://github.com/davidgyoung/ios-overflow-area). An
iOS host must therefore keep the app in the foreground. For someone actively running a
class that is an acceptable constraint, and Android hosts are unaffected.

Summary of what a future iOS port inherits:

| Role         | iOS status                                                        |
| ------------ | ----------------------------------------------------------------- |
| Collaborator | Works. Scanning for a known service UUID is well-supported.        |
| Host         | Works **in the foreground only**, because of the overflow area.    |
| Payload      | Already compatible — this is why it lives in the UUID.             |
| Crypto       | `CryptoKit` HMAC-SHA256. Same 8-byte truncation, same test vector. |

## System boundaries

- `:core:ble` — owns every Bluetooth API call. No other module touches
  `BluetoothAdapter`. Exposes Flows of typed state, never raw callbacks.
- `:core:data` — owns the Supabase client and all network I/O. Exposes suspend
  functions returning domain types, never DTOs.
- `:domain` — owns the code derivation and business rules. Knows nothing about
  Bluetooth transport or HTTP.
- `:core:designsystem` — owns colors, type, spacing, and shared components. The only
  place a hex value may appear.
- `supabase/` — SQL migrations. The schema and the verification function.
- `docs/` — human input. Read-only.

## Storage model

- **Postgres (Supabase)** — the system of record. `curso`, `instancia`, `colaborador`,
  `inscripcion`. The attendance mark is a column on `inscripcion`, not a separate
  table, so the `UNIQUE (colaborador_id, instancia_id)` constraint is what enforces
  idempotency.
- **On device** — nothing durable. No Room, no local queue. The demo has no offline
  write path; the offline screen state sets expectations rather than buffering writes.

## Auth and access model

There is **no authentication**. This is a deliberate scope cut, and it changes the
security posture in a way that must be handled explicitly:

- The collaborator is selected from a list, not logged in. `colaborador_id` is chosen
  on device — which would be forgeable, and is acceptable only because this is a demo.
- The Supabase **anon key ships inside the APK** and must be assumed public.
- Therefore: RLS is enabled on every table with **no policies at all** (deny-all),
  table grants are revoked from `anon`, and `anon` is granted `EXECUTE` on exactly one
  function. Every read and write flows through that validated function.
- The function is `SECURITY DEFINER` with a pinned `search_path`.

In production this is replaced by the JWT model in `docs/overview.md` §2.1, where
`colaborador_id` is extracted from the token and never transmitted. The RPC boundary is
shaped so that swap is a change of one argument.

## What proximity does and does not prove

Rotating codes defeat:

- Sharing a screenshot or texting the code to someone across town
- Replaying a captured code after its window closes
- Spoofing the beacon without the per-instance key

They do **not** defeat a real-time radio relay — forwarding the live broadcast over the
internet to a confederate who checks in remotely. This attack has been demonstrated
against BLE attendance systems with a Raspberry Pi
([IEEE 8555557](https://ieeexplore.ieee.org/document/8555557/)). It is a known,
accepted limitation. Mitigations available but not all built: short rotation window
(built), an RSSI floor, and the host's live roster as a human cross-check (built).

**Do not claim this system is relay-proof.** Stating the limit honestly is worth more
in an architecture review than overclaiming.

### The larger hole: identity

Worth stating before anyone else finds it. BLE proves that *a phone* was in the room. It
never proves *whose*. With no authentication, `colaborador_id` is selected from a list,
so a person standing legitimately in the room can select a colleague's name and register
that colleague's attendance. **No proximity technology addresses this** — only
authentication does, which is the deliberate v1 cut described above.

This is a bigger practical hole than the relay attack and it is cheap to close later:
the RPC boundary is shaped so that swapping a client-supplied `colaborador_id` for one
extracted from a JWT is a one-argument change.

Disclose both limits in the presentation. A reviewer who hears them from you reads it as
rigor; a reviewer who discovers them reads it as a gap. Neither is surfaced in the app
UI — see `progress-tracker.md` for why.

## Corrections to earlier notes

An earlier draft of `docs/overview.md` recommended `corona-warn-app/cwa-app-android` as
a BLE reference. **It is not usable as one**, and the recommendation has been removed at
the source. CWA implements no Bluetooth itself — it delegates to the Google/Apple
Exposure Notifications API, which was allowlist-gated to government health authorities
and removed from Play Services in November 2023. All proximity code here is raw platform
BLE. If the suggestion resurfaces, this is why it was dropped.

## Not built

These live in the architecture document and the presentation, not in the codebase.
Do not implement them without an explicit scope change.

| Component            | Why it is documented but absent                       |
| -------------------- | ----------------------------------------------------- |
| Outbox table + worker| No external course system exists to propagate to.     |
| Message queue        | Same.                                                 |
| JWT auth             | Scope cut. See above.                                 |
| BLE offline relay    | Highest-risk, lowest-demo-value part of the design.   |
| Check-out flow       | Automatic check-out is unsolved; check-in is priority.|
| Web download page    | A Vite + React single page whose only job is an APK button. Slots in at `web/` later. |

## Invariants

1. Only `:core:ble` touches Bluetooth APIs. Only `:core:data` performs network I/O.
2. `:domain` never imports from `android.*`.
3. Features never depend on other features.
4. The rotating code is verified **server-side**. A client that says "the code was
   valid" is not trusted; it sends the code and lets Postgres judge.
5. Attendance writes are idempotent. A second confirmation returns success and changes
   nothing — it never surfaces an error to the user.
6. No table is directly reachable with the anon key. Everything goes through the RPC.
7. Every BLE failure has a distinct, actionable Spanish message. No silent failures, no
   permanently disabled buttons with no explanation.

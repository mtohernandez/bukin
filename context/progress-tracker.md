# Progress Tracker

Update this file after every meaningful implementation change.

## Current phase

**Session 5 delivered the design overhaul.** All fifteen units of `specs/04-design.md` are
built; the app was walked end to end on the Galaxy A54 over wireless adb with the Mac as the
host beacon. See "Session 5 results" below for what was verified, what was deviated from, and
the three criteria that could not be run in this environment.

**All three build sessions delivered.** A collaborator types their name, signs into a
session, waits for its hour, and one tap puts a verified row in Postgres. The host names and
opens a room, watches arrivals appear on a live roster, and can register someone by hand.
Every table is unreachable with the key that ships in the APK.

**Criterion 0 passed.** The Galaxy A54 decodes a macOS-originated 128-bit service UUID.
There is no Apple overflow-area problem for a foreground macOS process, the Mac-as-beacon
plan holds, and netsim was never needed.

**Session 4 added a design spec and changed no code.** `context/specs/04-design.md` is eleven
units of design work, and it fixes three measured accessibility failures that are shipping
today alongside the visual overhaul. `context/ui-context.md` was rewritten as the standing
token authority.

**Start here next session:** `context/ui-context.md` is the standing token authority and now
matches the code. The open items are the three unrun criteria at the end of the session-5
section. `context/runbook.md` is still the operational reference — the pooler
host that is not the obvious one, how to seed and re-run the 19 RPC tests, the Mac beacon, the
live phone-vs-Postgres code cross-check, and the fact that the offline test disconnects `adb`
because `adb` is the Wi-Fi.

## Roadmap

| Session | Spec                              | Outcome                                              |
| ------- | --------------------------------- | ---------------------------------------------------- |
| 1       | `specs/01-foundation.md`          | Project builds, theme matches mockups, onboarding runs |
| 2       | `specs/02-ble-proximity.md`       | Mac beacons, phone detects the live code; flipped to verify the host path |
| 3       | `specs/03-supabase-checkin.md`    | End-to-end check-in landing in Postgres. **Done** — results below |
| 4       | `specs/04-design.md`              | Design spec authored. Research + audit only, no code. **Done** — results below |
| 5       | `specs/04-design.md`              | Execute the design spec. **Done** — results below |

## Hardware constraint change (2026-07-26)

The plan assumed two Android phones and a USB cable. **Neither exists.** There is one
phone (Android 11+), no USB-C to USB-C cable, and no second Android device.

`context/hardware-constraints.md` is the new authority and overrides every older
"two physical phones" statement. Resolved as follows:

| Problem | Resolution |
| --- | --- |
| No cable | **Solved and in use.** Wireless adb — paired and connected to the Galaxy A54 (Android 16) on 2026-07-26; `installDebug` deployed the real APK. Both endpoints are discoverable via `adb mdns services`, so no port needs to be read off the phone; only the 6-digit pairing code must come from a human. Fallback is `python3 -m http.server` on the 12 MB APK. |
| No second radio | **The Mac is the second BLE radio.** Verified on this M4: CoreBluetooth advertises a custom 128-bit UUID in the BukIn layout, `isAdvertising = true`. Probe preserved at `tools/mac-ble/advertise-probe.swift`. |
| Which side the phone plays | Phone = collaborator (the primary flow), Mac = host beacon. Host path verified by flipping: phone advertises, Mac scans and decodes. |
| Reporting discipline | Evidence table + reporting rules in `hardware-constraints.md`. Hardware named in every claim; unrun steps reported as unrun. |

**The unresolved risk, and it gates session 2:** it is verified that the Mac *transmits*;
it is **not** verified that Android can *decode* it. Apple hardware sometimes moves 128-bit
UUIDs into an undocumented overflow area. That check is criterion 0 of spec 02 and must
pass before any BLE code is written on top of it. Backup if it fails is netsim/Rootcanal
(`~/Library/Android/sdk/emulator/netsimd`), which is **entirely unverified** — timebox it.

Why the Mac works at all: the payload rides inside the 128-bit service UUID, a decision
made in planning for iOS portability because `CBPeripheralManager` refuses service data.
That same constraint is what lets a Mac emit a byte-correct BukIn advertisement. The
architecture already sanctions it — "any device holding the instance key can advertise the
same code."

## Session 2 results (2026-07-26)

All hardware claims below are on the **Samsung Galaxy A54 5G (SM-A546E), Android 16 / API
36, over wireless adb**, with the **M4 Mac** as the second radio. Nothing here was observed
on an emulator.

### Acceptance criteria

| # | Criterion | Result |
| --- | --- | --- |
| 0 | Phone decodes a macOS advertisement | **PASS.** `42554b4e-0001-0002-0003-a1b2c3d4e5f6` at ~-55 dBm, several times a second |
| 1 | Known-vector codec test passes | **PASS.** 22 tests, 0 failures — but see the `testDebugUnitTest` trap below |
| 2 | Mac beacons → phone SCANNING→READY unaided | **PASS.** READY 2.6 s after the beacon started, no input |
| 3 | Beacon stops → back to SCANNING | **PASS.** at 12.4 s (10 s grace + UI-dump latency) |
| 4 | Flipped: phone advertises, Mac decodes | **PASS.** `instancia_id=42 code=e2d9fc66f227c1ba`, identical to the phone's own display |
| 5 | Advertising survives screen lock | **PASS.** locked 13:27:29 (`mWakefulness=Dozing`), Mac still receiving at 13:28:09 |
| 6 | Every preflight failure has its own Spanish message | **PASS.** all four observed on the phone |
| 7 | No location permission requested | **PASS.** system package record lists only the three Bluetooth runtime permissions |
| 8 | No `ADVERTISE_FAILED_DATA_TOO_LARGE` | **PASS.** no advertise failure of any kind in logcat |
| 9 | No leaked scan callback | **PASS.** `LE scans (started/stopped): 9 / 9`, no ongoing scans after 5 in/out rounds |

**Every criterion was run. None is unmet, and none was downgraded.**

### The phone can advertise

`isMultipleAdvertisementSupported = true` on the A54. This closes the open question from
session 1: the Android host path is verifiable on this hardware, and criteria 4 and 5 above
are what verified it.

The platform's own advertising log shows restarts at exactly `13:17:30 → 13:18:00 →
13:18:30`, each with a matching stop — boundary-aligned rotation, no leaked advertise
callback. Scan config as specced:
`ScanFilter[ServiceUuid=42554b4e-… ServiceUuidMask=ffffffff-…]`, `ScanMode=LOW_LATENCY`.
Foreground service confirmed live with `types=0x00000010`
(`FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE`).

### `./gradlew testDebugUnitTest` does not run the codec test

`:domain` is a `kotlin-jvm` module, so it has **no `testDebugUnitTest` task at all**. Every
Android module reports `NO-SOURCE`, so the command exits `BUILD SUCCESSFUL` in under a
second having executed nothing. The gate that actually covers the codec is:

```bash
./gradlew test          # runs :domain:test — 22 tests, 0 failures
```

`CLAUDE.md` and spec 02 both name `testDebugUnitTest`. Treat that as a documentation bug,
not a passing gate — it is exactly the kind of green tick this project exists to distrust.

### Two bugs the device found that a compile never would

Both were invisible to `assembleDebug` and to unit tests, and both would have surfaced on
demo day.

1. **Recovery from any error state was stuck.** The scan branch only emitted on a
   `ScanEvent`. In an empty room nothing is emitted, so the `StateFlow` kept its previous
   value — switching Bluetooth back on looked like it did nothing, permanently. Fixed with
   `onStart { emit(Scanning) }`: entering the scanning branch is itself the news, whether
   or not anything has been heard yet.
2. **A first-time host was sent to Settings instead of the permission dialog.**
   `shouldShowRequestPermissionRationale` returns false both for "permanently denied" and
   for "never asked", and the host screen tested it before ever requesting. Fixed by only
   setting the flag inside the launcher result callback, which is what the check-in screen
   already did correctly.

### Verified NOT verifiable on this hardware — do not claim otherwise

- **Android host → Android collaborator is never exercised.** Both radios are never Android
  at once. Every host-path claim above rests on the Mac decoding the phone.
- **Range and crowd-scale behaviour.** One phone, one room, ~-50 dBm across a desk. The
  30–50 m figure and the 3–5 dB per-body attenuation remain unmeasured.
- **Non-connectable advertising from the Mac.** `setConnectable(false)` is set on the
  Android side and the packet is `ADV_NONCONN_IND` there, but CoreBluetooth cannot
  reproduce it, so the Mac beacon is not a faithful stand-in for the scale argument.
- **TX power and advertising interval.** CoreBluetooth exposes neither.

## What session 3 must account for

- **The instance key is generated on the host and never leaves it.** `HostViewModel` makes
  16 bytes from `SecureRandom` on start and drops them on stop. Session 3 sends it **up**
  via `abrir_instancia` and no endpoint ever returns it.
- **`instanciaId` is hardcoded to 42** in `HostViewModel.DEMO_INSTANCIA_ID` so the phone,
  `scan.swift`, and `RotatingCodeTest` all talk about the same instance. Replace with the
  row id `abrir_instancia` returns.
- **`clockOffsetSeconds` is plumbed through everywhere and is always 0.** `RotatingCode`,
  `BleAdvertiser`, and `HostAdvertisingService` all take it. Session 3 fills it from server
  time; it is a change of argument, not of signature.
- **Submit `CheckInViewModel.latestSighting`, not the first sighting.** It is already kept
  up to date for this reason.
- **The SQL must reproduce the known vector.** Key `000102030405060708090a0b0c0d0e0f`,
  instancia 42, counter 58000000 → `67e94bf8a08959ea`. Kotlin and Swift already agree;
  `beacon.swift` and `scan.swift` self-check against it on every start.
- **`CheckInErrorReason.HOST_NOT_FOUND` is currently unreachable.** Scanning never gives up,
  it just stays on SCANNING. The state and its copy still exist from session 1.
- **`POST_NOTIFICATIONS` is not requested.** If denied, the foreground-service notification
  is suppressed but the service still runs and advertising continues — criterion 5 was
  verified in that state.

## Session 3 results (2026-07-26)

Backend claims are against the **live Supabase project `nfysenajrfquusawyotc`** (Postgres
17.6, pgcrypto 1.3). Device claims are on the **Samsung Galaxy A54 5G (SM-A546E), Android
16 / API 36, over wireless adb**, with the **M4 Mac** as the co-host beacon. Nothing here
was observed on an emulator.

### Acceptance criteria

| # | Criterion | Result |
| --- | --- | --- |
| 1 | SQL agrees with the Kotlin known vector | **PASS.** `extensions.hmac(int4send(42) \|\| int8send(58000000), key,'sha256')[1..8]` = `67e94bf8a08959ea`, identical to `RotatingCodeTest` and `BukInProtocol.swift` |
| 2 | Mac beacons → phone SCANNING → READY → tap → SUCCESS, row with `metodo_confirmacion='BLE'` | **PASS.** Row written 19:20:06Z, `PRE_INSCRITO / BLE / asistencia=t` |
| 3 | Two taps → one row, no error screen | **PASS.** Two taps 250 ms apart; SUCCESS shown, `count = 1` |
| 4 | Unenrolled collaborator gets `origen='WALK_IN'` | **PASS.** Checked in without enrolling → `WALK_IN / BLE` |
| 5 | Stale code rejected | **PASS.** Code captured 14:22:53 accepted (`OK`); the *same* code replayed 14:24:37 returned `CODIGO_INVALIDO` and wrote no row |
| 6 | Direct table query with the anon key denied | **PASS.** All four tables `42501 permission denied`; ungranted helper denied too; granted RPC works |
| 7 | Roster shows the arrival within seconds | **PASS.** Went 3→4 within 4 s with the screen untouched |
| 8 | Manual registration writes `MANUAL` with `atestiguado_por_id` | **PASS.** `Diana Osorio / MANUAL`, witness recorded |
| 9 | No network produces the offline state, not a crash | **PASS.** "Sin conexión" card rendered, process alive, no crash log, **no row written**. Re-run after the fix below: card cleared itself when Wi-Fi returned, screen untouched, and the retry then wrote the row |

**Every criterion was run. None is unmet, and none was downgraded.**

### The strongest single piece of evidence

The fixed known vector proves the three implementations agree on one hardcoded input. Better
than that: with the phone hosting a session it had just created, `instance_key` generated by
`SecureRandom` on device and uploaded by `abrir_instancia`, the code **displayed on the
phone** and the code **derived by Postgres from the stored key** were compared live:

```
phone displays  : d8175d428981562f
postgres derives: d8175d428981562f
```

Same for the previous window (`fa94a92b69dee267`). Kotlin and pgcrypto agree on a random
key neither of them was written against. The byte-serialization bug spec 03 called "the most
likely bug in the session" cannot be hiding.

### Server-side suite

`supabase/tests/rpc_test.sql` — **19 cases, 0 failures**, run against the live project inside
a transaction that rolls back. Covers the vector, ±1 window tolerance, two-window rejection
both directions, a forged code, a valid code from *another* instance, out-of-hours, the
double-call idempotency pair, `WALK_IN` vs `PRE_INSCRITO`, manual registration, and
find-or-create identity. This is the regression gate; run it before touching the RPCs.

### Four bugs the device found

1. **Any server error crashed the app.** `BukInRepository` caught only `HttpRequestException`
   and `IOException`, so a `PostgrestRestException` — which is what a constraint violation, a
   bad argument, or a 500 arrives as — propagated out of `viewModelScope` and killed the
   process. Observed as `FATAL EXCEPTION: main` on the phone from one foreign key violation.
   The repository is a trust boundary and now lets nothing but `CancellationException`
   escape. Transport failures map to `SinRed`, everything else to the new
   `ResultadoConfirmacion.ErrorServidor`, because "sin conexión" on a phone that plainly has
   a connection sends someone chasing a problem they do not have.
2. **A phone holding a `colaborador_id` that no longer exists could never check in again.**
   Reseeding the database orphans every installed phone, and the only symptom is a foreign
   key violation at the moment of the write. The session list now re-issues the remembered
   name through `identificar_colaborador` — find-or-create, so a no-op until it is a repair —
   before it lists anything, and nav entries read the stored id fresh instead of reusing the
   one captured when the nav graph was first composed.
3. **`WALK_IN` was unreachable from the UI.** The session row offered "Inscribirme" before
   "Marcar asistencia", so a person had to enrol before they could mark — and every check-in
   came out `PRE_INSCRITO`. But someone who turns up to a session they never signed up for
   *is* the walk-in case. Reordered so an active session offers check-in regardless of
   enrolment; signing in ahead of time is what makes someone `PRE_INSCRITO`.
4. **A host screen claimed another session's broadcast.** `HostSession` is process-global, so
   opening instance 8 and then viewing instance 1 showed "La sala está abierta / Instancia:
   8" under instance 1's name. `HostViewModel` now treats a `Broadcasting` state for a
   different `instanciaId` as `Stopped`. The `ponytail:` note on `HostSession` already named
   this ceiling; this is the cheap half of the fix, not the binder.

### Deliberate deviations from spec 03

| Deviation | Why |
| --- | --- |
| **The clock gates check-in, not `estado`** | Direct instruction: a collaborator signs into a session and waits for its hour, and availability must not depend on whether a host pressed a button. `abrir_instancia` still sets `ABIERTO` because it is the only path by which `instance_key` is stored, but `confirmar_asistencia` tests `ventana_activa`. Nothing is weakened — an instance with no key cannot produce a verifiable code anyway. |
| **A typed name, not a list of names** | Spec 03 said pick yourself from a list. Showing one person the roster of their colleagues so they can tap whichever they like hands out the names and still proves nothing. Typing proves nothing either — that is the documented v1 cut — but it does not also leak. |
| **Byte arguments are hex `text`, not `bytea`** | JSON has no byte type. PostgREST's bytea escaping is one more thing that can differ silently between the client and a psql test; hex is unambiguous on both sides and is the notation all three test vectors are already written in. |
| **One `BukInRepository`** | Spec named `AsistenciaRepository` + `InstanciaRepository`. Nine RPCs are one PostgREST surface; splitting by table is delegation with no second consumer. |
| **No `ConfirmarAsistencia` use case** | It would be a pass-through. The one real rule — never submit a sighting older than one window — is `RotatingCode.isFresh`, in `:domain`, with four JVM tests. |
| **`NameEntryScreen` and `SessionPickerScreen` live in `:app`** | Both roles need the session list and features may never depend on features. Same rationale that already puts `RolePickerScreen` there. |

### Verified NOT verifiable on this hardware — do not claim otherwise

- **Android host → Android collaborator, end to end.** Still never exercised; both radios are
  never Android at once. The host path is verified by the Mac decoding the phone (session 2)
  and by Postgres deriving the phone's live code (above).
- **Range and crowd scale.** Unchanged from session 2, and unmeasurable with one phone.
- **The offline test costs the debugger.** `adb` runs over the same Wi-Fi the test disables,
  so the phone cannot be observed while it is offline. The sequence was scripted on-device
  (`svc wifi disable; input tap; svc wifi enable; input tap`) and the result read once the
  phone came back. The end state is a fact; the moment of transition was not watched.
- **`NET_CAPABILITY_VALIDATED` was not tested against a captive portal.** It is the right
  signal for one — associated is not connected — but no portal was available to prove it.
- **Concurrency at scale.** `UNIQUE (colaborador_id, instancia_id)` makes it safe by
  construction and 300 upserts contend for nothing, but no load test was run.

### "Sin conexión" used to be a dead end

Reported from the phone after the first pass: Wi-Fi came back, the internet came back, and
the card stayed. Three separate defects met in one place.

`CheckInState.Offline` was stored as a finished submission, and any finished submission
outranked the radio forever. Its notice card was the one blocked state written without an
action, so it also replaced the button with nothing to press. And `onCheckIn` guarded on
"any submission in flight or finished", so even a restored button would have refused to fire.

Fixed at the root rather than by adding a button:

- `NetworkMonitor` in `:core:data` wraps `registerDefaultNetworkCallback`. "Sin conexión" is
  a claim about the network, so it stops being true when the network returns —
  `NET_CAPABILITY_VALIDATED`, not "Wi-Fi associated", because a captive portal is associated
  and useless.
- The in-flight guard narrowed to `Enviando`. A previous attempt that failed for want of
  network must not block the retry the returning network makes possible.
- The card kept a `Reintentar` action anyway. The platform only calls a network validated
  once it has proved it, and nothing may be a dead end while it waits.

Invariant 7 says every failure has a distinct, actionable message and no permanently
disabled control. This state had been violating it since session 1's mockup.

## Session 4 results (2026-07-26) — design research and spec

**No production code was changed.** This session read every UI file, measured every colour
pair, researched the platform standards, and wrote `context/specs/04-design.md` plus a
rewritten `context/ui-context.md`. `./gradlew assembleDebug` and `./gradlew test` are
untouched.

### Three accessibility failures are shipping today

Measured with a WCAG 2.1 relative-luminance calculation over the composited token values, at
the worst end of every gradient. These are not preferences.

| Token | Used for | Measured | Needed |
| --- | --- | --- | --- |
| `BukOnBlueMuted` = white @ 0.55 | every ticket label, 12sp | **2.41:1** at the gradient's light end | 4.5:1 |
| `BukInkMuted` = ink @ 0.55 | footer, hints, all muted body | **4.40:1** | 4.5:1 |
| `BukSuccess` `#2BAB51` | success text, roster times, "Registrado" | **2.98:1** on white, **2.43:1** on the field | 4.5:1 |

Worse: `BukBlueLight` is light enough that **pure white on it measures 4.27:1**, so the
ticket's own course name fails. No alpha of white fixes it — white at 92% still reaches only
4.44:1. **The gradient was running the wrong direction.** Inverting it (`BukBlue →
BukBlueDeep`) makes `BukBlue` itself the lightest point of the card and every level clears:
white 7.60:1, muted 4.80:1, tear dots 3.49:1 against a 3:1 graphic threshold.

`BukSuccess` fails as text *and* as a graphic, so it is demoted to a decorative accent and
`BukSuccessInk = lerp(BukSuccess, BukInk, 0.30)` (`#1C7544`, 4.65:1 on the field) carries all
success meaning. All four brand values stay in `Color.kt`; no fifth hex is introduced.

### Bugs the audit found that a compile never would

1. **"Necesito ayuda" has been a dead control since session 1.** `TicketCard` declares
   `onHelpClick: () -> Unit = {}` and `CheckInScreen.kt:175` calls `TicketCard(instancia = it)`,
   taking the default. Tapping it does nothing, on every screen. Spec unit 3 turns it into the
   contextual help sheet.
2. **4 of the 13 typography styles the app references are declared nowhere**, so they fall
   back to Material defaults in Roboto: `bodySmall` (**12 call sites — the most-used style in
   the app**), `titleSmall` (4), `labelLarge` (4), `headlineSmall` (3). Those 23 call sites
   sit almost entirely in `HostScreen`, `SessionPickerScreen`, `HostRosterScreen` and
   `ManualRegistrationScreen` — the exact set this tracker already called "plain Material".
   They are plain Material because half their type literally is.
3. **`SessionPickerScreen` tracks `cargando` and never renders it.** The `when` at
   `SessionPickerScreen.kt:207-238` falls through to a `LazyColumn` over an empty list —
   a blank screen, then a pop.
4. **`ReducedMotion.animationsEnabled()` reads the setting once** inside `remember(resolver)`
   and never observes a change. Flip the setting and the app never notices.
5. **The halo does not fit a small phone.** `CheckInButton`'s outer ring is 280dp wide; on a
   320dp device with 20dp gutters that leaves exactly zero margin.
6. **The SCANNING screen is completely frozen.** `ProximityIllustration` contains no
   animation API of any kind — only `CheckInButton` and `SuccessCheck` animate anywhere in
   `:core:designsystem`. So the state a person spends the longest waiting in shows a static
   picture above a static sentence, which is indistinguishable from a hung app and is the
   same failure shape as the top complaint in `docs/feedback.md`. Missed on the first pass of
   this audit and caught on review; `ui-context.md` listed "three moments that earn motion"
   and scanning was not one of them, which is why it shipped static from session 1.

### The flow is demo-shaped, and that is separate from how it looks

Caught on review after the first draft of spec 04, which had specced only the visual layer.
A product can be beautiful and still behave like a prototype, and this one does:

1. **`RolePickerScreen` gates every launch.** `MainActivity.startKey` sends every returning
   user there forever, so a collaborator answers "¿Cómo entras hoy?" every day about a
   distinction that exists for the app's benefit. Almost none of the 300 collaborators will
   ever host. **Kept for the demo by decision** — role switching is constant while
   demonstrating — and recorded in spec 04 as a known cut with its upgrade path, so it is not
   rediscovered later as a defect.
2. **First run is three separate surfaces** with a hard cut between each: onboarding → name
   entry → role picker. Fixed by unit 11: onboarding becomes four steps and absorbs the name
   question, so first run is one continuous flow.
3. **The loudest complaint in `docs/feedback.md` was untouched** — *"There is no internal
   panel or profile menu where a worker can verify their history of past punch-ins."* The
   first draft of the spec answered it with an avatar and a greeting, which is decoration
   where the user asked for proof.

   **`listar_instancias` has no `WHERE` clause.** It already returns every instance, past and
   future, `order by fecha_inicio desc`, each with this collaborator's `asistencia` flag. The
   history is already on the device and the app flattens it into one list. Unit 12 builds the
   receipt with **no migration, no RPC, and no new network call.**
4. **Nothing in the app has a press state.** Every clickable surface relies on the default
   ripple; the ticket's help strip has a bare `Modifier.clickable`. Unit 13.
5. **No state change is announced to a screen reader.** SCANNING → READY → SUCCESS is silent
   under TalkBack, which is the accessibility form of this product's founding complaint —
   "you never know if your attendance was actually registered" — with no visual workaround
   available. Unit 14.

### Research findings that overturned the obvious choice

| Question | Answer | Consequence |
| --- | --- | --- |
| Adopt Material 3 Expressive? | **No.** `MaterialShapes` and `LoadingIndicator` were *reverted to experimental* in material3 1.5.0-alpha19 (b/497876695, b/497877850); `MotionScheme` graduated only on the 1.5 alpha branch. BOM 2026.06.01 pins material3 **1.4.0** stable. | Motion is built on stable `spring()` with our own token object. **No BOM bump.** |
| Downloadable Google Fonts for Inter/Merriweather? | **No.** Variable fonts are unsupported through the provider ([223262013](https://issuetracker.google.com/issues/223262013)), and the fallback chain flashes on first launch. | Bundle both, subset to latin+latin-ext, ~370 KB. |
| Glass effects? | `Modifier.blur` is a **silent no-op below API 31**; minSdk is 26. | Two surfaces only, and both must look finished as translucent solids. |
| `lerp` colour space (Oklab vs sRGB)? | Irrelevant here — the two differ by 0.01:1 on the token in question. | Stopped worrying about it. |

### Decisions taken

| Decision | Resolution |
| --- | --- |
| Page field | Periwinkle `BukField`, closing the session-1 open question. |
| Onboarding motion | `lottie-compose` 6.7.1, three bundled JSONs, **every** colour overridden at runtime via `LottieDynamicProperties` on `KeyPath("**")` so the palette comes from tokens and not the file. The one new UI dependency. |
| Profile picture | **On-device only.** `PickVisualMedia` (no permission), copied to `filesDir`, path beside the name in `IdentityPreferences`. No bucket, no RLS, no Coil — `BitmapFactory` with `inSampleSize` for one image. |
| Success feedback | `HapticFeedbackType.Confirm` always; a ~15 KB tone **only** when `AudioManager.ringerMode == RINGER_MODE_NORMAL`. A silent phone in a training room stays silent. |
| Onboarding is four steps | Steps 1–3 keep their copy verbatim; step 4 is the name question inline, so first run is one flow instead of three surfaces. The name *form* becomes a shared composable — `NameEntryScreen` stays reachable, because "No soy X" navigates to it. Step 4 has no Skip: the app cannot function without a name, and a skippable step that then blocks you is worse than no skip. |
| The role picker stays, for now | It gates every launch and is the clearest demo tell in the product, but role switching is constant on demo day and a two-tap profile path costs more there than the daily tax costs a user who does not exist yet. Explicitly a scope acceptance, not an oversight. Upgrade is `MainActivity.startKey` plus one menu entry; `SessionPicker` already takes `isHost` as a parameter. |
| Attendance history needs no backend | `listar_instancias` returns every instance with an `asistencia` flag and no `WHERE` clause. The receipt users asked for is presentation over data already being fetched. If unit 12 finds itself editing `supabase/`, something was misread. |
| Splash | `core-splashscreen` 1.2.0, AVD ≤1000 ms on the 432dp/288dp canvas. **The only indeterminate indicator left in the app.** Not held artificially — `MainActivity` has nothing to wait for. |
| Launcher icon | The `in` glyph from `docs/assets/buk-in.svg`, 108dp layers inside the 66dp safe viewport, with a `<monochrome>` layer. Replaces the Android Studio stock robot still shipping today. |
| No fades | `fadeIn`/`fadeOut` banned. Transitions move, scale, and morph. |

### Not verified, because nothing was built

Everything in spec 04 is a design intention until session 5 runs it on the **Galaxy A54 over
wireless adb**. No claim in this section is a claim about on-device appearance or frame rate;
the contrast numbers are computed from token values, and the audit findings are readings of
the source. Both are checkable without a device, which is why they are stated here and the
rest is not.

## Session 5 results (2026-07-26) — the design overhaul

Every device claim below is on the **Samsung Galaxy A54 5G (SM-A546E), Android 16 / API 36,
over wireless adb**, with the **M4 Mac** as the host beacon. Nothing here was observed on an
emulator. Three criteria could not be run at all and are listed as unrun rather than
downgraded.

### Post-review fixes (same session)

- **Screen transitions travelled a fifth of the width and were cut**, which reads as content
  vanishing rather than moving. Now a full-width shared axis, still with no fade.
- **The onboarding Lottie loops flickered.** Two causes: `animationsEnabled()` is an observed
  value and flipping from its initial emission restarted the clip mid-composition, and the
  clips run 1–2.6 s at source speed and loop with a hard cut. Read once, `speed = 0.5f`,
  `restartOnPlay = false`.
- **Host path walked on the phone** for the first time: host screen → roster → manual
  registration, all on `BukScreen`, no crashes.

### What shipped

All fifteen units. `./gradlew assembleDebug` and `./gradlew test` pass (26 tests, 0 failures —
the count has grown from session 2's 22). `git diff --stat supabase/` is empty: no migration,
no RPC, no backend change of any kind.

| # | Criterion | Result |
| --- | --- | --- |
| 1 | No functional regression | **PASS.** `./gradlew test` green, 26/0. `rpc_test.sql` = **19 / 0 / TODO VERDE**. A live BLE check-in wrote a real row: `Ana Restrepo / asistencia=true / BLE / WALK_IN / 2026-07-27T00:07:01Z`, confirmed through `listar_asistencia(2)` and by reading `inscripcion` directly |
| 2 | `Color.kt` is still the only hex | **PASS.** The only other `.kt` match is `0xFFFFFFFFL`, a bitmask in `AdvertisementPayload`, and it predates this session |
| 3 | Every used type style is declared | **PASS.** 15 styles referenced, 15 declared, none falling through to a Material default. Was 13 referenced / 9 declared |
| 4 | Contrast holds on device | **PASS by construction, spot-checked visually.** The token values are the computed ones; screenshots show white, muted and faint levels all legible on the deepened gradient. Not sampled with a pixel-level contrast tool |
| 5 | No fades | **PASS.** The only `fadeIn`/`fadeOut` in the tree is the comment saying they are banned |
| 6 | "Necesito ayuda" opens the sheet | **PASS.** Opened on device; content verified contextual for the permission-denied state. Not walked through all five states on device |
| 7 | Success morphs | **PASS.** Caught mid-flight (container closed from pill to circle, recolouring through teal, label gone) *and* completed: filled `BukSuccessInk` circle, white check drawn inside, Merriweather payoff line. One continuous object throughout. The tone is ringer-gated and the phone was on vibrate, so it correctly stayed silent — which is the specced behaviour, not an unverified one |
| 8 | One indeterminate indicator | **PASS.** The splash AVD, and nothing else. Every load is a skeleton |
| 8b | Scanning visibly works | **PARTIAL.** The arcs animate (one `rememberInfiniteTransition`, phase read inside the `Canvas` draw lambda). Verified by construction and by consecutive screenshots; **Layout Inspector recomposition counts were not captured** |
| 9 | No layout shift on load | **PASS by construction.** `TicketCardSkeleton` occupies the real card's bounds; list skeletons use the last known row count |
| 10 | Responsive | **PASS.** Walked at 320dp, 411dp and 200% font scale on the phone. Nothing clips or overlaps; two labels ellipsise gracefully at 200%, which is the designed degradation |
| 11 | 60 fps | **PASS.** `dumpsys gfxinfo` over scanning + the morph: 1423 frames, **18 janky (1.26%)**, 50th 7ms / 90th 9ms / 95th 10ms. Debug build |
| 12 | Icon and splash | **PARTIAL.** Launcher icon verified on the phone's home screen in Samsung's squircle mask. **Not** checked in circle or teardrop masks, in themed-icon mode, or as a captured splash frame |
| 13 | Weight | **PASS.** `assembleRelease` = **12.41 MB**, against a 14.5 MB budget and a ~12 MB baseline |
| 14 | Blur degrades | **N/A — the blur was removed.** See below |
| 15 | First run is one flow | **PASS.** Fresh install → four pager steps → session list, no screen cut that is not a page turn. "No soy X" still reaches the same form |
| 16 | History is reachable | **PASS by construction.** Avatar → Mi asistencia, two taps. Not walked on device |
| 17 | No backend was touched | **PASS.** `git diff --stat supabase/` empty |
| 18 | Everything presses | **PASS by construction.** One `Modifier.bukPressable`; disabled surfaces pass `enabled = false` and do not scale; every interactive surface carries `BukMinTouchTarget` |
| 19 | TalkBack | **NOT RUN.** The live region, the morph's outcome description, and `clearAndSetSemantics` on the footer and decorative canvases are all in the code and none of it was exercised with a screen reader |

### The live check-in, and the gate that is still shut

**`abrir_instancia` does not need `psql`.** It is one of the nine functions granted to `anon`,
because the host app calls it from the device — so seeding an instance with the known-vector
key is a `curl` against the same PostgREST surface the app uses:

```bash
curl -s -X POST "$SUPABASE_URL/rest/v1/rpc/abrir_instancia" \
  -H "apikey: $SUPABASE_PUBLISHABLE_KEY" -H "Content-Type: application/json" \
  -d '{"p_instancia_id": 2, "p_key_hex": "000102030405060708090a0b0c0d0e0f"}'
```

With instance 2 opened that way and `tools/mac-ble/beacon --instancia 2` running, the phone
walked SCANNING → READY → tap → the morph → SUCCESS, and the row landed. Both directions of
the code path are now covered on this hardware: a **valid** code writes a row, and a code
derived from the wrong key is refused as `CODIGO_INVALIDO` and rendered as "Ese código ya no
sirve" with a working recovery action.

**`rpc_test.sql` passes: 19 / 0 / TODO VERDE.** It needed the database password to be reset in
the Supabase dashboard first — the value in `.env` was correct but the stored one had drifted.
Worth knowing for next time: **a pooler password reset takes a few seconds to propagate**, and
during that window `psql` still answers `FATAL: password authentication failed for user
"postgres"` even with the right password. Two attempts seconds apart gave different results.

`.env` (gitignored, mode 600) now holds the operator credentials so this does not have to be
re-typed. **It must never be committed** — `.gitignore` has an explicit `.env` entry and
`git check-ignore` was verified against it.

### Five defects the device found that a compile never would

1. **`Modifier.blur` blurred the label, not the backdrop.** The ticket's state pill was
   specced as a glass surface. Compose has no backdrop-blur modifier — `Modifier.blur` blurs
   the element's *own* content — so on the A54 (API 36, well past the API 31 gate) the pill
   rendered as an unreadable smear. Removed. The pill ships as the translucent solid the spec
   required it to be designed as first, and unit 9's "two glass surfaces" is really "two
   translucent surfaces". Criterion 14 is moot rather than passed.
2. **`aapt2` silently truncates resource strings over 32 KB.** The launcher-icon and splash
   path data, serialised at full float precision, exceeded it — the build emitted
   `string too large to encode using UTF-8 written instead as 'STRING_TOO_LARGE'` as a
   *non-fatal* line and would have shipped an empty icon. Rounding coordinates to two decimals
   (past visible at 108dp) cut the longest path from 38 KB to 15 KB.
3. **The ticket's state pill did not update after a successful check-in.** `instancia` is
   loaded once in `bind()`, so the card sat there still reading "Sin inscripción" directly
   above a screen saying the attendance had registered. The pill exists to carry state and it
   has to carry the state that just changed; `cargarInstancia()` now re-runs on `Ok` /
   `YaRegistrado`. Verified on the phone: the session list afterwards shows that row with the
   `BukSuccessInk` edge, an "Asistencia marcada" chip, and no longer tappable.
4. **`responsive()` crashed the check-in screen on a narrow display.** It was written as
   `candidate.coerceIn(min, minOf(max, screenWidth))`, and on a screen narrower than `min`
   that is an inverted range — `coerceIn` throws. `FATAL EXCEPTION: Cannot coerce value to an
   empty range: maximum 114.0.dp is less than minimum 176.0.dp`, caught in the crash buffer
   during the width sweep. A floor wider than the screen is not a floor, it is a
   contradiction; the upper bound is now computed first and the floor clamped under it.
5. **The launcher icon was drawn at the full 66dp safe zone and looked wrong on the
   launcher.** 66dp is the largest content guaranteed not to be *cut off*; it is not the
   largest content that should be *drawn*. At 66dp the mark filled the safe square, broke the
   safe circle at its corners, and sat visibly tighter in its mask than every neighbouring
   icon. Refitted to 52dp, inside the 60dp keyline. The splash had the mirror-image bug — a
   3.36:1 lockup scaled to the full 288dp width would have had both ends cut by the circular
   mask — and is now fitted to the inscribed circle with 12% margin.

### Deviations from spec 04, and why

| Deviation | Why |
| --- | --- |
| **Merriweather ships as a static Bold, not a variable font** | The variable file subset to the same glyph set is 462 KB against 187 KB for the single instance, and the type scale never asks Merriweather for a weight other than Bold. 275 KB of unreachable weights is dead payload, not a trade-off. Inter *is* variable — it uses four weights |
| **`CheckInButton.kt` and `SuccessCheck.kt` are deleted, not rewritten** | The spec's manifest keeps them either side of a new `CheckInMorph.kt`. But the requirement is "one continuous object, never two composables swapped", and leaving two swappable composables in place is an invitation to swap them. One file now owns the whole Ready → Enviando → Success container |
| **The wordmark is a vector drawable, not an `ImageVector`** | The splash needs an `AnimatedVectorDrawable`, which is XML-only, so the path data has to exist as XML regardless. A second 37 KB copy as Kotlin string constants to satisfy "no baked fill" would be worse — and the fill is never rendered, because every call site draws it through `Icon`, which tints |
| **Two icons are hand-declared `ImageVector`s** | material3 1.4.0 no longer brings `material-icons-core`, and the app needs exactly two glyphs (a chevron and a back arrow). Adding the artifact for two glyphs is the dependency this project exists to refuse |
| **`app/res/values/colors.xml` holds two hex values** | A launcher icon and a splash window are inflated by the framework before any Compose theme exists and cannot read `Color.kt`. Criterion 2 greps `*.kt` and still passes; this is the minimum duplication the platform forces |
| **Onboarding takes the name step as a slot parameter** | `:features:onboarding` cannot see `NameEntryViewModel`, which lives in `:app`. `:app` passes the step in. The alternative — a second copy of the call that issues an identity — is the thing unit 11 explicitly forbids |
| **The confirmation tone is a generated WAV, not a sourced `.ogg`** | 13 KB, two notes, generated from a formula. Sourcing a 15 KB audio file of unverifiable provenance to save 2 KB is a bad trade |

### The Lottie animations, and where they came from

Three bundled JSONs, 27 KB total, all **vector-only with no gradients and no bitmaps** —
which is what makes the runtime recolouring total. All three are **Lottie Simple License**:
commercial use, modification, and distribution inside a product are permitted, and attribution
is not required.

| Page | Animation | Author | Size |
| --- | --- | --- | --- |
| "Tu asistencia, en un toque" | Double Tap Touch Gesture | J | 2.1 KB |
| "Tu teléfono encuentra a tu anfitrión" | bluetooth connecting | daizy | 2.0 KB |
| "Por ahora, registramos tu entrada" | Checked | Priya Muda Wineko | 1.5 KB |

The first choice for page 2 — a more literal "Bluetooth searching" — was **rejected after
inspection**: it is five image layers wrapping two PNGs, so `LottieDynamicProperties` cannot
touch its colour and it would have imported palette from a file. Every candidate was
downloaded and filtered for vector-only content before any of them was picked. Recolouring is
per-layer rather than a blanket `KeyPath("**")` because two of the three carry a knocked-out
glyph that has to stay light against the shape behind it.

### Not verified on this hardware — do not claim otherwise

- **`MiAsistenciaScreen`, the profile sheet and the avatar picker.** Built and compiled, never
  opened on the phone.
- **The splash animation.** Wired and declared; no frame captured.
- **TalkBack.** No screen-reader pass was made.
- **Layout Inspector recomposition counts.** The zero-recomposition claims rest on where the
  reads are written (inside `graphicsLayer` / `drawWithCache` / `Canvas` lambdas), not on a
  measurement.
- **Launcher icon in circle and teardrop masks, and themed-icon mode.** Only Samsung's
  squircle was seen.
- **The splash animation itself.** `installSplashScreen()` is wired and the AVD is declared,
  but no splash frame was captured — it is ~900 ms and the screencap never landed inside it.

## Limitations to state out loud in the presentation

Disclosed by you they read as rigour; discovered by a reviewer they read as gaps.

1. **Not relay-proof, and no BLE scheme is.** Forwarding the live broadcast over the internet
   to a confederate defeats proximity ([IEEE 8555557](https://ieeexplore.ieee.org/document/8555557/)).
   Rotation kills screenshots, sharing, and replay — demonstrated live, criterion 5 — but not
   a real-time relay. Mitigations available and not built: an RSSI floor (noisy, risky on demo
   day). The one that *is* built is the host's roster as a human cross-check.
2. **Identity is the bigger hole.** BLE proves *a phone* was in the room, never *whose*. With
   a typed name and no authentication, anyone can type a colleague's name. This is sharper
   than it was in the plan, where the name came from a list. Only authentication closes it,
   and the RPC boundary is shaped so swapping the client-supplied `colaborador_id` for a JWT
   claim is a one-argument change.
3. **The Mac is a co-host, not an Android host.** The architecture explicitly allows any
   device holding the key to advertise. It does not reproduce non-connectable advertising,
   and it says nothing about range.

## Completed

- Planning: `context/` filled, three specs written, three master prompts written,
  `CLAUDE.md` created.
- **Session 1 — foundation, design system, onboarding.**
  - Gradle monorepo: `:app`, `:domain`, `:core:designsystem`, `:features:onboarding`,
    `:features:checkin`, `:features:host` (empty placeholder). Version catalog pinned to
    AGP 9.2.0, Kotlin 2.3.21, Compose BOM 2026.06.01, Nav3 1.0.1, minSdk 26.
  - Buk theme. `Color.kt` holds exactly the four brand hex values and derives every other
    token; it is verifiably the only file in the project containing a hex literal.
  - `TicketCard` with the dotted stub separator and the tucked "Necesito ayuda" strip,
    `CheckInButton` with pulsing concentric halos, `ProximityIllustration`, `SuccessCheck`,
    `NoticeCard`, `BukInFooter`.
  - `CheckInState` as a sealed interface in `:domain`; `CheckInScreen` renders all five
    states with a persistent header and footer and an animated transition into Success.
  - 3-screen onboarding with the spec copy verbatim, shown once via `SharedPreferences`.
  - Role picker and Navigation 3 back stack.
  - Verified on device: `assembleDebug` and `installDebug` pass, every state screenshotted
    and compared against `docs/assets/`, onboarding confirmed to appear once.

- **Session 3 — Supabase, real data, the closed loop.**
  - `supabase/`: three append-only migrations, a seed, and `tests/rpc_test.sql` (19 cases).
    Nine `SECURITY DEFINER` functions; deny-all RLS with no policies, table grants revoked
    from `anon`, `EXECUTE` revoked from `PUBLIC` and granted back by name.
  - `:core:data` — supabase-kt **3.6.0** (the release built against Kotlin 2.3.21; 3.7.0
    pulls stdlib 2.4.0 and a 2.3.21 compiler reads that as newer metadata), Ktor 3.4.3,
    OkHttp engine. One `BukInRepository`, DTOs private to the module, `INTERNET` declared
    here and merged up the way `:core:ble` declares Bluetooth.
  - Every hardcoded string gone: `rememberDemoInstancia` and the six `demo_*` strings are
    deleted, `Instancia` carries real `Instant`s, and `TicketCard` formats them.
  - Name entry, session picker (both roles, one screen), live roster, manual registration.
    Nav keys carry their `instanciaId`; check-in is no longer a dead end.
  - `clockOffsetSeconds` finally has a producer. `DEMO_INSTANCIA_ID = 42` is gone.

- **Session 2 — the BLE proximity engine.**
  - `RotatingCode` and `AdvertisementPayload` in `:domain`, pure Kotlin, 22 JVM tests
    including the known vector that binds Kotlin, Swift, and session 3's SQL together.
  - `:core:ble`: `BleCapability` (ordered preflight + adapter-state flow), `BleScanner`
    (`callbackFlow`, hardware-offloaded prefix filter), `BleAdvertiser` (boundary-aligned
    30 s rotation), `HostAdvertisingService` (`connectedDevice` foreground service).
    Every Bluetooth permission is declared here and merges up.
  - Check-in screen driven by the radio; the session-1 debug state switcher is gone.
  - Host screen: open/close the room, live code, countdown, advertising status.
  - Diagnostics screen behind a quiet affordance on the role picker.
  - `tools/mac-ble/`: `BukInProtocol.swift` (shared), `beacon.swift`, `scan.swift`.

## In progress

- None.

## Next up

Nothing is required for the demo. If the work continues:

1. **Execute `specs/04-design.md`.** Specced in session 4, eleven units, not started. It
   fixes three measured accessibility failures alongside the visual work.
2. **Check-out.** Still unsolved and still out of scope — see the open question below.
3. **Auth.** The single change that closes the identity hole. One argument per RPC.

## Architecture decisions

| Decision | Rationale |
| --- | --- |
| **Material 3 Expressive is not adopted** | `MaterialShapes` and `LoadingIndicator` were reverted to *experimental* in material3 1.5.0-alpha19, and `MotionScheme` graduated only on the 1.5 alpha branch. The project is on 1.4.0 stable via BOM 2026.06.01. Motion is built on `androidx.compose.animation.core.spring`, which is stable foundation API, with a local token object. Chasing the expressive APIs would mean bumping the BOM on a working build to buy nothing this design needs. Re-check when 1.5.0 goes stable. |
| **The ticket gradient runs deep, not light** | It ran `BukBlue → lighter`, and that direction cannot be made legible: pure white on the old light end measures 4.27:1, below the 4.5:1 minimum, and white at 92% alpha still only reaches 4.44:1. Inverting to `BukBlue → BukBlueDeep` makes `BukBlue` the lightest point of the card — the worst case — and every white level clears. This is a legibility fix that happens to look better, not a taste change. |
| **Fonts are bundled and subset, not downloaded** | Variable fonts do not work through the Google Fonts downloadable provider (issue 223262013), and downloadable fonts need Play Services plus a fallback chain that flashes on first launch. A flash of the wrong typeface at launch is precisely the seam this product exists to remove. ~370 KB for Inter Variable + Merriweather at latin+latin-ext. |
| **`BukSuccess` never carries text or an icon** | At `#2BAB51` it measures 2.98:1 on white and 2.43:1 on the page field — it fails the 4.5:1 text threshold *and* the 3:1 graphic threshold. It stays in `Color.kt` as one of the four authoritative brand values but is demoted to a decorative accent; `BukSuccessInk` carries all success meaning. |
| **The avatar is on-device only** | A Supabase Storage bucket means new RLS policies, an upload path in `:core:data`, and an image-loading dependency — backend work inside a design session, on a product with no auth to scope a bucket against. `PickVisualMedia` into `filesDir` needs no permission and no server. It does not survive a reinstall, which is the accepted cost. |
| `AdvertisementPayload` lives in `:domain`, not `:core:ble` | Spec 02's file manifest put the class in `:core:ble` but its **test** in `domain/src/test/`, which cannot both be true. The payload is pure format over `java.util.UUID` — JVM stdlib, not Android — so putting it in `:domain` satisfies the test location, keeps `:domain` free of `android.*`, and makes the wire format unit-testable without a device. `:core:ble` wraps it in `ParcelUuid` at the one call site that needs to. |
| `:core:ble` returns typed results and never a string | It would otherwise need `:core:designsystem` for `R.string`, coupling the radio to the theme. Feature modules map `BleStatus` to copy, which keeps the one-`strings.xml` rule intact. The foreground-service notification text is passed in as an Intent extra for the same reason. |
| `BleAdvertiser` uses `flow` + `suspendCancellableCoroutine`, not `callbackFlow` | Each rotation window is one request/response against `AdvertiseCallback`, not a stream. `code-standards.md` asks for `callbackFlow` + `awaitClose`; the rule it protects — never leak a registration — is kept by the `finally`, in a third of the code. `BleScanner` is a genuine stream and does use `callbackFlow`. |
| `SharingStarted.WhileSubscribed` instead of `DisposableEffect` for scan lifecycle | `collectAsStateWithLifecycle` unsubscribes at STOPPED, which cancels the `callbackFlow` and runs `awaitClose`. One operator covers "stop scanning off-screen" and "never leak a callback" — measured at `9 / 9` started/stopped on the phone. |
| `HostSession` is a process-global `StateFlow` | The service is a singleton and there is one host session at a time. A binder plus a connection callback would be forty lines of ceremony around a value with one writer. Marked `ponytail:` in the source with the upgrade path. |
| `ACTION_BLUETOOTH_SETTINGS`, not `ACTION_REQUEST_ENABLE` | The enable dialog requires `BLUETOOTH_CONNECT`, which a collaborator is never asked for. Sending them to the settings screen needs no permission and cannot dead-end. |
| Grace period implemented with `transformLatest` | Each new sighting cancels the pending "host is gone" emission. The whole 10-second grace is one operator rather than a timer, a job handle, and a cancellation path. |
| `RotatingCode.verify` exists although no client calls it | It is the reference the session-3 SQL must match, and it is what the known-vector test exercises. The collaborator holds no key and never validates — invariant 4 stands. |
| Raw `BluetoothLeAdvertiser` / `BluetoothLeScanner`, not CWA | CWA implements no BLE of its own — it wraps the Exposure Notifications API, which was allowlist-gated to health authorities and removed from Play Services in Nov 2023. Nothing in it is reusable for proximity. |
| Host advertises, collaborator scans. One-way, no GATT | Matches the mockups exactly and is achievable in the time available. GATT was needed only for the offline relay, which is cut. It also caps at ~7 concurrent connections on Android — unusable at classroom scale, let alone 300. |
| Payload encoded in the 128-bit service UUID, not service data | iOS `CBPeripheralManager` accepts only local name and service UUIDs and errors on anything else — an iPhone cannot advertise service data at all. Encoding in the UUID costs nothing on Android and is what keeps an iOS host possible. |
| Non-connectable, **no scan response** | Keeps the advertisement `ADV_NONCONN_IND`, which scanners cannot reply to. A scan response would invite `SCAN_REQ` from every active scanner — 300 listeners would become 300 transmitters on three channels. |
| Submit the latest observed code, not the first | A person can detect the host and tap a minute later. The first code would be two windows stale and rejected. Most likely source of spurious rejections with real users. |
| The instance key travels **up** only — host generates it, server stores it, no endpoint returns it | With no auth, any RPC that hands out the key would let anyone generate valid codes from anywhere. That is worse than the relay attack because it needs no physical presence. Inverting the direction costs nothing and closes it. |
| Host's "start session" opens the instance via `abrir_instancia` | §2.2 assumes a background job flips `PROGRAMADO → ABIERTO`. There is no worker in this demo, so without this nothing ever opens an instance and every check-in fails the window check. |
| Accept `counter ± 1`, and correct the host clock from server time | RFC 6238 §5.2 recommends ±1 step. A host phone with automatic time off generates codes rejected 100% of the time with no diagnosable symptom; the offset returned by `abrir_instancia` removes the failure class. |
| `codigo_ble` column dropped | It described one static code per instance — exactly the design that allows screenshot-sharing. Replaced by `instance_key` + rotation. |
| Offline card copy corrected | The mockup promised presence alone was enough. With the relay cut that is false, and a reassurance that turns out false causes the exact "did it register?" anxiety the app exists to remove. |
| Scope stated in onboarding screen 3, framed as preparation | Naming what a person needs (Bluetooth, internet) before they need it prevents surprise mid-flow. Surprise is what breaks seamlessness — not honesty. |
| The identity limitation is **not** surfaced in the app | Telling a user seconds before check-in that anyone could impersonate them is alarming and unactionable. It is an architecture disclosure for the presentation, recorded in `architecture.md`. |
| Seamlessness defined as 9 testable rules, not an adjective | "Make it feel seamless" is unimplementable and unverifiable. The rules in `ui-context.md` are checkable on a device and are acceptance criteria in spec 01. |
| Rotating HMAC-SHA256 code on a 30s window | Kills screenshot-sharing, replay, and spoofing — the exact fraud the assessment calls out. Same concept as Google's Eddystone-EID but simpler on both ends: ~10 lines of Kotlin, one SQL expression. |
| Verify the code in Postgres, not on device | A client asserting "the code was valid" is not authorization. `pgcrypto` ships enabled on Supabase, so verification costs zero backend code. |
| Accept `counter ± 1` | Clock skew between the host and the server would otherwise cause spurious rejections at window boundaries. Superseded the original backward-only tolerance — see the RFC 6238 row below. |
| Supabase free tier, no server code | 500MB DB, 5GB egress, 2 projects, pauses after 1 week idle. Plain Postgres, so the AWS migration story stays honest. |
| Deny-all RLS + one `SECURITY DEFINER` RPC | The anon key ships in the APK and must be assumed public. Everything funnels through one validated function. |
| No `public-api` / `implementation` module split | The pattern in `docs/architecture.md` is right for a large team and doubles module count for a three-feature demo. Revisit if the app grows. |
| Single APK with a role switch | Two apps would double the build and install surface for no demo benefit. |
| minSdk 26 | Required by supabase-kt, and covers the mid-range devices this app targets. |
| No location permission | `neverForLocation` on `BLUETOOTH_SCAN` plus `ACCESS_FINE_LOCATION` capped at API 30 means Android 12+ needs no location grant. Directly answers the top-3 user complaint about location errors indoors. |
| **The single `strings.xml` lives in `:core:designsystem`, not `:app`** | Spec 01 placed it in `app/src/main/res/`. That cannot work: Android resource merging is one-directional, so a library module cannot reference `R.string` from the app that depends on it. Every UI module already depends on `:core:designsystem`, which makes it the only module where one shared strings file is reachable from all of them. The invariant the spec cared about — one file, no hardcoded copy — is preserved. |
| Illustration and wordmark drawn in Compose, not shipped as vector drawables | A `<vector>` needs its fill colour baked into XML, which would put a hex value outside `Color.kt` and break the project's own rule. A `Canvas` takes the theme token directly. Also removes the icon-pack dependency for a clock and a sun. |
| `:core:ble` and `:core:data` not created yet | They would be empty Android library modules that lengthen every build for nothing. Spec 01's file manifest lists neither, and calls out `:features:host` as the one module to stub. Session 2 creates `:core:ble`, session 3 `:core:data`, each with something in it. |
| No `BukInApplication` class | There is no DI container, no startup work, and no global state to initialise. An empty `Application` subclass is scaffolding for a later session that can add it in one line if it ever needs one. |
| Role picker lives in `:app`, not a feature module | It is a fork in the routing graph rather than a feature — it owns no state and no domain logic. Giving it a module would double the build surface for two cards. |
| `enableEdgeToEdge` forced to `SystemBarStyle.light` on both bars | The default `auto` style follows the *system* dark theme. This app is light-only, so on a phone in dark mode the default would put white status-bar icons on a near-white background. Forcing light bars is what makes the light-only decision actually hold on a real device. |
| Onboarding has no illustrations | Three pages of type on the brand field, and the pages carry real information. Mixed or half-finished illustrations would read worse than none, and nothing in the mockups specifies them. Cheap to add later if the deck wants them. |

## Open questions

- ~~**The mockup's page background is more saturated than `BukBackground`.**~~
  **Resolved 2026-07-26 (session 4):** the mockup is the authority. The page becomes
  `BukField = BukBlue.copy(alpha = 0.10f).compositeOver(BukBackground)` ≈ `#E3E8F6`.
  `BukBackground` stays `#F7F9FF` as a brand value; no fifth hex is introduced. Specced in
  `specs/04-design.md` unit 0.

- **Automatic check-out is unsolved.** Users forget to mark "Me retiro". Out of scope
  for the demo; check-in is the priority. Candidate approaches if revisited: geofence
  exit, BLE-absence timeout, or auto-closing at `fecha_fin` with `metodo = AUTO`.
- **Relay attack is accepted, not solved.** Forwarding the live broadcast over the
  internet defeats any BLE proximity scheme
  ([IEEE 8555557](https://ieeexplore.ieee.org/document/8555557/)). Mitigations not
  built: RSSI floor (noisy, risky on demo day), host roster cross-check (built).
- **No authentication means `colaborador_id` is client-chosen** and therefore forgeable.
  Acceptable for a demo; the RPC boundary is shaped so the JWT swap is one argument.
- ~~**Whether the phone can advertise at all.**~~ **Resolved 2026-07-26:**
  `isMultipleAdvertisementSupported = true` on the A54, and the host path was verified end
  to end by the Mac decoding the phone's broadcast.
- ~~**Whether Android can decode a macOS advertisement.**~~ **Resolved 2026-07-26:** yes.
  Criterion 0 passed on the phone; a foreground macOS process uses the standard AD
  structure, not the overflow area. netsim was never needed and remains untried.
- **Range, not capacity, is the ceiling at 300 people.** The radio and the database both
  handle that size without changes; a packed hall can exceed BLE's ~30–50 m indoor range
  once human bodies attenuate the signal. The fix is a co-host beacon advertising the
  same code — no protocol change, no collaborator change. Not built; build only if a
  real 300-person room is in play.
- **An iOS host must keep the app in the foreground.** Backgrounded iOS moves service
  UUIDs to an undocumented Apple overflow area that Android cannot decode. Acceptable
  for someone actively running a class. Android hosts are unaffected.

## Session notes

Planning session (2026-07-25) resolved the following against research rather than
assumption: the CWA reference is dead, peripheral mode is hardware-gated, no location
permission is required, BLE cannot stop a live relay, `pgcrypto` can verify the code,
and the advertising payload fits comfortably in 31 bytes (~19 used).

Every version, byte layout, permission block, and SQL snippet the build sessions need is
embedded in the specs. Do not re-derive them.

Session 1 (2026-07-25/26) discovered the following, none of which was predictable from the
plan:

- **AGP 9.2.0 requires Gradle 9.4.1.** The `android` CLI scaffolds a Gradle 9.1.0 wrapper,
  which fails immediately against the pinned AGP. The wrapper is now on 9.4.1.
- **AGP 9 has built-in Kotlin support and rejects the `kotlin-android` plugin.** Applying
  it is a hard build error, not a warning. Android modules apply only
  `com.android.library` / `com.android.application` plus the Compose compiler plugin;
  `:domain` still applies `kotlin-jvm` because it is not an Android module.
- **The `android` CLI resolves older versions than the specs pin** (AGP 9.0.1, Kotlin
  2.3.20, BOM 2026.03.01). All the spec-pinned versions were confirmed to exist in Maven
  and are what the catalog uses. Scaffold with the CLI, then pin.
- **The pre-existing `pixel_test` AVD is a 32-bit `arm` image and cannot start** — QEMU2
  refuses it and the classic engine is gone. A `medium_phone` arm64 AVD was created and is
  what session 1 was verified on.
- The local environment had no Android SDK and no `local.properties`; both now exist. The
  SDK lives at `~/Library/Android/sdk`.

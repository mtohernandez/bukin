# Progress Tracker

Update this file after every meaningful implementation change.

## Current phase

**Session 1 complete.** The project builds, installs, and runs. Onboarding, the role
picker, and all five collaborator states render and match the mockups. Nothing below the
UI exists yet — no Bluetooth, no network.

## Roadmap

| Session | Spec                              | Outcome                                              |
| ------- | --------------------------------- | ---------------------------------------------------- |
| 1       | `specs/01-foundation.md`          | Project builds, theme matches mockups, onboarding runs |
| 2       | `specs/02-ble-proximity.md`       | Mac beacons, phone detects the live code; flipped to verify the host path |
| 3       | `specs/03-supabase-checkin.md`    | End-to-end check-in landing in Postgres              |

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

## Current goal

Execute `context/prompts/session-2.md` — `specs/02-ble-proximity.md`. The Mac beacons a
rotating code and the phone detects it; then flip the radios to verify the Android host
path. Criterion 0 — that Android can decode a macOS advertisement — gates everything.

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

## In progress

- None.

## Next up

1. Session 2: `:core:ble`, advertiser and scanner, permission preflight, the rotating-code
   codec in `:domain` with its JVM unit test.
2. Check `isMultipleAdvertisementSupported()` on the phone. The Mac hosts by default, so
   this decides only whether the Android host path can be verified at all.
3. ~~Install on the phone over wireless adb.~~ **Done 2026-07-26.** Session 1 now verified
   on the real device: Samsung Galaxy A54 5G (SM-A546E), Android 16 / API 36, paired and
   connected over Wi-Fi. Onboarding, role picker, ticket card, and the scanning state all
   render correctly; insets are clean and the status-bar icons are dark over the light
   background, confirming the forced-light `enableEdgeToEdge` decision holds on a real
   Samsung device. Session 1's one outstanding acceptance criterion is met.
4. Build `tools/mac-ble/beacon.swift` and `scan.swift` — they are the test harness that
   replaces the missing second phone, not optional extras.

## Architecture decisions

| Decision | Rationale |
| --- | --- |
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

- **The mockup's page background is more saturated than `BukBackground`.** The mockups
  render the field as a clearly periwinkle tone; `#F7F9FF` is near-white and reads that
  way on device. The four brand values are stated as authoritative in three documents, so
  the token was implemented as specified rather than retuned to match a mockup render.
  If the mockup is the authority here, it is a one-line change in `Color.kt` — a derived
  token such as `BukBlue.copy(alpha = 0.08f).compositeOver(BukBackground)` mapped to
  `background` gets there without introducing a fifth hex. **Needs a decision.**

- **Automatic check-out is unsolved.** Users forget to mark "Me retiro". Out of scope
  for the demo; check-in is the priority. Candidate approaches if revisited: geofence
  exit, BLE-absence timeout, or auto-closing at `fecha_fin` with `metodo = AUTO`.
- **Relay attack is accepted, not solved.** Forwarding the live broadcast over the
  internet defeats any BLE proximity scheme
  ([IEEE 8555557](https://ieeexplore.ieee.org/document/8555557/)). Mitigations not
  built: RSSI floor (noisy, risky on demo day), host roster cross-check (built).
- **No authentication means `colaborador_id` is client-chosen** and therefore forgeable.
  Acceptable for a demo; the RPC boundary is shaped so the JWT swap is one argument.
- **Whether the phone can advertise at all** is unknown until
  `isMultipleAdvertisementSupported()` is checked on it. It no longer decides who hosts —
  the Mac is the default beacon — but if it returns false, the Android host path cannot be
  verified on this hardware at all, and that must be reported rather than glossed.
- **Whether Android can decode a macOS advertisement** is the single unresolved
  dependency of the whole BLE plan. Criterion 0 of spec 02. See the hardware constraint
  change above.
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

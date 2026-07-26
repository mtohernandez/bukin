# Progress Tracker

Update this file after every meaningful implementation change.

## Current phase

Planning complete. **Session 1 not started.** No application code exists yet.

## Roadmap

| Session | Spec                              | Outcome                                              |
| ------- | --------------------------------- | ---------------------------------------------------- |
| 1       | `specs/01-foundation.md`          | Project builds, theme matches mockups, onboarding runs |
| 2       | `specs/02-ble-proximity.md`       | Two phones: host advertises, collaborator detects the live code |
| 3       | `specs/03-supabase-checkin.md`    | End-to-end check-in landing in Postgres              |

## Current goal

Execute `context/prompts/session-1.md`.

## Completed

- Planning: `context/` filled, three specs written, three master prompts written,
  `CLAUDE.md` created.

## In progress

- None yet.

## Next up

1. Add `Bash(./gradlew *)` to the allow-list in `.claude/settings.json` — otherwise
   every build triggers a permission prompt.
2. Session 1: scaffold the Gradle monorepo and module skeleton.

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

## Open questions

- **Automatic check-out is unsolved.** Users forget to mark "Me retiro". Out of scope
  for the demo; check-in is the priority. Candidate approaches if revisited: geofence
  exit, BLE-absence timeout, or auto-closing at `fecha_fin` with `metodo = AUTO`.
- **Relay attack is accepted, not solved.** Forwarding the live broadcast over the
  internet defeats any BLE proximity scheme
  ([IEEE 8555557](https://ieeexplore.ieee.org/document/8555557/)). Mitigations not
  built: RSSI floor (noisy, risky on demo day), host roster cross-check (built).
- **No authentication means `colaborador_id` is client-chosen** and therefore forgeable.
  Acceptable for a demo; the RPC boundary is shaped so the JWT swap is one argument.
- **Which phone can host** is unknown until `isMultipleAdvertisementSupported()` is
  checked on both devices. Determine this early in session 2.
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

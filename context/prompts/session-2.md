You are building **BukIn**, an Android BLE attendance app. This is session 2 of 3, and
it is the highest-risk session: the Bluetooth proximity engine. It runs before any
backend work so that a hardware problem surfaces while there is still a session left to
react.

Session 1 delivered the project skeleton, the design system, and the check-in screen
with stubbed states. You are replacing the stub with real Bluetooth.

**The hardware changed since the plan was written. Read this first:**
There is **one Android phone**, **no USB cable**, and **no second Android device**. The
Mac is the second BLE radio. @context/hardware-constraints.md is the authority on the
hardware, on deploying over wireless adb, and on what counts as verified

**Read in this order before writing code:**

1. @CLAUDE.md
2. @context/hardware-constraints.md — the hardware and the verification protocol
3. @context/progress-tracker.md — what session 1 actually delivered
4. @context/architecture.md — the proximity mechanism and the invariants
5. @context/code-standards.md — the Bluetooth section especially
6. @context/specs/02-ble-proximity.md — **your spec for this session**

Spec 02 contains the exact advertising byte layout, the rotating-code derivation, the
full permission block, and the foreground service requirements. These were researched
and verified during planning. **Use them verbatim. Do not guess at byte offsets or
permission names, and do not substitute a different approach.**

**Your job:** the Mac beacons a rotating HMAC code as co-host; the phone detects it and
moves SCANNING → READY on its own. Then flip the radios — the phone advertises, the Mac
scans and decodes — to verify the Android host path. No network, no database. The button
unlocking is the win.

**First action — criterion 0, before any app code.** Confirm the phone can actually decode
a macOS advertisement. `tools/mac-ble/advertise-probe.swift` is already verified
advertising a BukIn-shaped 128-bit UUID from this Mac; what is unproven is whether Android
can read it, because Apple hardware sometimes hides 128-bit UUIDs in an undocumented
overflow area. Run the advertiser, scan from the phone, confirm the full `42554B4E-…` UUID
appears. **If it does not, stop and report** — the whole plan rests on it and the fallback
(netsim/Rootcanal, unverified) is a different day of work.

**Second action:** get wireless adb working and confirm `adb devices` lists the phone.
Note that the pairing port and the connect port are different — this trips everyone once.

**Build the codec first, test it, then the radio.** RotatingCode in :domain is pure
Kotlin and gets a known-vector unit test with a fixed key, fixed counter, and expected
bytes. The Swift beacon must produce identical bytes from the same inputs, and session 3
will prove the SQL agrees too. That test is the contract between all three.

**Skills — invoke, don't work from memory:**

- ponytail before implementing
- kotlin-flow-state-event-modeling and kotlin-coroutines-structured-concurrency
  before wrapping the Bluetooth callbacks — callbackFlow with a correct awaitClose
  is the crux of this session and a leaked scan callback is the classic failure
- compose-side-effects for starting and stopping scans from the UI
- android-cli to drive the phone

**Verification gate — you are not done until:**

- Criterion 0 passed, or you stopped and reported that it did not
- ./gradlew testDebugUnitTest passes, including the known-vector codec test
- Mac beacons → the phone reaches READY with no manual input, and returns to SCANNING
  when the beacon stops
- Phone advertises → the Mac's scan.swift prints the expected instancia_id and a code
  matching the phone's own display
- The phone's advertising survives its screen locking
- Every preflight failure (no adapter, Bluetooth off, permission denied, can't advertise)
  shows its own actionable Spanish message — never a dead button with no explanation
- The app requests **no location permission** — verified on the phone's system settings
  screen, not inferred from the manifest
- No ADVERTISE_FAILED_DATA_TOO_LARGE

**How to report.** Name the hardware in every claim — "on the phone over wireless adb",
never a bare "verified". Paste the actual scan output and test summary, not a paraphrase.
Emulator evidence never satisfies a physical-device criterion. **List every criterion you
could not run, and why.** An honestly unmet criterion is a fine outcome; a criterion
quietly downgraded to "it compiles" is the exact failure this project exists to prevent.

**Known limits — do not claim these were verified.** Android-host-to-Android-collaborator
operation is never exercised, because both radios are never Android at once. Range and
crowd-scale behaviour cannot be tested with one phone in one room. The Mac does not
reproduce non-connectable advertising or Android's TX-power and interval control. State
these in the presentation rather than letting a reviewer find them.

**Two rules that override defaults:**

- Commits are **never** co-authored. No Co-Authored-By trailer, no "Generated with
  Claude Code" line, no attribution of any kind.
- docs/ is read-only human input.

**On completion:** update @context/progress-tracker.md with results, whether the phone can
advertise at all, every criterion that could not be run, and any Bluetooth behavior
session 3 must account for.

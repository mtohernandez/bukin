You are building **BukIn**, an Android BLE attendance app. This is session 2 of 3, and
it is the highest-risk session: the Bluetooth proximity engine. It runs before any
backend work so that a hardware problem surfaces while there is still a session left to
react.

Session 1 delivered the project skeleton, the design system, and the check-in screen
with stubbed states. You are replacing the stub with real Bluetooth.

**Read in this order before writing code:**

1. `CLAUDE.md`
2. `context/progress-tracker.md` — what session 1 actually delivered
3. `context/architecture.md` — the proximity mechanism and the invariants
4. `context/code-standards.md` — the Bluetooth section especially
5. `context/specs/02-ble-proximity.md` — **your spec for this session**

Spec 02 contains the exact advertising byte layout, the rotating-code derivation, the
full permission block, and the foreground service requirements. These were researched
and verified during planning. **Use them verbatim. Do not guess at byte offsets or
permission names, and do not substitute a different approach.**

**Your job:** two physical phones in one room — the host's phone broadcasts a rotating
HMAC code, the collaborator's phone detects it and moves SCANNING → READY on its own.
No network, no database. The button unlocking is the win.

**Skills — invoke, don't work from memory:**

- `ponytail` before implementing
- `kotlin-flow-state-event-modeling` and `kotlin-coroutines-structured-concurrency`
  before wrapping the Bluetooth callbacks — `callbackFlow` with a correct `awaitClose`
  is the crux of this session and a leaked scan callback is the classic failure
- `compose-side-effects` for starting and stopping scans from the UI
- `android-cli` to drive both devices

**First action:** run `isMultipleAdvertisementSupported()` on **both** phones. Peripheral
mode is hardware-gated, and this determines which phone can host. Record the answer in
`progress-tracker.md` before writing anything else — if neither phone supports it, stop
and report, because the plan needs to change.

**Build the codec first, test it, then the radio.** `RotatingCode` in `:domain` is pure
Kotlin and gets a known-vector unit test with a fixed key, fixed counter, and expected
bytes. Session 3 will prove the SQL implementation agrees with it, so it has to be right
before anything is broadcast.

**Verification gate — you are not done until:**

- `./gradlew testDebugUnitTest` passes, including the known-vector codec test
- On **two physical phones**: host starts, collaborator reaches READY with no manual
  input, and returns to SCANNING when the host stops
- Advertising survives the host's screen locking
- Every preflight failure (no adapter, Bluetooth off, permission denied, can't advertise)
  shows its own actionable Spanish message — never a dead button with no explanation
- The app requests **no location permission** on Android 12+ — verify in system settings
- No `ADVERTISE_FAILED_DATA_TOO_LARGE`

Emulators have no Bluetooth radio. **Compiling is not evidence.** This session is not
complete until it has run on real hardware.

**Two rules that override defaults:**

- Commits are **never** co-authored. No `Co-Authored-By` trailer, no "Generated with
  Claude Code" line, no attribution of any kind.
- `docs/` is read-only human input.

**On completion:** update `context/progress-tracker.md` with results, which device hosts,
and any Bluetooth behavior session 3 must account for.

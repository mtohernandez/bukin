# Spec 02 — BLE Proximity Engine

**Session 2 of 3.** The highest-risk session. It runs before any backend work so that a
hardware problem surfaces with a session left to react.

## Objective

Two physical phones in one room. The host's phone broadcasts a rotating code; the
collaborator's phone detects it, validates the format, and moves from SCANNING to READY
on its own. No network, no database — the button unlocks, and that is the win.

## Read first

`CLAUDE.md` → `context/progress-tracker.md` (what session 1 left) →
`context/architecture.md` (the mechanism and the invariants) →
`context/code-standards.md` (the Bluetooth section).

## Skills

`ponytail` before implementing. `kotlin-flow-state-event-modeling` and
`kotlin-coroutines-structured-concurrency` before wrapping the Bluetooth callbacks —
`callbackFlow` with a correct `awaitClose` is the crux of this session.
`compose-side-effects` for starting and stopping scans from the UI. `android-cli` to
drive both devices.

## Non-negotiable facts

Researched and verified — do not re-derive, do not guess.

### Device capability

Peripheral mode is hardware-gated. `BluetoothAdapter.isMultipleAdvertisementSupported()`
must return true before a device can host; Bluetooth 4.1-and-lower chipsets are
central-only. **Check this on both test phones first thing this session** — it decides
which phone hosts.

### Advertising payload — encoded in the service UUID

The payload lives **inside a 128-bit service UUID**, not in service data. This is a
deliberate choice for iOS portability: `CBPeripheralManager` on iOS accepts only
`CBAdvertisementDataLocalNameKey` and `CBAdvertisementDataServiceUUIDsKey` and
[errors on anything else](https://developer.apple.com/documentation/corebluetooth/cbperipheralmanager)
— service data and manufacturer data cannot be advertised from an iPhone at all. Putting
the payload in the UUID costs nothing on Android and keeps an iOS host possible later.

Sixteen bytes, exactly one UUID:

```
128-bit service UUID:
  [0..3]   magic       0x42554B4E  ("BUKN") — constant, what the scan filter matches
  [4..7]   instancia_id uint32, big-endian
  [8..15]  code         8 bytes, truncated HMAC

rendered:  42554B4E-XXXX-XXXX-YYYY-YYYYYYYYYYYY

budget:
  AD overhead    2 bytes   (length + type 0x07)
  UUID          16 bytes
  flags          3 bytes   (added automatically by Android)
  ─────────────────────
  total      21 of 31 bytes
```

The advertised UUID changes every 30 seconds as the code rotates. That is fine — the
scan filter matches on the constant prefix.

`ScanFilter`: `setServiceUuid(uuid, mask)` with the mask
`FFFFFFFF-0000-0000-0000-000000000000`, so only the 4-byte magic must match. Android's
`ScanFilter.Builder` supports partial UUID matching this way — bits set to 1 in the mask
must match, bits set to 0 are ignored. Filtering in the Bluetooth stack rather than in
app code matters: it keeps the app off the CPU for every unrelated advertisement in a
crowded room.

`AdvertiseSettings`: `ADVERTISE_MODE_LOW_LATENCY`, `ADVERTISE_TX_POWER_HIGH`,
`setConnectable(false)`.
`AdvertiseData`: **`setIncludeDeviceName(false)`** — including the device name is the
single most common cause of `ADVERTISE_FAILED_DATA_TOO_LARGE`.

**Never set a scan response.** Non-connectable advertising with no scan response is
`ADV_NONCONN_IND`, which scanners cannot reply to. Adding a scan response turns it into
`ADV_SCAN_IND`, which invites a `SCAN_REQ` from every active scanner in range — with 300
phones in the room that is 300 devices transmitting back at the host on the same three
advertising channels. One-way broadcast is what makes this design scale; do not break
it.

`ScanSettings`: `SCAN_MODE_LOW_LATENCY` (continuous scan window, sub-second detection).

### Rotating code

TOTP-shaped. Google's Eddystone-EID is the conceptual prior art; HMAC is simpler on both
ends and verifiable in one SQL expression.

```
counter = floor((unix_seconds + clock_offset) / 30)
code    = HMAC-SHA256(instance_key, instancia_id_bytes || counter_bytes)[0..7]
```

The host generates `instance_key` itself — 16 bytes from `SecureRandom` — when it opens
the session, and keeps it in memory only. `clock_offset` is the correction returned by
the server when the session opens. Both are wired in session 3; this session may use a
hardcoded key and a zero offset, but **build the signatures to take them now** so
session 3 is a wiring change and not a rewrite.

No API ever returns the key. See spec 03 for why that direction matters.

Kotlin: `javax.crypto.Mac.getInstance("HmacSHA256")`. Counter as an 8-byte big-endian
long. The host re-derives and restarts advertising every 30 seconds.

**The scanner must always expose the most recently observed code, not the first one.**
A person can detect the host, get distracted, and tap a minute later — by then the code
they first saw is two windows stale and the server will reject it. Keep the latest
observation in state and submit that. This is the most likely source of spurious
rejections once real people with real attention spans are in the room.

The collaborator **does not validate the code** — it cannot, it has no key. It checks
the payload is well-formed and the instance matches, then treats it as READY. Real
validation happens server-side in session 3. This split is deliberate: see invariant 4
in `architecture.md`.

### Capacity — why this design holds at 300 people

The host **broadcasts**; it never connects. Scanners are passive receivers that transmit
nothing back. The number of listeners is therefore irrelevant to the radio: 5 phones and
300 phones cost the host exactly the same. This is the single most important property of
the design and the reason GATT was rejected — Android caps concurrent GATT connections
at roughly 7, so a connection-based design would have been impossible at this size.

Arrival is unsynchronized by nature. People trickle in over ten minutes, each opens the
app, detects the broadcast in under a second at `SCAN_MODE_LOW_LATENCY`, and taps. There
is no handshake, no queue, and no coordination between devices.

The real limit is **radio range, not capacity.** BLE at high TX power reaches roughly
30–50 m indoors, but each human body between transmitter and receiver costs 3–5 dB, and
a phone in a pocket costs more. In a packed 300-person auditorium the back rows may not
hear the host at all.

Do not solve this in code this session. The fix is operational and already supported by
the design: **any device holding the instance key can advertise the same code.** A
co-host standing at the back of the room is a second beacon, and collaborators cannot
tell which one they heard. Note this in `progress-tracker.md` as the scale path; build
it only if a real 300-person room is actually in play.

### Permissions

```xml
<uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN"
                 android:usesPermissionFlags="neverForLocation" />
<uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
```

`BLUETOOTH_SCAN`, `BLUETOOTH_ADVERTISE`, and `BLUETOOTH_CONNECT` are **runtime**
permissions. On Android 12+ this set requires **no location grant at all** — that is the
point, and it is what answers the top user complaint about location errors indoors. Do
not add `ACCESS_FINE_LOCATION` without the `maxSdkVersion="30"` cap.

### Foreground service

Host advertising runs in a foreground service declared
`android:foregroundServiceType="connectedDevice"`, and the app must hold
`BLUETOOTH_CONNECT` at the moment the service starts. Without the typed declaration the
service is killed on Android 14+.

## File manifest

```
core/ble/
  build.gradle.kts
  src/main/AndroidManifest.xml              ← permissions + service declaration
  src/main/kotlin/.../BleCapability.kt      ← preflight checks, sealed result
  src/main/kotlin/.../BleAdvertiser.kt      ← host side
  src/main/kotlin/.../BleScanner.kt         ← collaborator side, callbackFlow
  src/main/kotlin/.../AdvertisementPayload.kt ← encode / decode, bounds-checked
  src/main/kotlin/.../HostAdvertisingService.kt ← foreground service
  src/main/kotlin/.../BlePermissions.kt     ← version-aware permission list
domain/
  src/main/kotlin/.../crypto/RotatingCode.kt      ← derive + verify. Pure Kotlin.
  src/test/kotlin/.../crypto/RotatingCodeTest.kt  ← known-vector test
  src/test/kotlin/.../AdvertisementPayloadTest.kt
features/host/
  src/main/kotlin/.../HostScreen.kt         ← start/stop session, show live code
  src/main/kotlin/.../HostViewModel.kt
features/checkin/
  ...CheckInViewModel.kt                    ← replace session-1 stub with real scanner
app/
  src/main/kotlin/.../diagnostics/BleDiagnosticsScreen.kt
```

## What to build

### `RotatingCode` in `:domain`

Pure Kotlin, no Android. `derive(key, instanciaId, counter): ByteArray` and a
`verify(key, instanciaId, code, now)` accepting `counter ± 1` per RFC 6238 §5.2 — a host
clock running slightly fast produces `counter + 1` codes, and rejecting those would fail
legitimate check-ins for no visible reason. The SQL side in spec 03 must match. This is the
most important code in the project and the easiest to get subtly wrong — it gets a
known-vector unit test with a fixed key, fixed counter, and expected bytes. That test is
what will later prove the Kotlin and SQL implementations agree.

### `AdvertisementPayload`

Encode and decode the 12-byte layout. The decoder takes untrusted input from the air:
check length before reading, return a nullable or sealed result, never throw.

### `BleCapability` preflight

Ordered checks, each with its own distinct Spanish recovery message:

1. Adapter exists on this device
2. Bluetooth is enabled → offer to enable it
3. Runtime permissions granted → request, and handle permanent denial by pointing at
   settings
4. Host only: `isMultipleAdvertisementSupported()` → this device cannot host, use the
   other phone or manual registration

Return a sealed result the UI renders. **Never a disabled button with no explanation** —
that is the failure mode users complained about most in the existing app.

### `BleScanner`

`callbackFlow` emitting typed results. Unregister the callback in `awaitClose`. Surface
`onScanFailed` as a visible state, never a swallowed log. Include RSSI in the emitted
model — it costs nothing now and diagnostics need it.

### `BleAdvertiser` + `HostAdvertisingService`

Start advertising, re-derive and restart every 30 seconds. `onStartFailure` maps to a
visible error with the failure code translated to something a human can act on. The
foreground service keeps it alive when the host's screen locks.

### Host screen

Start/stop the session, show the live rotating code and its countdown, show advertising
status plainly. Minimal styling — this is an operator surface, not the hero screen.

### Wire the collaborator screen

Replace the session-1 stub: SCANNING while no matching advertisement, READY when one is
seen, back to SCANNING if the host disappears for a grace period. Stop the scan when the
screen leaves the foreground.

### Diagnostics screen

Reachable from a debug affordance. Shows adapter state, each permission, advertising
support, advertising status, and a live list of scan results with RSSI. This is what
turns "it doesn't work" into a diagnosis on demo day. Worth every minute it costs.

## Acceptance criteria

1. `./gradlew testDebugUnitTest` passes, including the known-vector codec test.
2. On two physical phones: host starts a session, collaborator moves SCANNING → READY
   without any manual input.
3. Host stops the session → collaborator returns to SCANNING within the grace period.
4. Advertising survives the host's screen locking.
5. Each preflight failure shows its own actionable Spanish message. Turning Bluetooth
   off mid-session produces a clear recovery state, not a hang.
6. The app requests **no location permission** on an Android 12+ device — verify in
   system settings.
7. No `ADVERTISE_FAILED_DATA_TOO_LARGE`.
8. Leaving and returning to the screen does not leak a scan callback.

## Verification

```bash
./gradlew testDebugUnitTest
./gradlew installDebug   # on BOTH phones
```

Then, in one room: phone A hosts, phone B checks in. Confirm each criterion above. Use
`android-cli` for screenshots of both devices. **This session is not complete until it
has run on two real phones** — compiling is not evidence.

Record which phone can host in `progress-tracker.md`.

## Out of scope — do not build

Supabase, networking, persistence. GATT connections or the offline relay. Check-out.
Manual registration (session 3). Server-side code validation (session 3) — the
collaborator only format-checks.

## On completion

Update `context/progress-tracker.md` with results, which device hosts, and any BLE
behavior discovered that sessions 3 must account for.

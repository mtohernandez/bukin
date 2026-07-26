# Hardware Constraints and Verification

This file overrides any earlier statement in this repository about testing on two phones.
Read it before writing BLE code and before claiming anything works.

## What actually exists

| Asset | Detail |
| ----- | ------ |
| Development machine | Apple Silicon **M4** Mac. Bluetooth controller BCM_4388C2, LE + GATT. Swift 6.3.3 and CoreBluetooth available. |
| Android device | **One** phone: **Samsung Galaxy A54 5G (SM-A546E), Android 16, API 36.** Confirmed connected over wireless adb 2026-07-26. |
| USB cable | **None.** No USB-C to USB-C. The phone cannot be wired to the Mac. |
| Second Android device | **None, and none is coming.** |

Everything below follows from those four rows. The original plan assumed two phones and a
cable; neither assumption survives, so the verification strategy is rebuilt around a Mac
that happens to be a fully capable BLE radio.

## Deploying to the phone — no cable

### Primary: wireless debugging (Android 11+)

Full `adb`, so `installDebug`, `logcat`, screenshots, and `android-cli` all work exactly
as they would over a cable.

On the phone, once: **Settings → Developer options → Wireless debugging → on**.

**Do not read ports off the phone screen — discover them.** Both endpoints advertise over
mDNS, so `adb` finds them itself. The only thing that must come from the human is the
6-digit pairing code, which is the security boundary and cannot be discovered by design.

```bash
export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"

# Both ports, discovered. The pairing service only appears while the
# "Pair device with pairing code" dialog is open on the phone.
adb mdns services
#   adb-<serial>-xxxxxx  _adb-tls-pairing._tcp  192.168.111.163:45865
#   adb-<serial>-xxxxxx  _adb-tls-connect._tcp  192.168.111.163:35487
# (addresses and ports are illustrative — they change; always read them from mdns)

adb pair 192.168.111.163:45865 <6-digit-code>   # pairing port + the code
adb connect 192.168.111.163:35487               # connect port — a DIFFERENT port

adb devices -l
./gradlew installDebug
```

Scripted, so no port is ever typed:

```bash
PAIR=$(adb mdns services | grep pairing     | awk '{print $3}' | head -1)
CONN=$(adb mdns services | grep tls-connect | awk '{print $3}' | head -1)
adb pair "$PAIR" <6-digit-code> && adb connect "$CONN"
```

Things that waste time if you do not know them:

- **The pairing port and the connect port are different.** Discovery sidesteps this
  entirely; typing them by hand does not.
- **`adb connect` fails before pairing** with a bare "failed to connect". That is normal
  and means the Mac is not yet a trusted device — it is not a network problem.
- **The connect port changes** after a reboot or after toggling wireless debugging.
  Re-discover and reconnect. The pairing itself survives.
- **The phone often appears twice** — once as `IP:PORT` and once as the mDNS service name
  — and Gradle then fails on an ambiguous target. Drop one:
  `adb disconnect <mdns-service-name>`, and pin the rest with
  `export ANDROID_SERIAL=<ip>:<port>`.
- **`android screen capture` did not work against the wireless device**; it produced no
  file. `adb exec-out screencap -p > shot.png` works and is what session 1 used on the
  phone. Prefer it.
- **Same network, no client isolation.** Common on guest and corporate Wi-Fi. Phone
  hotspot with the Mac joined to it is the reliable fallback.

### Fallback: serve the APK over local HTTP

If wireless debugging cannot be established, this needs no `adb` at all. It costs the
logcat and the fast redeploy loop, so use it only when the primary path fails.

```bash
./gradlew assembleDebug
cd app/build/outputs/apk/debug   # app-debug.apk, ~12 MB
python3 -m http.server 8000
```

On the phone, browse to `http://<mac-lan-ip>:8000/app-debug.apk` and install. The browser
needs "install unknown apps" permission once. Get the Mac's address with
`ipconfig getifaddr en0`.

## Testing BLE with one phone — the Mac is the second radio

The Mac can both advertise and scan BLE. That makes it a legitimate second endpoint, and
not by accident:

> `architecture.md` puts the payload **inside the 128-bit service UUID** rather than in a
> service-data field, because `CBPeripheralManager` accepts only local name and service
> UUIDs. That was decided for iOS portability. The same constraint is what makes a Mac
> able to emit a byte-correct BukIn advertisement.

A Mac beacon is also already sanctioned by the design: *"any device holding the instance
key can advertise the same code."* A Mac is such a device.

### Verified on this machine

`tools/mac-ble/advertise-probe.swift`, compiled with `swiftc` and run:

```
STATE: poweredOn
ADVERTISE OK: 42554B4E-0001-0002-0003-A1B2C3D4E5F6
isAdvertising = true
```

A custom 128-bit service UUID in the BukIn layout advertises successfully, with no
entitlement or permission problem. **This proves the Mac transmits. It does not prove the
Android phone can decode it** — see the unknown below.

### Role assignment

| Role | Runs on | Verifies |
| ---- | ------- | -------- |
| **Default — collaborator** | The phone | The primary flow: scan, filter, decode, SCANNING → READY. This is the flow that "gets the most design care", so it is the one tested against real Android radio code. |
| **Default — host beacon** | The Mac (Swift CLI) | Supplies a rotating, byte-correct advertisement for the phone to find. |
| **Flipped — host** | The phone | `BleAdvertiser`, the foreground service, and advertising surviving screen lock. |
| **Flipped — scanner** | The Mac (Swift CLI) | Confirms the phone's advertisement is well-formed and decodes to the expected instance and code. |

Running both directions covers both sides of the protocol. Build the Mac scanner as well
as the Mac advertiser — the flipped direction is the only way to verify the host path.

## The one unknown that must be resolved first

**Can the Android phone actually decode a macOS-originated advertisement?**

Apple devices sometimes place 128-bit service UUIDs in an undocumented "overflow area"
that non-Apple hardware cannot read. This is documented for *backgrounded iOS apps*; a
foreground macOS process is expected to use the standard AD structure, but **expected is
not verified**.

This is the first task of session 2, before any app code is written:

1. Run the Mac advertiser.
2. Scan from the phone with any off-the-shelf BLE scanner app, or a throwaway Android
   scan loop.
3. Confirm the full `42554B4E-…` UUID appears.

If it does not appear, the Mac-as-host plan is dead and the fallback is netsim (below).
**Stop and report rather than building on top of an unverified radio.**

## Where the Mac is not a faithful Android host

Real limits, to be stated rather than discovered:

- **No TX power or advertising-interval control.** CoreBluetooth exposes neither, so
  `ADVERTISE_MODE_LOW_LATENCY` and `ADVERTISE_TX_POWER_HIGH` have no macOS equivalent.
  Timing and range characteristics will differ from an Android host.
- **Not `ADV_NONCONN_IND`.** The Mac cannot be made reliably non-connectable the way
  `setConnectable(false)` does on Android. The scale argument in `architecture.md` rests
  on non-connectable advertising, and the Mac does not reproduce it.
- **Range testing is meaningless** on this setup. The 30–50 m indoor figure and the
  3–5 dB per-body attenuation cannot be validated with one phone in one room.
- **Android-host-to-Android-collaborator is never exercised.** Both radios are never
  Android at the same time.

Consequence: the Mac verifies **the protocol and the application logic**. It does not
verify **Android host radio behaviour**. Say so in the presentation. A reviewer who hears
the limit from you reads it as rigour; one who finds it reads it as a gap — the same
principle `architecture.md` already applies to the relay attack and the identity hole.

## Backup path: netsim / Rootcanal — UNVERIFIED

`~/Library/Android/sdk/emulator/netsimd` ships with the installed SDK. It is Google's
emulated Bluetooth controller and is designed to let two emulator instances exchange real
BLE traffic, which would close the Android-to-Android gap.

**Nobody has tried it on this project.** Treat it as an experiment with unknown payoff,
not a plan. Attempt it only if the Mac advertiser fails the decode check above, and
timebox it — a day lost to emulator Bluetooth plumbing is worse than an honestly stated
limitation. The relevant emulator flag is `-packet-streamer-endpoint`.

Note the existing `pixel_test` AVD is a 32-bit `arm` image that cannot boot on this Mac.
Use `medium_phone` (arm64) or create another with `android emulator create medium_phone`.

## Verification protocol

The rule this project cares about is not "test on two phones". It is **never report
something as working when it has not been observed working.** Session 1 reported an
emulator install against an acceptance criterion that said physical device; that is the
failure mode to prevent.

### What each claim requires

| Claim | Evidence required | Emulator sufficient? |
| ----- | ----------------- | -------------------- |
| Code compiles | `./gradlew assembleDebug` output | n/a |
| Codec is correct | `./gradlew testDebugUnitTest`, known-vector test passing | Yes — pure JVM |
| Payload encodes/decodes | Unit test, including malformed and short input | Yes — pure JVM |
| A screen renders correctly | Screenshot compared against `docs/assets/` | Yes, for layout only |
| A screen behaves correctly | Screenshot or UI dump **from the phone** | No |
| **The phone detects a beacon** | Phone scanning, Mac advertising, observed state change | **No** |
| **The phone advertises correctly** | Mac scanning, phone advertising, decoded UUID printed | **No** |
| Advertising survives screen lock | Phone locked, Mac still receiving | **No** |
| No location permission requested | The phone's own system permission screen | **No** |
| Range or capacity behaviour | Not verifiable on this hardware — **do not claim it** | — |

### Reporting rules

1. **Name the hardware in the claim.** "Verified on the phone over wireless adb", or
   "verified on the arm64 emulator" — never a bare "verified".
2. **Emulator evidence never satisfies a physical-device criterion.** If the phone is not
   reachable, say the criterion is unmet and why. An unmet criterion reported honestly is
   a fine outcome; a criterion quietly downgraded is not.
3. **Paste the actual output.** The scan result, the test summary, the decoded UUID. Not
   a paraphrase.
4. **"It compiles" is never evidence that BLE works.** It was not evidence when the rule
   said two phones and it is not evidence now.
5. **If a step could not be run, list it as not run.** Do not omit it from the summary.

## Consequences for the demo

The success criterion "two phones, one room" cannot be met and has been rewritten in
`project-overview.md`. The demo is **one phone plus the Mac**: the Mac holds the instance
key and beacons; the phone walks the collaborator flow to a registered attendance.

This is a weaker physical demonstration than two phones and a stronger architectural one,
provided it is framed honestly — the beacon is a co-host, which the design explicitly
allows, and the host role on Android is verified separately by flipping the radios.

# Mac BLE tools

The project has one Android phone and no second device. This Mac is the second BLE radio.
See `context/hardware-constraints.md` for the full picture.

## `advertise-probe.swift`

A throwaway probe, not the real beacon. It answers one question: can this Mac advertise a
custom 128-bit service UUID in the BukIn layout?

```bash
swiftc -O advertise-probe.swift -o advertise-probe
./advertise-probe
```

Verified on the M4 (2026-07-26):

```
STATE: poweredOn
ADVERTISE OK: 42554B4E-0001-0002-0003-A1B2C3D4E5F6
isAdvertising = true
```

It now holds the advertisement up instead of exiting the moment it starts, because the
radio stops with the process and criterion 0 needed the phone to have something to find.
Superseded by `beacon.swift` for anything real — this one advertises a fixed UUID and does
no crypto.

**Criterion 0 passed on 2026-07-26.** The Galaxy A54 (Android 16) decoded this Mac's
`42554b4e-0001-0002-0003-a1b2c3d4e5f6` at ~-55 dBm. A foreground macOS process uses the
standard AD structure — the overflow area is a backgrounded-iOS problem, not a macOS one.

## `beacon.swift` — the Mac as co-host

Derives the same rotating HMAC code as `:domain`'s `RotatingCode`, on the same 30-second
window, re-advertising on each boundary and printing the live code.

```bash
swiftc -O BukInProtocol.swift beacon.swift -o beacon
./beacon                              # instancia 42, the known-vector key
./beacon --instancia 7 --key aabb...  # anything else
```

## `scan.swift` — the flipped direction

The only way to verify the phone's own advertising with no second Android device. Scans
unfiltered and matches the magic in code, because CoreBluetooth cannot express Android's
prefix mask — `scanForPeripherals(withServices:)` takes whole UUIDs, and the whole UUID
changes every 30 seconds by design.

```bash
swiftc -O BukInProtocol.swift scan.swift -o scan
./scan --instancia 42
#   [13:27:10] instancia_id=42 code=e2d9fc66f227c1ba rssi=-52
```

Pass `--key` to also check the code against its own derivation. Normally you cannot: the
host generates the key with `SecureRandom` and never discloses it.

## The shared contract

`BukInProtocol.swift` holds the protocol and is compiled into both tools. It must agree
byte for byte with `:domain`'s `RotatingCode` and `AdvertisementPayload`, and with the
`pgcrypto` expression session 3 verifies against. Both tools run `selfCheck()` on startup
against the known vector and refuse to start if it fails:

```
key 000102030405060708090a0b0c0d0e0f · instancia 42 · counter 58000000
  -> code 67e94bf8a08959ea
  -> uuid 42554B4E-0000-002A-67E9-4BF8A08959EA
```

Same numbers as `RotatingCodeTest`. Change them in one place and you change them in three.

Both tools print unbuffered and run until ctrl-C — `setvbuf` is not optional here, because
stdout is block-buffered when piped and a process that never exits never flushes.

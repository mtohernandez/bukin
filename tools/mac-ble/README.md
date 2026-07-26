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

It exits as soon as advertising starts, so it is useless for an actual scan test — it
stops transmitting on exit. To hold the advertisement up while scanning from the phone,
delete the `exit(0)` in `peripheralManagerDidStartAdvertising` and let the run loop
continue.

**What this proves:** CoreBluetooth accepts the UUID shape and the radio transmits.
**What it does not prove:** that an Android device can decode it. Apple hardware sometimes
moves 128-bit UUIDs into an undocumented overflow area. Confirming the phone actually sees
this UUID is the first task of session 2 — do it before building anything on top.

## What session 2 builds here

- `beacon.swift` — the real host beacon: derives the rotating HMAC code on the same 30s
  window as the Kotlin `RotatingCode`, re-advertises each window, prints the current code
  so it can be cross-checked against the phone.
- `scan.swift` — the flipped direction: scans for the `42554B4E` prefix and prints the
  decoded instance id, code, and RSSI. This is the only way to verify the phone's own
  advertising, since there is no second Android device.

Both must agree byte-for-byte with `:domain`'s `RotatingCode`. The known-vector test is
the shared contract — same fixed key, same counter, same expected bytes on both sides.

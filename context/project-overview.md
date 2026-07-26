# BukIn

## Overview

BukIn lets a person confirm their own attendance to an in-person training session from
their phone, in one tap, without anyone typing a list afterward. A host opens the
session and their phone broadcasts a rotating code over Bluetooth Low Energy.
Collaborators in the same room detect that code, which unlocks their check-in button.
The confirmation goes straight to the database with a verifiable proof of presence.

It is built for **Buk**, a Chilean HR platform, for deployment in **Colombia**. It does
not try to revolutionize attendance — it attacks the specific pains that make the
existing app frustrating.

Today, attendance for in-person courses is taken on a printed sheet and typed in later:
80–100 names after every course, mistyped IDs, incomplete lists, no evidence the person
who signed actually attended, and walk-ins who fall out of the record entirely.

## Goals

1. A collaborator confirms attendance in one tap, physically present, no admin typing.
2. Presence is *proven*, not asserted — a screenshot or a text message cannot fake it.
3. The demo runs reliably on the hardware that exists — one phone and the Mac as co-host
   beacon — in a real room, on demo day. See `context/hardware-constraints.md`.

## Personas

- **Colaborador** — attends the course. Opens the app, waits for their host to be
  detected, taps Check In. This is the primary flow and gets the most design care.
- **Anfitrión** — runs the course. Opens the session, their phone becomes the beacon,
  sees a live roster of who has arrived, and can register someone manually if their
  phone is dead or incompatible.
- **Administrador** — consumes the data downstream. **Not built.** No admin surface
  exists in this demo; the architecture accounts for them, the code does not.

## Core user flow

The four states from the mockups in `docs/assets/`:

1. **SCANNING** — "Estamos localizando a tu anfitrión…" with the proximity
   illustration. The app is listening for the host's broadcast.
2. **OFFLINE variant** — same, plus a card explaining that we can still find the host,
   but internet is needed to save the attendance. The mockup's original line promised
   presence alone was enough; that is not true in v1, and the corrected copy is in
   `ui-context.md`.
3. **READY** — the host's code was detected and validated. The large blue **Check In**
   button appears with its pulsing halo.
4. **SUCCESS** — green check, "Registraste tu asistencia exitosamente."

The ticket-style header card (course name, date, time, duration, check-in and exit
times) is persistent across all four states.

## Requirements coverage

From the solution document in `docs/overview.md` §1.

| Req | What it demands                                     | Where it lands                        |
| --- | --------------------------------------------------- | ------------------------------------- |
| R1  | One action, no host typing                          | Built — the check-in flow             |
| R2  | Walk-ins enroll on the spot                         | Built — INSERT branch, `origen=WALK_IN`|
| R3  | Verify physical presence, not just a session        | Built — BLE rotating code             |
| R4  | One record, no duplicates, no error screen          | Built — `ON CONFLICT … DO UPDATE`     |
| R5  | Punctuality of entry/exit                           | Partial — entry only, no check-out    |
| R6  | Concurrency in a short burst                        | Built — per-row UNIQUE, no counter    |
| R7  | Propagate to the external course system             | **Documented, not built** — outbox and queue stay in the architecture document |

## Scope

### In scope

- 3-screen onboarding introducing the app's capabilities
- Collaborator check-in: scan → validate → confirm → success
- Host mode in the same APK: open session, broadcast, live roster
- Manual registration by the host (the fallback when a phone can't participate)
- Supabase schema, the rotating-code verification function, and RLS

### Out of scope

| Not building                    | Why                                                       |
| ------------------------------- | --------------------------------------------------------- |
| Authentication                  | Deliberately cut. Collaborator is picked, not logged in.  |
| Check-out / "Me retiro"         | Automatic check-out is genuinely unsolved — people forget. Check-in is the priority. |
| BLE offline relay (doc §2.5)    | Roughly doubles the Android work and is the most fragile part on real hardware. |
| Outbox worker, queue, DLQ       | Architecture-document material. No external system to talk to in a demo. |
| Vite/React download page        | Specced in `architecture.md`, built by nobody this round. |
| iOS                             | Android only.                                             |
| Admin panel, reporting, certificates | Explicitly out of scope in the original assessment.  |

## Pain points we are answering

Real Play Store reviews of the current Buk attendance app live in `docs/feedback.md`.
Each one below is a design constraint, not a nice-to-have.

| Complaint                                       | BukIn's answer                                                     |
| ----------------------------------------------- | ------------------------------------------------------------------ |
| Logged out daily; waiting on an email token     | No auth in the demo; the architecture never puts a token in the critical path of marking attendance. |
| Config resets on every app update               | No per-company URL to re-enter. Nothing to reconfigure.            |
| "Location error" while standing in the office   | **We request no location permission at all** on Android 12+. There is no GPS to fail. |
| Facial recognition fails or crashes the app     | No biometrics. Proximity is the proof.                             |
| Buttons don't change state — did it register?   | Every state is visually distinct and the success state is unmissable. This is the single most important UI rule in this project. |
| No way to see history or whether a punch synced | Out of scope, but the success screen is unambiguous about the record landing. |
| Fails to load on mobile data                    | Presence is verified locally over BLE. The network is only the delivery channel. |
| Punches stuck in offline memory                 | Acknowledged; the offline banner sets the right expectation rather than lying. |
| Kept from the positive reviews                  | The simple layout, and one-click registration with no extra hardware. Do not add steps. |

## Success criteria

Rewritten for the hardware that exists — one phone and a Mac, no second Android device
and no USB cable. See `context/hardware-constraints.md`.

1. One phone, one Mac, one room: the Mac beacons as a co-host, the phone's app moves
   SCANNING → READY on its own, taps once, and reaches SUCCESS.
2. Flipped: the phone advertises as host and the Mac's scanner decodes the expected
   instance id and rotating code. This is what verifies the Android host path.
3. The attendance row is in Postgres with `metodo_confirmacion = 'BLE'`.
4. Tapping Check In twice produces one row and no error screen.
5. A code captured from the broadcast and replayed a minute later is **rejected**.
6. Onboarding runs on first launch and the UI matches the mockups.

**Not demonstrable on this hardware, and to be stated rather than implied:**
Android-host-to-Android-collaborator operation, radio range, and behaviour at crowd
scale. Both radios are never Android simultaneously, and one room with one phone proves
nothing about 30–50 m through a packed hall. The architecture argument for those stands
on its own; the demo does not evidence them.

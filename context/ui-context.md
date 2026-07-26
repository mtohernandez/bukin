# UI Context

## Theme

Light only. No dark mode. The reference is **the entrance to a concert, but corporate**:
the course is an event, the attendance record is a ticket, and checking in should feel
like being admitted. Warm, confident, uncluttered — a very light periwinkle field with
one saturated blue object holding the content and a single obvious action.

The mockups in `docs/assets/` are the base UI, not the ceiling. Where a mockup feels
cluttered, simplify to what is needed to operate. Nothing decorative earns its place if
it competes with the check-in action.

## Colors

Derived entirely from four Buk brand values. Every other shade is a ramp, tint, or
gradient of these. Defined once as Compose tokens in `:core:designsystem`; no composable
outside that module may contain a hex value.

| Role                    | Token              | Value     |
| ----------------------- | ------------------ | --------- |
| Primary / brand blue    | `BukBlue`          | `#2F4DAA` |
| Page background         | `BukBackground`    | `#F7F9FF` |
| Primary text / near-black | `BukInk`         | `#030819` |
| Success                 | `BukSuccess`       | `#2BAB51` |

Derived, all generated from the four above:

| Role                    | Token              | Derivation                          |
| ----------------------- | ------------------ | ----------------------------------- |
| Ticket card gradient    | `BukBlueGradient`  | `BukBlue` → a lighter tint of itself, top-left to bottom-right |
| Button halo rings       | `BukBlueHalo`      | `BukBlue` at descending alpha, layered concentric |
| Muted text              | `BukInkMuted`      | `BukInk` at reduced alpha           |
| Card / surface          | `BukSurface`       | White over `BukBackground`          |
| Border                  | `BukBorder`        | `BukInk` at low alpha               |
| Illustration figures    | `BukInkSoft`       | Desaturated blue-grey between `BukBlue` and `BukInk` |
| Error                   | `BukError`         | Standard Material error, used sparingly — most failures are informational, not alarming |

Map these into a Material 3 `ColorScheme`: `BukBlue` → `primary`, `BukBackground` →
`background`, `BukInk` → `onBackground`, `BukSuccess` for the success state.

## Typography

System default sans (Roboto). No custom font — it is weight and scale that carry the
design, not a typeface.

| Role                | Weight   | Notes                                       |
| ------------------- | -------- | ------------------------------------------- |
| Ticket time         | Bold     | The largest text on screen. `10:00 AM`      |
| Ticket course name  | SemiBold | `Manejo de alimentos`                       |
| Button label        | Bold     | `Check In` — large, high contrast           |
| State message       | SemiBold | `Estamos localizando a tu anfitrión…`       |
| Labels / captions   | Regular  | `Duración:`, `Check In`, `Salida`           |
| Footer microcopy    | Regular  | Muted, smallest on screen                   |

## Shape

| Context                    | Radius        |
| -------------------------- | ------------- |
| Ticket card, outlined cards| Large         |
| Check In button            | Medium-large  |
| Small pills (`Ticket` badge)| Full          |

## The ticket card

The signature component, persistent across all four collaborator states. Lives in
`:core:designsystem` and is built once.

```
┌────────────────────────────────────────┐
│ Manejo de alimentos           ( Ticket )│   ← title + dark pill badge
│                                        │
│ Lunes, 13 de Julio        Duración:    │   ← labels
│ 🕐 10:00 AM               2 horas      │   ← large values
│                                        │
│ Check In · · · · · · · · · · · Salida  │   ← dotted stub separator
│ 9:50 AM                     12:00 PM   │
└────────────────────────────────────────┘
     ┌──────────────────────────────┐
     │      Necesito ayuda ☀        │        ← torn-stub strip, tucked under
     └──────────────────────────────┘
```

Blue gradient fill, white text. The dotted separator is what makes it read as a ticket
stub rather than a generic card — keep it.

## Screen states

The collaborator screen is a state machine. The ticket header and the footer never
change; only the center region does.

| State    | Center content                                                    |
| -------- | ----------------------------------------------------------------- |
| SCANNING | Two-figure proximity illustration with signal arcs. "Estamos localizando a tu anfitrión…" |
| OFFLINE  | Same, plus an outlined card — see the copy correction below       |
| READY    | Large `Check In` button with layered concentric halo rings, gently pulsing |
| SUCCESS  | Large green check. "Registraste tu asistencia exitosamente."      |
| ERROR    | Not in the mockups. Follow the same shape: one clear icon, one sentence in Spanish saying what to do. Never a raw error code. |

**Copy correction — the offline card.** The mockup reads *"¿Sin conexión? Tranqui, solo
debes estar en la sala de tu anfitrión."* That promise is not true in v1: the offline
relay is cut, so without internet a person genuinely cannot check in. A reassuring
message that turns out to be false produces exactly the "did it register?" anxiety this
app exists to remove. Use instead:

> **Sin conexión**
> Podemos encontrar a tu anfitrión, pero necesitas internet para guardar tu asistencia.

Same card, same warmth, no lie. It also stays accurate: BLE detection really does work
offline — only the save needs the network.

**Every state must be unmistakably different at a glance.** This is the single most
important rule in the UI: the top complaint about the existing app is that buttons
never change state, so users never know whether their attendance registered. The
transition into SUCCESS should be impossible to miss.

## Seamlessness

The product goal for v1 is that checking in feels like nothing happened — you walked in,
you tapped once, you're done. Seamless is not a vibe here; it is these rules, and they
are testable.

**The happy path is one tap.** Opening the app to a registered attendance must cost
exactly one deliberate action. Anything else the app needs, it does by itself.

1. **Nothing is manually started.** Scanning begins when the screen opens. There is no
   "Buscar anfitrión" button, no pull-to-refresh, no retry the user has to find.
2. **The button unlocks itself.** SCANNING → READY happens with no interaction.
3. **Success is terminal.** No confirmation dialog, no "OK" to dismiss, no second tap to
   acknowledge. The green check is the end of the interaction.
4. **Never a dead disabled control.** Every blocked state names its cause and offers the
   action that unblocks it, in one tap. Bluetooth off → an inline enable button, not
   "go to Settings".
5. **Permissions are asked in context, never as a wall at launch.** Ask immediately
   before the capability is needed, with one plain sentence saying why. Onboarding
   explains; it does not request.
6. **No spinner without a sentence.** If the user is waiting, the screen says what for.
7. **No dead ends.** Every error state has a next action. "Algo salió mal" with no way
   forward is a bug, not a state.
8. **Nothing is asked twice.** Onboarding shows once. The collaborator identity is
   chosen once and remembered. Permissions granted are never re-requested.
9. **The app never blames the user.** "No pudimos encontrar a tu anfitrión" — not "You
   are out of range."

The one thing that may interrupt the flow is a genuine failure the user must resolve
(Bluetooth off, no connection). Those interrupt *early and clearly*, before the person
believes they are done — never after.

## Footer

Persistent, muted, centered:

```
              ·buk in·
  Usamos tecnología de proximidad,
     no desactives tu bluetooth.
```

The wordmark sets `in` in italic. The microcopy does real work — it explains why
Bluetooth matters before the user hits a failure, so keep it visible in every state.

## Motion

Restrained. Three moments earn animation:

1. The halo rings around Check In pulse slowly — a beacon, echoing the proximity idea.
2. SCANNING → READY: the button arrives with confidence, not a jump cut.
3. READY → SUCCESS: the biggest moment in the app. This is the payoff.

Nothing else moves. Respect the system's reduced-motion setting.

## Voice

Colombian Spanish, warm and reassuring, never bureaucratic. The mockups set the register
with lines like "Tranqui, solo debes estar en la sala de tu anfitrión." — informal,
calming, solving the user's worry before they voice it. Match that *tone* in every new
string, especially error and permission messages, where the instinct to sound technical
is strongest and most wrong.

Warmth never buys a promise the app cannot keep, though — that specific line was
corrected above precisely because it was reassuring and untrue. Reassure about what is
real; be plain about what is not.

All copy lives in one `strings.xml`. No hardcoded strings in composables.

## Icons

Material Symbols, outlined. Only what the mockups need: a clock, the success check, the
sun glyph on the help strip. The proximity illustration and the wordmark are custom
vector drawables.

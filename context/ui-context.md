# UI Context

The standing design authority. Tokens, motion, voice, and the rules a screen has to satisfy
before it is done. `context/specs/04-design.md` is the work order that gets the codebase here;
this file is what stays true afterwards.

Rewritten in session 4 after auditing every UI file and measuring every colour pair. Where an
earlier version of this file disagreed, it was wrong — and in three places it was specifying
text nobody could read.

## Theme

Light only. No dark mode, no dynamic colour. The reference is **the entrance to a concert,
but corporate**: the course is an event, the attendance record is a ticket, and checking in
should feel like being admitted. A soft periwinkle field, one deep blue object holding the
content, and a single obvious action.

The mockups in `docs/assets/` are the base, not the ceiling. Where a mockup is cluttered,
simplify to what is needed to operate. Nothing decorative earns its place if it competes with
the check-in action.

## Colours

**Four brand values. `Color.kt` is the only file in the project that may contain a hex
literal, and everything else is a ramp, tint, or alpha of those four.** If a new shade is
needed, derive it there. Never add a fifth constant, never inline a hex in a composable.

| Role | Token | Value |
| --- | --- | --- |
| Primary / brand blue | `BukBlue` | `#2F4DAA` |
| Near-white base | `BukBackground` | `#F7F9FF` |
| Primary text / near-black | `BukInk` | `#030819` |
| Success (brand) | `BukSuccess` | `#2BAB51` |

Derived — every one of these is computed, and the comment shows what it lands on:

| Token | Derivation | Lands on |
| --- | --- | --- |
| `BukField` | `BukBlue @ 10%` over `BukBackground` | `#E3E8F6` — the page |
| `BukBlueDeep` | `lerp(BukBlue, BukInk, 0.35)` | `#1D3373` — deep end of the ticket |
| `BukBlueGradient` | `BukBlue → BukBlueDeep` | the ticket fill |
| `BukSuccessInk` | `lerp(BukSuccess, BukInk, 0.30)` | `#1C7544` — all success meaning |
| `BukInkMuted` | `BukInk @ 58%` | muted body and captions |
| `BukOnBlueMuted` | `White @ 72%` | labels on the ticket |
| `BukOnBlueFaint` | `White @ 55%` | the tear dots — a graphic, not text |
| `BukSurface` | `White` over `BukField` | cards |
| `BukBorder` | `BukInk @ 12%` | hairlines |
| `BukInkSoft` | desaturated blue-grey | illustration figures |
| `BukError` | Material error | used sparingly — most failures are informational |

Mapped into a Material 3 `ColorScheme`: `BukBlue → primary`, **`BukField → background`**,
`BukInk → onBackground`, `BukSuccessInk → tertiary`.

### The page field is periwinkle, not near-white

Resolved in session 4, closing an open question that had been live since session 1.
`BukBackground` stays `#F7F9FF` as a brand value, but the *page* is `BukField` — `BukBlue` at
10% over it, ≈`#E3E8F6`. That is what every mockup renders, it gives the blue ticket a field
to sit on instead of floating on white, and it introduces no fifth hex.

### The ticket gradient runs deep, not light

It used to run `BukBlue → lighter`. That direction cannot be made legible: **pure white on
the old light end measured 4.27:1**, below the 4.5:1 minimum, and no alpha of white fixes it —
white at 92% still only reaches 4.44:1. Inverting the gradient makes `BukBlue` itself the
lightest point of the card, which is the worst case, and every level clears.

### Contrast is measured, not assumed

Every pair below was computed with WCAG 2.1 relative luminance over composited values, at the
worst end of every gradient. **Any new pair must be computed before it ships.**

| Pair | Worst case | Need | Result |
| --- | --- | --- | --- |
| white on the ticket gradient | 7.60:1 | 4.5 | PASS |
| `BukOnBlueMuted` on the ticket | 4.80:1 | 4.5 | PASS |
| `BukOnBlueFaint` tear dots (graphic) | 3.49:1 | 3.0 | PASS |
| `BukInkMuted` on `BukField` | 4.66:1 | 4.5 | PASS |
| `BukInkMuted` on a white surface | 4.96:1 | 4.5 | PASS |
| `BukSuccessInk` text on `BukField` | 4.65:1 | 4.5 | PASS |
| `BukSuccessInk` text on white | 5.70:1 | 4.5 | PASS |
| white on a `BukSuccessInk` fill | 5.70:1 | 4.5 | PASS |
| `BukBlue` text on `BukField` | 6.20:1 | 4.5 | PASS |
| `BukInk` on `BukField` | 16.27:1 | 4.5 | PASS |
| `BukInkSoft` illustration (graphic) | 4.43:1 | 3.0 | PASS |

**`BukSuccess` never carries text or an icon.** At `#2BAB51` it measures 2.98:1 on white and
2.43:1 on the field — it fails as text *and* as a graphic. It remains one of the four brand
values and is used only as a decorative accent (the success halo). `BukSuccessInk` carries
every piece of success meaning in the app.

## Typography

**Inter** for body and UI. **Merriweather** for display only — ticket times, the success line,
onboarding headlines. Nothing else gets a serif; a serif on a button label is the fastest way
to make this look like a template.

Both bundled and subset to latin + latin-ext, **296 KB together**. Not downloadable: variable
fonts do not work through the Google Fonts provider, and the downloadable fallback chain
flashes on first launch, which is exactly the seam this product exists to remove.

Inter is the **variable** file, instanced at four weights. Merriweather is a **static Bold**:
it is display-only and the scale below never asks it for another weight, and the variable file
is 462 KB against 187 KB for the one instance. Weights nothing can reach are not a trade-off.

**Every style the app uses must be declared.** This is not a style preference — before session
4, `headlineSmall`, `bodySmall`, `titleSmall` and `labelLarge` were consumed by four screens
and declared nowhere, so those screens silently rendered Material defaults in Roboto while the
check-in screen rendered the Buk scale. The app looked like two apps because it was two.

| Role | Family | Weight |
| --- | --- | --- |
| Ticket time, success line | Merriweather | Bold |
| Onboarding headline | Merriweather | Bold |
| Screen title | Inter | SemiBold |
| Ticket course name | Inter | SemiBold |
| Button label | Inter | SemiBold |
| State message | Inter | Medium |
| Body | Inter | Regular |
| Labels, captions | Inter | Regular |
| Footer microcopy | Inter | Regular |

Line height and letter spacing are set per style, never inherited.

## Spacing

`2 · 4 · 6 · 8 · 12 · 16 · 20 · 24 · 32 · 40 · 48 · 64`

The scale used to skip 12 and 20, which is why components reached past it — one of them
computed `BukSpacing.xs + 2.dp` to land on 6. **If a value is not on the scale, the scale is
wrong; do not do arithmetic at the call site.**

**Gutter is responsive**: `20.dp` below 360dp screen width, `24.dp` at or above. One UI asks
for at least 24dp side margins; the mockups use 20, and a 320dp phone cannot afford 24.

## Shape

`4 · 8 · 12 · 16 · 20 · 28 · Full`

| Context | Radius |
| --- | --- |
| Ticket card, instance cards, sheets | 28 |
| Notice cards, surfaces | 20 |
| Buttons, inputs | 16 |
| Skeleton blocks, small chips | 12 |
| Hairline details | 8 / 4 |
| Pills, avatars, the success container | Full |

`RoundedCornerShape` does not appear outside `Shape.kt` and `TicketShape.kt`. The ticket
silhouette carries real notches at the tear line, and instance cards share that shape so a row
and the screen it opens are visibly the same object.

## Motion

Built on stable `spring()`. **Material 3 Expressive is not adopted** — `MaterialShapes` and
`LoadingIndicator` were reverted to experimental in material3 1.5.0-alpha19, and this project
is on 1.4.0 stable.

| Token | Damping | Stiffness | For |
| --- | --- | --- | --- |
| `spatialFast` | 0.80 | 1400 | small moves, indicator morphs |
| `spatialDefault` | 0.85 | 700 | the success morph, card transitions |
| `spatialSlow` | 0.90 | 350 | the halo, ambient motion |
| `effectsFast` | 1.0 | 1400 | colour, opacity |
| `effectsDefault` | 1.0 | 700 | colour, opacity |

Spatial springs may overshoot slightly. **Effects springs never do** — an overshooting colour
reads as a rendering bug, not as polish. Settle times land inside One UI's stated 100–500 ms
envelope.

### No fades

`fadeIn` and `fadeOut` are banned. Transitions move, scale, and morph. The one exception even
One UI allows is cross-dissolving a bitmap, and this app never does that.

### The four moments that earn motion

1. **SCANNING**: the signal arcs travel across the gap on a staggered loop. The phone is
   working and must look like it. This one is not decoration — it is the longest state in the
   app and the one a person waits in, and a frozen screen there is indistinguishable from a
   crashed one. An earlier version of this file listed only three moments and omitted it,
   which is exactly why the illustration shipped static from session 1.
2. **The halo** behind Check In pulses slowly — a beacon, echoing the proximity idea.
3. **SCANNING → READY**: the button arrives with confidence, not a jump cut.
4. **READY → SUCCESS**: the payoff. The button's container *becomes* the success container —
   one continuous object, resizing and recolouring, with the check drawing on inside it.
   Never two composables swapped.

Nothing else moves.

**A waiting state always shows motion.** The converse of "no spinner without a sentence" is
just as binding: no sentence without motion, where the user is waiting on something the app
is actively doing. Static text under a static picture is how an app looks when it has hung.

### Reduced motion

Observed continuously, not read once at composition. Reduced motion collapses a transition to
a **cut**, never to a missing state: the success container still appears, filled, check
complete. The information never depends on the animation.

### Press feedback

**Every interactive surface acknowledges the finger before the action completes.** Scale to
`0.97` on press, return on `spatialFast`, applied through one shared
`Modifier.bukPressable()` so it cannot be present on one surface and missing on another —
inconsistency here reads worse than absence.

Disabled surfaces do **not** press. A control that animates and then does nothing is a lie.

Ripple belongs on list rows and not on the ticket, which is an object rather than a menu item.

## Haptics and sound

| Moment | Haptic |
| --- | --- |
| check-in confirmed | `Confirm` |
| onboarding page settles | `SegmentTick` |
| code rejected by the server | `Reject` |
| help sheet opens | `ContextClick` |

Nothing else vibrates. A phone that buzzes on every tap is noisy, not premium.

The confirmation tone plays **only** when `AudioManager.ringerMode == RINGER_MODE_NORMAL`.
A silent phone in a training room gets the haptic and nothing else.

## Loading

**The splash animation is the only indeterminate indicator in the app.**

Everything else is a skeleton shaped like the content it is standing in for, with the row
count taken from the last known count so nothing resizes when data lands. Under one second,
show nothing at all — a skeleton that flashes for 200 ms is worse than no skeleton.

Skeletons share one animation clock. Twenty rows must not mean twenty animations.

## Glass — there is none, and that is settled

**Compose has no backdrop blur.** `Modifier.blur` blurs the element's *own* content, not what
is behind it. Applied to the ticket's state pill it blurred the pill's own label into an
unreadable smear on the A54 — which is API 36, so this is not the below-31 no-op, it is what
the modifier does everywhere.

The two surfaces that were going to be glass — the ticket's state pill and the help sheet's
scrim — are **translucent solids**, which is what the rule already demanded they be designed
as first. The rule was right and it is the only part of this section that survives:

**A surface must look finished without the blur.** Both do, so nothing was lost. If a real
backdrop blur ever arrives in a stable Compose release, these two are where it goes and
nowhere else.

## The ticket card

The signature component. Persistent across every collaborator state, and shared in geometry
with the instance cards in the list.

```
┌────────────────────────────────────────┐
│  MANEJO DE ALIMENTOS                   │   Merriweather — the subject
│  Lunes 13 de julio · 2 h               │   one line, BukOnBlueMuted
│                                        │
│  10:00                          12:00  │   Merriweather display
│  Inicio                            Fin │   BukOnBlueMuted
│◗ · · · · · · · · · · · · · · · · · · ◖│   the tear, with real notches
│  Entrada desde 9:50      ( Inscrito )  │   actionable fact + state pill
└────────────────────────────────────────┘
   ⌃  Necesito ayuda                        48dp, unmistakably a control
```

Rules the card has to keep:

- **Nothing is stated twice.** The stub carries the door-opening time, which appears nowhere
  else. It does not repeat the end time that the duration already implied.
- **The two display figures drop the meridiem** — `6:00` / `7:30`, not `6:00 p. m.`. At 34sp
  the meridiem nearly doubles the rendered width and the two blocks squeezed each other until
  the start time clipped. The stub line right beneath carries a full `Entrada desde 5:50`, and
  `Inicio` / `Fin` label the two figures, so nothing is ambiguous. The instance cards in the
  list use the same short form for the same reason at 320dp.
- **The pill carries state**, not the word "Ticket". `Inscrito` / `Sin inscripción` /
  `Asistencia marcada`.
- **One icon maximum**, and only where it disambiguates. A clock beside the largest number on
  screen disambiguates nothing.
- **The tear is structural.** Real notches in the silhouette, not a dotted line doing the job
  alone.
- **"Necesito ayuda" is a real control** at full 48dp touch height, and it opens the help
  sheet. It was a dead no-op from session 1 until session 5; it must never be one again.

## Screen states

The collaborator screen is a state machine. The ticket and the footer persist; only the centre
changes.

| State | Centre |
| --- | --- |
| `Scanning` | proximity illustration, "Estamos localizando a tu anfitrión…" |
| `EsperandoHora` | illustration + the hour it opens |
| `Offline` | illustration + informational notice, with a retry |
| `Ready` | the Check In button, halo pulsing |
| `Enviando` | the button is gone, so a second tap has nothing to hit |
| `Success` | the morphed success container. Terminal. |
| `Error(*)` | one cause, one sentence, one action |

**Every state must be unmistakably different at a glance.** This is the most important rule in
this file. The loudest complaint about the product this replaces is that buttons never change
state, so nobody knows whether their attendance registered. The transition into success must
be impossible to miss.

## Seamlessness

The goal is that checking in feels like nothing happened — you walked in, you tapped once,
you're done. Seamless is not a vibe; it is these rules, and every one is testable on a device.

**The happy path is one tap.** Anything else the app needs, it does by itself.

1. **Nothing is manually started.** Scanning begins when the screen opens. No "Buscar"
   button, no pull-to-refresh.
2. **The button unlocks itself.** SCANNING → READY needs no interaction.
3. **Success is terminal.** No confirmation dialog, no "OK" to dismiss.
4. **Never a dead disabled control.** Every blocked state names its cause and offers the
   action that unblocks it, in one tap.
5. **Permissions are asked in context**, immediately before the capability is needed.
   Onboarding explains; it does not request.
6. **No spinner without a sentence.** If the user is waiting, the screen says what for.
7. **No dead ends.** Every error state has a next action, and the last resort is always a real
   path: ask your host to register you by hand.
8. **Nothing is asked twice.** Onboarding once, identity once, permissions once.
9. **The app never blames the user.** "No pudimos encontrar a tu anfitrión" — not "You are out
   of range."
10. **Nothing shifts under the user.** A skeleton occupies the same bounds as the content
    replacing it. Layout must not jump when data arrives.
11. **Every control is at least 48dp.** No exceptions for things that look decorative — if it
    is tappable it is a control.
12. **Every control presses.** A surface that accepts a tap acknowledges it before the result
    arrives. A disabled one does not pretend to.
13. **The app can always show its work.** "Did it register?" must be answerable at any time,
    not only in the two seconds after the check mark. That is what the attendance history is
    for, and it is why it is a requirement rather than a feature.
14. **Every state change is announced**, not just drawn. Under TalkBack, SCANNING → READY →
    SUCCESS is spoken via a polite live region. A user who cannot see the check mark has no
    visual workaround for the uncertainty this product exists to remove.

The one thing that may interrupt the flow is a genuine failure the user must resolve. Those
interrupt *early and clearly*, before the person believes they are done — never after.

## Voice

Colombian Spanish, warm, never bureaucratic. The mockups set the register with lines like
"Tranqui, solo debes estar en la sala de tu anfitrión" — informal, calming, solving the
worry before it is voiced. Match that tone in every new string, especially error and
permission messages, where the instinct to sound technical is strongest and most wrong.

**Warmth never buys a promise the app cannot keep.** That specific mockup line was corrected
because it was reassuring and untrue: without internet a person genuinely cannot check in.
The offline card says so plainly and stays warm:

> **Sin conexión**
> Podemos encontrar a tu anfitrión, pero necesitas internet para guardar tu asistencia.

Error copy has one shape: **cause in the title, fix in the body, action on the button.** Never
a raw error code.

All copy lives in one `strings.xml`, in `:core:designsystem` — the only module every UI module
can reach. No hardcoded strings in composables, and no string concatenation in Kotlin; a value
inside a sentence is a format argument.

## Identity and the footer

The wordmark is `docs/assets/buk-in.svg` converted to an `ImageVector` and tinted from the
theme — not a `buildAnnotatedString` imitation with a system italic, which will never match
the real letterforms.

The `in` glyph alone is the launcher icon: adaptive layers at 108×108dp, plus a
`<monochrome>` layer so Android 13+ themes it properly rather than the system doing it badly
on its own.

**The glyph is drawn at 52dp, not at the 66dp safe zone.** This is the distinction that got it
wrong the first time: 66dp is the largest content guaranteed not to be *cut off* by a mask; it
is not the largest content that should be *drawn*. At 66dp the mark filled the whole safe
square, broke the safe circle at its corners, and sat visibly tighter in its mask than every
neighbouring icon on the launcher. 52dp puts it inside the 60dp keyline circle with even
margin.

The splash mark has the mirror-image constraint and the same answer: the splash icon is
**circle-masked**, so the 3.36:1 `buk in.` lockup is fitted to the inscribed circle of the
288dp visible area with 12% margin, not to its bounding square — scaled to the full 288dp
width, both ends would be cut off.

Two hex values live in `app/res/values/colors.xml`, and only there: a launcher icon and a
splash window are inflated by the framework before any Compose theme exists and cannot read
`Color.kt`. The rule the project actually enforces — `Color.kt` is the only *Kotlin* file with
a hex — is intact.

The footer's microcopy does real work — it explains why Bluetooth matters *before* the user
hits a failure that mentions it. It stays on the check-in screen for that reason. On list
screens it is noise, and it goes.

## Icons

Material Symbols, outlined, and as few as possible. The hand-drawn `Canvas` clock and sun are
gone: two glyphs at unrelated stroke weights cost more consistency than they saved in
dependencies.

The proximity illustration and the wordmark are the only custom vector work. There are exactly
two icons — a chevron and a back arrow — declared as `ImageVector`s in `BukIcons.kt`, because
material3 1.4.0 no longer brings `material-icons-core` and adding that artifact for two glyphs
is the dependency this project exists to refuse.

**Vector path data is rounded to two decimals.** `aapt2` truncates any resource string over
32 KB and reports it as a *non-fatal* `STRING_TOO_LARGE` line, so a full-precision path ships
as an empty icon and the build still says BUILD SUCCESSFUL. Two decimals is past visible at
108dp and cuts the longest path from 38 KB to 15 KB. Anyone regenerating these from the SVG
has to keep doing it.

## Responsiveness

Verified at **320 / 360 / 411 dp** width and **100 / 130 / 200 %** font scale.

No fixed dp for anything that holds text or spans the screen. Sizes are
`min(fraction-of-width, cap)`. The old halo was 280dp wide, which on a 320dp phone with 20dp
gutters left exactly zero margin — that is the failure mode to design against.

## Performance

Every animated property is read inside a `graphicsLayer` or `drawWithCache` lambda, never in
composition. The halo, the shimmer, the onboarding parallax and the success morph must each
show **zero recompositions per frame** while running.

Invoke `compose-recomposition-performance` before writing any of them. "It felt smooth" is not
evidence; `dumpsys gfxinfo` is.

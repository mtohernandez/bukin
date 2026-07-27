# Spec 04 — Design Overhaul

Session 4 wrote this spec and changed no production code. Session 5 executes it.

## Objective

Make BukIn look and feel like a product that has been in development for seven years, without
changing what it does. Every behaviour verified in sessions 2 and 3 — BLE detection, the
rotating code, the write to Postgres, the roster, manual registration — must still work
identically when this is done.

This is a design session with a functional floor: **no acceptance criterion from spec 02 or
03 may regress.** Re-run `supabase/tests/rpc_test.sql` and `./gradlew test` at the end; they
must be untouched and green.

## Read first

1. `context/ui-context.md` — rewritten alongside this spec; it is the standing token
   authority and this file does not repeat it
2. `context/hardware-constraints.md` — what a verification claim requires
3. `context/code-standards.md` — the Compose and Kotlin rules still apply
4. `docs/assets/*.png` — the mockups. Intent, not ceiling. Where this spec and a mockup
   disagree, this spec wins and says why.

## Skills

Invoke these rather than working from memory. Named per unit below.

| Unit | Skill |
| --- | --- |
| every unit, before writing | `ponytail` |
| 0, 2, 7, 11 | `compose-state-authoring`, `compose-state-hoisting` |
| 3, 4, 8, 11 | `compose-side-effects` |
| 4, 5, 8, 10, 13 | `compose-recomposition-performance` |
| 4, 5, 12 | `kotlin-flow-state-event-modeling` |
| 3, 6, 7, 11, 12 | `navigation-3` |
| 7 | `edge-to-edge` |
| 1, 10, 14 | `android-cli` |
| 10 | `r8-analyzer` |

---

## Non-negotiable facts

Established by research in session 4. Do not re-derive them; several cost real time to pin
down and two of them overturn an obvious-looking choice.

### Versions

| Thing | Value | Why it matters |
| --- | --- | --- |
| material3 | **1.4.0** (via BOM 2026.06.01) | see M3 Expressive below |
| compose ui / animation / foundation | **1.11.4** | `HapticFeedbackType.Confirm` et al. are stable since 1.8, so they are available |
| minSdk | **26** | gates variable fonts (OK, exactly 26) and blur (not OK, needs 31) |
| `lottie-compose` | **6.7.1** | new dependency, the only one this spec adds |
| `androidx.core:core-splashscreen` | **1.2.0** | second new dependency |

### Material 3 Expressive is NOT adopted

`MaterialShapes` and `LoadingIndicator` were **reverted to experimental** in material3
1.5.0-alpha19 (2026-05-06, b/497876695, b/497877850). `MotionScheme` graduated only on the
1.5 alpha branch. The project is on **1.4.0 stable**.

Therefore: build motion on `androidx.compose.animation.core.spring`, which is stable
foundation API, with our own token object. **Do not bump the BOM to chase expressive APIs.**
It buys nothing this spec needs and destabilises a working build.

### Fonts must be bundled, not downloaded

Variable fonts are **not supported through the Google Fonts downloadable provider**
([issue 223262013](https://issuetracker.google.com/issues/223262013)). Downloadable fonts
also require Play Services and a fallback chain, and the fallback flashes on first launch —
which is precisely the seam this spec exists to remove.

Bundle Inter Variable and Merriweather, **subset to latin + latin-ext**. Variable font
support requires API 26; minSdk is 26, so no guard is needed — but `FontVariation.Settings`
is still `@OptIn(ExperimentalTextApi::class)`.

### Blur is a no-op below API 31

`Modifier.blur` silently does nothing on API < 31, and minSdk is 26. Every glass surface must
therefore be **designed as a translucent solid first**, with blur added as enhancement. If it
only looks finished with the blur, it is wrong.

### The colour numbers

Computed with a WCAG 2.1 relative-luminance calculation over the composited token values, at
both ends of every gradient. `lerp` colour space is irrelevant here — Oklab and sRGB
interpolation of `BukBlueLight` differ by 0.01:1 (4.27 vs 4.26), well inside any decision.

**Three failures exist in the shipped app today:**

| Token | Used for | Measured | Needed | Verdict |
| --- | --- | --- | --- | --- |
| `BukOnBlueMuted` = white @ 0.55 | every ticket label, 12sp | **2.41:1** at the gradient's light end | 4.5:1 | FAIL |
| `BukInkMuted` = ink @ 0.55 | footer, hints, all muted body | **4.40:1** | 4.5:1 | FAIL |
| `BukSuccess` #2BAB51 | success text, roster times, "Registrado" | **2.98:1** on white, **2.43:1** on the field | 4.5:1 text / 3:1 graphic | FAIL |

Worse, `BukBlueLight` is light enough that **pure white on it is only 4.27:1** — the ticket's
own course name fails. No alpha of white fixes this: white @ 0.92 on that blue still measures
4.44:1. The gradient is going the wrong direction.

**The fix is to deepen the ticket, not lighten it.** Inverting the gradient makes `BukBlue`
itself the lightest point of the card, which is the worst case, and everything clears:

| Pair | Value | Worst case | Need | Result |
| --- | --- | --- | --- | --- |
| white / ticket gradient | `#FFFFFF` on `#2F4DAA` | **7.60:1** | 4.5 | PASS |
| `BukOnBlueMuted` = white @ 0.72 | `#C5CDE7` on `#2F4DAA` | **4.80:1** | 4.5 | PASS |
| `BukOnBlueFaint` = white @ 0.55 (tear dots, graphic) | `#A1AFD9` on `#2F4DAA` | **3.49:1** | 3.0 | PASS |
| `BukInkMuted` = ink @ 0.58 | `#616676` on field | **4.66:1** | 4.5 | PASS |
| `BukInkMuted` on white surface | `#6D707A` | **4.96:1** | 4.5 | PASS |
| `BukSuccessInk` text | `#1C7544` on field | **4.65:1** | 4.5 | PASS |
| `BukSuccessInk` text | `#1C7544` on white | **5.70:1** | 4.5 | PASS |
| white on `BukSuccessInk` fill | | **5.70:1** | 4.5 | PASS |
| `BukBlue` text on field | | **6.20:1** | 4.5 | PASS |
| `BukInk` on field | | **16.27:1** | 4.5 | PASS |
| `BukInkSoft` illustration on field | | **4.43:1** | 3.0 | PASS |

### The final derived tokens

Still **four brand hex values and no more**. `Color.kt` remains the only file in the project
containing a hex literal.

```kotlin
val BukField      = BukBlue.copy(alpha = 0.10f).compositeOver(BukBackground)  // #E3E8F6
val BukBlueDeep   = lerp(BukBlue, BukInk, 0.35f)                              // #1D3373
val BukBlueGradient = listOf(BukBlue, BukBlueDeep)   // was BukBlue -> lighter. Inverted.
val BukSuccessInk = lerp(BukSuccess, BukInk, 0.30f)                           // #1C7544
val BukInkMuted   = BukInk.copy(alpha = 0.58f)       // was 0.55f
val BukOnBlueMuted= Color.White.copy(alpha = 0.72f)  // was 0.55f
val BukOnBlueFaint= Color.White.copy(alpha = 0.55f)  // was 0.35f
```

`BukSuccess` stays in `Color.kt` — it is one of the four brand values and rule 3 makes it
authoritative. But it is **demoted to a decorative accent** (the success halo/glow) and never
again carries text or an icon. `BukSuccessInk` carries all success meaning.

---

## File manifest

New files are marked **new**. Everything else is a rewrite of a file that exists.

```
core/designsystem/src/main/kotlin/.../designsystem/
  theme/Color.kt                    tokens above; still the only hex file
  theme/Spacing.kt                  full scale + responsive gutter
  theme/Shape.kt                    six-step scale
  theme/Type.kt                     Inter + Merriweather; every used style defined
  theme/Motion.kt              new  BukMotion spring tokens
  theme/Elevation.kt           new  stroke, elevation, opacity tokens
  theme/Theme.kt                    BukField -> background; wire the above
  component/TicketCard.kt           rebuilt; notched silhouette, state pill
  component/TicketShape.kt     new  the notched Shape, shared with instance cards
  component/InstanceCard.kt    new  the list variant of the ticket
  component/CheckInButton.kt        morph source; responsive sizing
  component/SuccessCheck.kt         morph target; filled container
  component/CheckInMorph.kt    new  the button -> check transition
  component/BukSkeleton.kt     new  one-pass shimmer
  component/NoticeCard.kt           severity axis
  component/BukScreen.kt       new  the one scaffold
  component/BukTopBar.kt       new  leading back, predictive-back aware
  component/Avatar.kt          new  photo or initials monogram
  component/BukInFooter.kt          real wordmark ImageVector
  component/Wordmark.kt        new  buk-in.svg paths as ImageVector
  component/ProximityIllustration.kt   animated signal arcs (unit 4); responsive sizing
  component/ReducedMotion.kt        observes changes instead of caching
  component/BukHaptics.kt      new  the haptic map
  component/BukSound.kt        new  ringer-gated confirmation tone
  component/Pressable.kt       new  Modifier.bukPressable() — the 0.97 press scale
  res/values/strings.xml            new strings; error copy pass
  res/font/                    new  inter_variable.ttf, merriweather_*.ttf (subset)
  res/raw/                     new  confirm.ogg (~15 KB)

features/checkin/.../
  CheckInScreen.kt                  BukScreen; morph; skeleton ticket; help sheet
  NecesitoAyudaSheet.kt        new  contextual help
features/onboarding/.../
  OnboardingScreen.kt               Lottie, parallax, haptics
  res/raw/                     new  three recoloured Lottie JSONs
features/host/.../
  HostScreen.kt                     BukScreen; no string concatenation
  HostRosterScreen.kt               designed list, skeletons, avatars
  ManualRegistrationScreen.kt       designed list, skeletons
app/src/main/
  kotlin/.../MainActivity.kt        installSplashScreen()
  kotlin/.../ui/SessionPickerScreen.kt   InstanceCard, skeletons, greeting, Próximas/Historial
  kotlin/.../ui/MiAsistenciaScreen.kt new  the receipt. No backend change.
  kotlin/.../ui/RolePickerScreen.kt      BukScreen (kept — see the note at the end)
  kotlin/.../ui/NameEntryScreen.kt       form extracted; hosts onboarding step 4 and "No soy X"
  kotlin/.../ui/IdentityPreferences.kt   + avatarPath
  kotlin/.../ui/AvatarPicker.kt     new  PickVisualMedia -> filesDir
  kotlin/.../navigation/BukInNavDisplay.kt   shared-axis transitions
  res/drawable/ic_launcher_*.xml    the `in` glyph, replacing the stock robot
  res/drawable/avd_splash.xml  new  the splash animation
  res/values/themes.xml             splash theme
gradle/libs.versions.toml           lottie 6.7.1, core-splashscreen 1.2.0
```

---

## What to build

Fifteen units, in order. Each ends with `./gradlew assembleDebug` passing. Units 0 and 1 are
prerequisites for everything after them — do not start unit 2 with the token scales unsettled.

Units 0–10 make the app look like a finished product. **Units 11–14 are what make it behave
like one**, and they are not optional polish: 12 answers a complaint users actually filed, 14
answers the same complaint for people using a screen reader. A beautiful app that still asks
its daily user which kind of user they are, and still cannot show them last week's
attendance, is a demo with good typography.

### Unit 0 — Foundations

`:core:designsystem` only. No feature module changes.

**Spacing.** The current scale (`4/8/16/24/32/48`) has no 12 and no 20, which is why
`TicketCard.kt:234` computes `BukSpacing.xs + 2.dp` and `TicketCard.kt:195` hardcodes
`12.dp`/`6.dp`. Replace with `2/4/6/8/12/16/20/24/32/40/48/64`.

The gutter becomes responsive. One UI asks for **≥24dp side margins**; the mockups use 20.
Resolve as: `20.dp` below 360dp screen width, `24.dp` at or above it.

**Shape.** `4/8/12/16/20/28` plus `Full`. Map every existing literal onto a step — the 14dp
in `HelpStrip` becomes 12 or 16, the pill becomes `Full`. After this unit, `RoundedCornerShape`
must not appear outside `Shape.kt` and `TicketShape.kt`.

**Elevation / stroke / opacity.** A token object each, so `BorderStroke(1.dp, …)` and bare
`0.55f` alphas stop being invented per call site.

**Colour.** Exactly the tokens in the table above. The gradient inverts.

**Type.** Inter for body and UI, Merriweather for display only — ticket times, the success
line, onboarding headlines. Nothing else gets a serif; a serif on a button label is the
single fastest way to make this look like a template.

Define **every style the app actually uses.** Measured across the repo — 4 of the 13
referenced styles are declared nowhere in `BukTypography` and silently fall back to Material
defaults in Roboto:

| Style | Call sites | Declared? |
| --- | --- | --- |
| `bodySmall` | **12** — the most-used style in the app | **no** |
| `titleSmall` | 4 | **no** |
| `labelLarge` | 4 | **no** |
| `headlineSmall` | 3 | **no** |

That is 23 call sites, concentrated in `HostScreen`, `SessionPickerScreen`,
`HostRosterScreen` and `ManualRegistrationScreen` — which is exactly the set of screens the
tracker describes as "plain Material". They are plain Material because half their type
literally is. Fold the loose `TicketStubTime` into the scale. Set line height and letter
spacing per style; do not inherit.

**Motion.** `BukMotion`, built on stable `spring()`:

```kotlin
object BukMotion {
    // Spatial: anything that moves, resizes, or changes corner radius. Slight overshoot.
    val spatialFast    = spring<Float>(dampingRatio = 0.80f, stiffness = 1400f)
    val spatialDefault = spring<Float>(dampingRatio = 0.85f, stiffness =  700f)
    val spatialSlow    = spring<Float>(dampingRatio = 0.90f, stiffness =  350f)
    // Effects: colour and opacity. Never overshoot — an overshooting colour reads as a bug.
    val effectsFast    = spring<Float>(dampingRatio = 1f, stiffness = 1400f)
    val effectsDefault = spring<Float>(dampingRatio = 1f, stiffness =  700f)
}
```

Tuned so settle time lands inside One UI's stated 100–500 ms envelope. Verify on device with
`dumpsys gfxinfo`, not by eye.

**`fadeIn`/`fadeOut` are banned from this codebase.** The one exception One UI itself allows
is cross-dissolving a bitmap, which this app never does. Transitions move, scale, and morph.

**Haptics.** `BukHaptics` over `LocalHapticFeedback`:

| Moment | Type |
| --- | --- |
| check-in confirmed | `Confirm` |
| onboarding page settles | `SegmentTick` |
| code rejected by the server | `Reject` |
| help sheet opens | `ContextClick` |

Nothing else vibrates. A phone that buzzes on every tap is not premium, it is noisy.

*Acceptance:* `grep -rn "\.dp\b" --include=*.kt` outside `theme/` returns only sizes that are
genuinely component-intrinsic and named as constants. `Color.kt` is still the only file with
a hex. Every `MaterialTheme.typography.*` reference in the project resolves to a style
declared in `Type.kt`.

*Skills: `ponytail`, `compose-state-authoring`.*

---

### Unit 1 — App identity: icon, splash, wordmark

`docs/assets/buk-in.svg` is 435×160, 8 paths, single fill `#2F4DAA`: `buk` in a geometric
sans, `in` in an italic script, then a period.

**Launcher icon.** The `in` glyph alone, as you specified. Adaptive layers at **108×108dp**;
the glyph must sit inside the **66×66dp** safe viewport, because the outer 18dp per side is
reserved for masking and parallax. Ship a **`<monochrome>`** layer: Android 13+ themes icons
that have one, and from Android 16 QPR2 the system themes icons that lack one automatically
and badly. The current `ic_launcher_foreground.xml` is still the Android Studio stock robot —
it goes.

**Splash.** `androidx.core:core-splashscreen:1.2.0`, `installSplashScreen()` before
`super.onCreate`.

- `windowSplashScreenBackground` = `BukField`. It **must be fully opaque**; the API rejects
  transparency.
- Animated icon is an `AnimatedVectorDrawable` on the **432dp canvas with a 288dp inner
  visible area** (4× the adaptive icon geometry), duration **≤1000 ms**, declared in
  `windowSplashScreenAnimationDuration`.
- The animation: `buk` strokes on, then `in` writes itself. This is your "buk as the initial
  loader" — and it is **the only indeterminate loading indicator the app is allowed to have.**
- Do **not** hold the splash with an `OnPreDrawListener`. `MainActivity` reads two
  `SharedPreferences` values synchronously and has nothing to wait for; holding the splash to
  look busy is the opposite of the goal.

**In-app wordmark.** Convert the SVG paths to an `ImageVector` in `Wordmark.kt`, tinted from
the theme. This replaces the `buildAnnotatedString` imitation in `BukInFooter.kt:55-71`,
which fakes the logo with a system italic and will never match it.

*Acceptance:* icon rendered on the phone's launcher in circle, squircle and teardrop masks,
plus themed-icon mode. Splash captured on device. `assembleDebug` size delta recorded.

*Skill: `android-cli`.*

---

### Unit 2 — The ticket card, rebuilt

The current card reads as a form: duration is given the same 26sp weight as start time, and
the stub row repeats information already stated above it (`12:00 PM` **is** `fechaFin`, which
the duration already implied).

```
┌────────────────────────────────────────┐
│  MANEJO DE ALIMENTOS                   │   Merriweather — the subject
│  Lunes 13 de julio · 2 h               │   one line, BukOnBlueMuted
│                                        │
│  10:00                          12:00  │   Merriweather display
│  Inicio                            Fin │   BukOnBlueMuted, 12sp
│◗ · · · · · · · · · · · · · · · · · · ◖│   the tear, with real notches
│  Entrada desde 9:50      ( Inscrito )  │   actionable fact + state pill
└────────────────────────────────────────┘
   ⌃  Necesito ayuda                        48dp target, unmistakably a control
```

- Deep gradient (`BukBlue → BukBlueDeep`), so every white level clears contrast.
- Duration demoted into the subtitle. The stub stops repeating `fechaFin` and instead carries
  the only genuinely new fact: **when the door opens.**
- `TicketShape` punches real semicircular notches at the tear line, so the silhouette says
  "ticket" and the dotted line is no longer carrying that meaning alone.
- The pill carries **state**, not the decorative word `Ticket`: `Inscrito` /
  `Sin inscripción` / `Asistencia marcada`. Same geometry, real information.
- **One icon maximum.** The hand-drawn `SunGlyph` goes; the `ClockGlyph` goes too — the time
  is already the largest thing on the card and a clock beside it is redundant.
- `HelpStrip` becomes a full **48dp** control with a clear affordance, wired to unit 3.

*Acceptance:* rendered at 320/360/411dp width and at 100/130/200% font scale without clipping
or overlap. Contrast spot-checked on a device screenshot against the table above.

---

### Unit 3 — `NecesitoAyudaSheet`

**"Necesito ayuda" does nothing today.** `TicketCard` declares
`onHelpClick: () -> Unit = {}` and `CheckInScreen.kt:175` calls `TicketCard(instancia = it)`,
taking the default. It has been a dead control since session 1.

A `ModalBottomSheet`, **contextual on `CheckInState`** — what it offers while scanning is not
what it offers after a rejected code:

| State | "Qué está pasando" | "Qué puedes hacer" |
| --- | --- | --- |
| `Scanning` | buscando la señal del anfitrión | acércate a la sala · confirma que el anfitrión abrió · Bluetooth encendido |
| `EsperandoHora` | la sesión aún no abre | vuelve a las HH:MM · deja el Bluetooth encendido |
| `Offline` | detectamos al anfitrión, falta guardar | revisa datos o Wi-Fi · reintentar |
| `Error(*)` | the specific cause | the specific recovery, already in `strings.xml` |
| `Success` | quedó registrada | nothing to fix; show the time it was recorded |

Every branch ends at the same last resort: **"pídele a tu anfitrión que te registre a mano"** —
which is a real path that exists in the app (`ManualRegistrationScreen`), not a platitude.
This is what closes the "app never leaves the user alone" requirement: every dead end in the
audit now terminates somewhere real.

Copy rule for the whole sheet: **name the cause, then the fix, then the action.** Never a raw
error code, never blame.

*Skills: `compose-side-effects` (sheet state, back handling), `navigation-3`.*

---

### Unit 4 — Motion, and the success morph

`CheckInScreen.kt:177-187` currently cross-fades every state including the payoff, and the
success moment is a jump cut: the button vanishes, then a check scales in from nothing.

**The morph.** The Check In button's container *becomes* the success container:

1. On tap: `Confirm` haptic fires **at the start of the morph**, not on the raw tap and not at
   the end. The button label leaves upward and out; the container starts resizing.
2. Container animates on `spatialDefault` — width and height toward the success shape, corner
   radius toward `Full`. Fill animates `BukBlue → BukSuccessInk` on `effectsDefault`.
   Never overshoot a colour.
3. The check stroke draws on inside the filled container. White on `BukSuccessInk` is
   **5.70:1** — the check is legible, which the current bare `BukSuccess` stroke on the field
   (**2.43:1**) is not.
4. The halo rings, which were pulsing as a beacon, expand once and dissipate.
5. Confirmation tone plays **only** when `AudioManager.ringerMode == RINGER_MODE_NORMAL`. A
   silent phone in a training room gets the haptic and nothing else.

Implement with `LookaheadScope` + `Modifier.animateBounds`, or a single `Animatable` driving a
`graphicsLayer` — whichever `ponytail` lands on. It must be **one continuous object**, never
two composables swapped.

**The scanning state must visibly be working.** `ProximityIllustration` contains no animation
API at all — during SCANNING the entire screen is frozen: a static illustration above a static
sentence. This is the state a person spends the most time in while uncertain, and a frozen
screen is indistinguishable from a crashed app. It is also the exact shape of the top
complaint about the product this replaces — *"the buttons do not change state so you never
know if your attendance was actually registered"*. A sentence with no motion behind it is the
converse of the "no spinner without a sentence" rule, and just as bad.

The signal arcs animate: each arc scales outward from the emitting figure and fades as it
crosses the gap, on a staggered repeating loop, so the illustration reads as a signal in
flight rather than as a picture of one.

- **One clock.** A single `rememberInfiniteTransition`; the three arcs derive their phase from
  it by offset. Not three transitions.
- **Zero recomposition.** The phase is read inside the `Canvas` draw lambda via
  `drawWithCache`/`graphicsLayer`, never in composition. Verify in Layout Inspector.
- **Not a spinner.** It depicts the actual activity, which is why it is exempt from the
  one-indeterminate-indicator rule in unit 5. It is a state illustration, not a progress
  indicator, and it never implies a percentage or an ETA.
- **Reduced motion** collapses it to the static arcs that exist today — the fallback is
  already drawn, so this costs nothing.

`ponytail:` deliberately presentation-only. The arcs loop on a timer and do not know whether
anything has actually been heard, so the illustration is honest about *scanning* but cannot
distinguish "listening" from "heard something stale". Making it state-aware means
`CheckInState.Scanning` carrying a sighting flag, which touches `:domain` and `:core:ble` —
out of scope for a design session. Upgrade path if it is ever wanted: add the flag, and let a
detected-but-stale sighting brighten the arriving arc.

**Navigation.** Shared-axis transitions in `BukInNavDisplay`, no fade, with
`predictivePopTransitionSpec` set so the back gesture drives the same motion under the user's
finger. All Navigation 3 versions support predictive back.

**Reduced motion.** `ReducedMotion.kt` currently reads the animator scale once inside
`remember(resolver)` and never sees a change — flip the setting and the app never notices.
Rewrite to observe (`ContentObserver` → `callbackFlow` → `collectAsStateWithLifecycle`).
Reduced motion collapses the morph to a **cut**, never to a missing state: the success
container still appears, filled, with the check complete.

*Skills: `compose-side-effects`, `compose-recomposition-performance`,
`kotlin-flow-state-event-modeling`.*

---

### Unit 5 — Loading: skeletons, and the one-loader rule

Per NN/g: under 1 s show nothing; 1–10 s on a full-page load show a **skeleton**; spinners
only where there is no shape to predict. Apple's guidance agrees — the best loading experience
finishes before the person notices it.

Today: `SessionPickerScreen.kt:207-238` tracks `uiState.cargando` and **renders nothing for
it** — the `when` falls through to a `LazyColumn` over an empty list, so the screen is blank
and then pops. `CheckInScreen.kt:175` renders no ticket at all while `instancia == null`, so
the header lands late and shoves the layout down. `NameEntryScreen.kt:158` admits in a comment
that its entire in-flight affordance is a disabled button.

- `BukSkeleton`: a token-driven shimmer drawn in **one** `Modifier.drawWithCache` pass with a
  single shared animation clock. Not an `InfiniteTransition` per item — 20 rows must not mean
  20 animations.
- Session list, roster and manual registration render skeleton rows **shaped like their real
  rows**, and the count is the **last known count** so the list does not resize when data
  lands.
- Check-in renders a **skeleton ticket** while `instancia == null`. The layout never shifts.
- Name entry gets a real in-flight state on the button.
- After this unit the app contains **exactly one indeterminate indicator: the splash AVD.**
  The animated scanning arcs (unit 4) are not one — they depict the activity itself rather
  than standing in for unknown progress, and they never imply a percentage or an ETA. The
  distinction is the test: a spinner would look identical whatever the app were doing; the
  arcs would be wrong for any state but SCANNING.

*Skills: `compose-recomposition-performance`, `kotlin-flow-state-event-modeling`.*

---

### Unit 6 — Instance cards: the list as tickets

`SessionRow` is a plain Material `Card` whose entire affordance is a blue word, with disabled
states rendered as muted text.

`InstanceCard` shares `TicketShape` with the ticket card — same notch, same tear, same radius —
so tapping a row into the check-in screen is a **shared-element continuation**, not a screen
swap. The object the person tapped is the object they arrive at.

State drives the surface, not just a label colour:

| State | Surface |
| --- | --- |
| active now | full brand gradient, the only card with it |
| enrolled, waiting | white surface, brand-tinted edge, opening time stated |
| not enrolled | white surface, neutral edge, `Inscribirme` |
| already marked | white surface, `BukSuccessInk` edge and pill |
| finished | desaturated, no action, does not pretend to be tappable |

The ordering logic in `SessionPickerScreen.kt:272-279` is correct and stays — `activa` before
`inscrito`, so a walk-in can mark attendance without enrolling first. That was a session-3
bug fix; do not undo it while restyling.

---

### Unit 7 — Scaffold, greeting, profile

Six screens each re-implement `Column + safeDrawingPadding + padding(gutter) + … +
BukInFooter + Spacer`.

- **`BukScreen`**: insets, gutter, optional top bar, optional footer. Deletes all six copies.
- **`BukTopBar`**: a **leading** back affordance. The bottom-of-screen `TextButton` labelled
  *Volver* in `CheckInScreen`, `HostScreen`, `HostRosterScreen` and `ManualRegistrationScreen`
  is not an Android pattern and goes. Predictive back is handled once, here.
- **Greeting**: the session list header greets by name, with the avatar as the affordance to
  change it.
- **Avatar**: `ActivityResultContracts.PickVisualMedia` — no permission required, which
  matters in an app whose whole pitch is that it asks for nothing. Copy the result into
  `filesDir`, store the path beside the name in `IdentityPreferences`. Decode with
  `BitmapFactory` + `inSampleSize` at a capped size; **do not add Coil for one image.**
  Initials monogram is the always-available fallback and is what the roster shows.
- **Footer**: the proximity microcopy earns its place on the check-in screen, where it warns
  before a failure. On list screens it is noise — drop it there.

*Skills: `edge-to-edge`, `navigation-3`, `compose-state-hoisting`.*

---

### Unit 8 — Onboarding

Copy stays **verbatim**. It was argued for in session 1, screen 3 is deliberately honest about
what v1 does, and it explains permissions without requesting them. Do not rewrite it.

**This unit styles three pages; unit 11 adds the fourth.** Build the motion and the Lottie
treatment here for pages 1–3, then unit 11 appends the name step, which is a form and takes
none of the below. If unit 11 is done first, the Lottie work still applies only to 1–3.

- One brand-recoloured Lottie per page, bundled in `res/raw` so it works offline.
  **Every colour is overridden at runtime** via `LottieDynamicProperties` on `KeyPath("**")`,
  driven from `BukBlue` / `BukInkSoft` / `BukSuccessInk`. Nothing reads a colour out of the
  JSON — that is what keeps `Color.kt` the only source of colour and keeps the animations from
  looking like stock assets.
- Parallax tied to `pagerState.currentPageOffsetFraction`, read **inside a `graphicsLayer`
  lambda**. This is a frame-rate value; reading it in composition recomposes the page on every
  pixel of drag.
- `SegmentTick` haptic on page settle.
- The indicator morphs on `spatialDefault` instead of `animateDpAsState` with a default tween.

*Skill: `compose-recomposition-performance` — this is the screen most likely to hide a
frame-rate state read in composition.*

---

### Unit 9 — Error copy and the glass inventory

**Copy pass.** Every `error_*` string re-read against one rule: **cause in the title, fix in
the body, action on the button.** Most already comply — session 1 and 3 did this work
carefully, and `error_no_bluetooth_*` is a genuinely good example of naming a thing the user
cannot fix without blaming them. Fix what does not comply, and add the strings units 2, 3, 6
and 7 need.

Delete `HostScreen.kt:233`, which concatenates in Kotlin:

```kotlin
stringResource(R.string.host_instancia_label) + ": ${session.instanciaId}"
```

That is a format-argument string, and `code-standards.md` already forbids it.

`NoticeCard` gains a **severity** axis — informational versus blocking — so "Sin conexión",
which is a temporary condition that clears itself, stops looking like the same object as a
hard failure.

**Glass.** Gated on `Build.VERSION.SDK_INT >= 31`, in exactly two places:

1. the help sheet's scrim over the ticket
2. the ticket's state pill sitting over the gradient

Everywhere else it costs a render pass to say nothing. Below API 31 both degrade to a solid
translucent tint that **must already look finished** — verify by testing with blur forced off.

---

### Unit 10 — Performance and weight

**Frame rate.** Every animated property read inside a `graphicsLayer` / `drawWithCache` lambda,
never in composition. The halo pulse, the skeleton shimmer, the onboarding parallax and the
success morph must each show **0 recompositions per frame** while running.

**Responsiveness.** Fixed sizes go. `CheckInButton`'s halo is **280dp wide**; on a 320dp
device with the current 20dp gutters that leaves exactly zero margin. `ProximityIllustration`
(210×165), the button (176×72) and `SuccessCheck` (150) are all fixed and scale with neither
screen width nor font scale. Replace with `min(fraction-of-width, cap)`.

**Weight.** ~12 MB today, budget **≤14.5 MB**:

| Addition | Estimate |
| --- | --- |
| lottie-compose 6.7.1 | ~1.3 MB |
| 3 Lottie JSON | ~120 KB |
| Inter Variable + Merriweather, subset latin+latin-ext | ~370 KB |
| core-splashscreen | ~30 KB |
| confirm tone | ~15 KB |

Run `r8-analyzer`. Lottie will need a keep rule; add the narrowest one that works, not the
package-wide one its docs suggest.

*Skills: `compose-recomposition-performance`, `r8-analyzer`, `android-cli`.*

---

### Unit 11 — First run as one flow

Today first run is three separate surfaces with a hard cut between each: `Onboarding` (3
pages) → `NameEntry` → `RolePicker`. A person sets the app up by being handed off between
screens.

Onboarding becomes **four steps**. The first three keep their existing copy verbatim. Step 4
is the name question, inline:

```
1  Tu asistencia, en un toque
2  Tu teléfono encuentra a tu anfitrión
3  Por ahora, registramos tu entrada
4  ¿Cómo te llamas?          [____________]
                             [  Empezar   ]
```

- The name **form** becomes a shared composable. `NameEntryScreen` does **not** disappear as a
  destination — `SessionPickerScreen` offers "No soy %1$s" (`sesiones_cambiar_nombre`) and
  navigates to it, so the form is used in two places: as onboarding step 4, and as the
  standalone change-name screen. One composable, two hosts.
- Step 4 has **no Skip**. Skip stays on steps 1–3. The app cannot function without a name, and
  a skippable step that then blocks you is worse than no skip at all.
- The progress indicator now measures something real: four steps, the last of which is the
  only one that asks for anything.
- `onboarding_page_indicator` ("Paso %1$d de %2$d") already takes a count — no string change.
- Completing step 4 calls `identificar_colaborador` exactly as `NameEntryViewModel` does now.
  **Do not duplicate that logic**; reuse the view model.

*Acceptance:* fresh install reaches the session list without a screen cut that is not a pager
page turn. "No soy X" still reaches the same form.

---

### Unit 12 — "Mi asistencia": the history panel

This is the loudest unaddressed complaint in `docs/feedback.md`:

> *"There is no internal panel or profile menu where a worker can verify their history of past
> punch-ins or check if a punch synchronized correctly."*

**It needs no backend work.** `listar_instancias` has no `WHERE` clause — it already returns
every instance, past and future, `order by i.fecha_inicio desc`, each carrying this
collaborator's `inscrito` and `asistencia` flags. The data is on the device and the app
currently flattens it into one undifferentiated list.

Build:

- The session list splits into **Próximas** and **Historial**. Past instances stop competing
  with actionable ones, which also fixes a present-day oddity: a finished session renders as a
  card that looks tappable and is not.
- A `Mi asistencia` destination reached from the avatar menu: every past instance with
  `asistencia = true`, showing course, date, and the fact that it is recorded. This is the
  **receipt** — the answer to "did it register?" available at any time, not just in the two
  seconds after the check mark.
- Sessions the person attended but never enrolled in read as walk-ins, which the data already
  distinguishes.
- Empty state is a real design, not a bare sentence: nothing recorded yet, and what to do
  about it.

**No migration, no RPC change, no new network call.** If this unit finds itself editing
`supabase/`, something has been misread — stop and re-read `listar_instancias`.

*Acceptance:* a collaborator who checked in during a previous session can find that fact from
a cold start in two taps, offline-tolerant behaviour aside.

---

### Unit 13 — Press states and micro-interaction

There is **no press feedback anywhere in the app.** Every `Card`, `TextButton` and clickable
row relies on the default Material ripple, and the ticket's help strip uses a bare
`Modifier.clickable`. Nothing acknowledges a finger before the action completes.

- Every interactive surface scales to **0.97** on press and returns on `spatialFast`. This is
  the One UI touch idiom, it costs one `graphicsLayer`, and it is most of what makes a button
  feel worth pressing.
- Press scale is applied via a shared `Modifier.bukPressable()` so it cannot be forgotten on
  one surface and applied on another — inconsistency here reads worse than absence.
- Ripple stays where it belongs (list rows) and goes where it does not (the ticket card,
  which is an object, not a menu item).
- Interactive elements never fall below **48dp** in either dimension.
- Disabled controls do not press. A surface that animates and then does nothing is a lie, and
  `SessionPickerScreen` currently has several rows whose whole affordance is a coloured word.

*Skill: `compose-recomposition-performance` — press scale reads inside `graphicsLayer`, never
in composition.*

---

### Unit 14 — Accessibility beyond contrast

Contrast is fixed in unit 0. The rest is unaddressed, and one part of it is the accessibility
form of this product's founding complaint.

- **State changes announce.** The check-in centre gets
  `Modifier.semantics { liveRegion = LiveRegionMode.Polite }` so SCANNING → READY → SUCCESS is
  spoken. A blind user currently gets no notification that their attendance registered — which
  is precisely *"you never know if your attendance was actually registered"*, with no visual
  workaround available.
- **The success morph carries a `contentDescription`** that states the outcome, not the shape.
- **Focus order** is verified on every screen: the ticket, then the action, then help. The
  footer is decorative and gets `clearAndSetSemantics {}`.
- **Decorative canvases are hidden** from the accessibility tree. The tear dots and the halo
  are not information.
- **Touch targets ≥48dp**, enforced with `Modifier.minimumInteractiveComponentSize()`.
- **Font scale to 200%** is a layout requirement, already in unit 10, and is where most of
  these screens will actually break.

*Acceptance:* TalkBack walked through the full collaborator path on the phone; every state
change announced; no unlabelled control; no decorative element focusable.

---

### Not built, and deliberately so: the role picker

`RolePickerScreen` gates every launch — `MainActivity` sends every returning user there, so a
collaborator answers "¿Cómo entras hoy?" every day, forever, about a distinction that exists
for the app's benefit rather than theirs. Almost none of the 300 collaborators will ever host.

**Decision: it stays for the demo.** Role switching happens constantly while demonstrating,
and a two-tap path through a profile menu costs more on demo day than the daily tax costs a
user who does not exist yet. Recorded so it is a known cut rather than something a later
session rediscovers as a defect.

Upgrade path when this stops being a demo: remember the role after the first answer, open
straight into the session list, and move host mode behind the avatar menu built in unit 7.
That is a change to `MainActivity.startKey` and one menu entry — the navigation graph already
supports it, since `SessionPicker` takes `isHost` as a parameter.

---

## Acceptance criteria

Each is falsifiable on the Samsung Galaxy A54 over wireless adb, or by a `grep` over the repo.

| # | Criterion | How it is checked |
| --- | --- | --- |
| 1 | No functional regression | `./gradlew test` green; `rpc_test.sql` returns `19 / 0 / TODO VERDE`; one live BLE check-in writes a row |
| 2 | `Color.kt` is still the only hex | `grep -rn "0xFF\|#[0-9a-fA-F]\{6\}" --include=*.kt` matches only `Color.kt` |
| 3 | Every used type style is declared | no `MaterialTheme.typography.*` reference resolves to a Material default |
| 4 | Contrast holds on device | screenshot sampled at 6 points against the table above; all pass |
| 5 | No fades | `grep -rn "fadeIn\|fadeOut" --include=*.kt` returns nothing |
| 6 | "Necesito ayuda" opens the sheet | tapped on device in each of 5 states, showing different content |
| 7 | Success morphs | screen recording shows one continuous container, not a swap; haptic felt; tone silent when the phone is on vibrate |
| 8 | One indeterminate indicator | the splash AVD, and nothing else. The scanning arcs are a state illustration, not an indicator |
| 8b | Scanning visibly works | screen recording of SCANNING for 15 s shows continuous arc motion; Layout Inspector shows **0 recompositions** while it runs; with animations disabled the static arcs still render |
| 9 | No layout shift on load | check-in and session list recorded from cold; skeleton and real content occupy identical bounds |
| 10 | Responsive | renders at 320/360/411dp × 100/130/200% font scale with no clipping |
| 11 | 60 fps | `dumpsys gfxinfo` over the morph and a pager drag: no janky frames above the device's refresh budget |
| 12 | Icon and splash | launcher icon in 3 masks + themed mode; splash captured |
| 13 | Weight | `assembleRelease` APK ≤14.5 MB |
| 14 | Blur degrades | forced off, both glass surfaces still look finished |
| 15 | First run is one flow | fresh install → session list with no screen cut that is not a page turn; "No soy X" still reaches the name form |
| 16 | History is reachable | a collaborator who attended a past session finds that fact from a cold start in ≤2 taps |
| 17 | No backend was touched | `git diff --stat supabase/` is empty for the whole session |
| 18 | Everything presses | every interactive surface scales on press; no disabled surface does; nothing interactive is under 48dp |
| 19 | TalkBack | full collaborator path walked on the phone; SCANNING → READY → SUCCESS each announced; no unlabelled control; no decorative element focusable |

**Report anything not run as not run.** An honestly unmet criterion is a fine outcome; a
quietly downgraded one is not.

## Verification

```bash
./gradlew assembleDebug              # every unit
./gradlew test                       # unaffected, must stay green
psql "$BUKIN_PG" -f supabase/tests/rpc_test.sql    # 19 / 0 / TODO VERDE
export ANDROID_SERIAL=adb-RZCWA02ZSHV-0v4jBa._adb-tls-connect._tcp
./gradlew installDebug
adb exec-out screencap -p > state.png
adb shell dumpsys gfxinfo com.buk.bukin framestats
```

Font scale and width sweeps:

```bash
adb shell settings put system font_scale 1.3      # and 2.0, then back to 1.0
adb shell wm size 320x640                          # and 360x800, 411x914, then reset
adb shell wm size reset
```

See `context/runbook.md` for the Mac beacon and the live code cross-check, both of which must
still pass after this session.

## Out of scope — do not build

| Thing | Why |
| --- | --- |
| Dark mode | The app is light-only by decision. A dark ticket is a second design to keep in contrast, for a demo shot in one room. |
| Backend changes of any kind | This is a design session. No migration, no RPC, no bucket. |
| Avatar upload to Supabase Storage | Explicitly decided: on-device only. |
| Auth, check-out, offline write queue | Still out of scope, still in `architecture.md`'s "Not built". |
| Bumping the Compose BOM | See M3 Expressive above. |
| Coil or any image library | One avatar, decoded with `BitmapFactory`. |
| Redesigning `BleDiagnosticsScreen` | A service door. It gets `BukScreen` and nothing more. |
| Removing the role picker | Decided to keep for the demo. See the note above unit 11's acceptance criteria — it is a known cut with a one-line upgrade path, not an oversight. |
| Any new RPC or migration for the history panel | `listar_instancias` already returns every past instance with the `asistencia` flag. Unit 12 is presentation over data the app is already fetching. |

## On completion

1. Update `context/progress-tracker.md` — what shipped, what did not, every deviation and why.
2. Update `context/ui-context.md` if any token changed during execution. It is the standing
   authority; a token that drifts from it is a bug in one of the two.
3. Record the measured APK size and the `gfxinfo` numbers. They are the evidence for criteria
   11 and 13, and "it felt smooth" is not.

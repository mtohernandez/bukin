# Spec 01 — Foundation, Design System, Onboarding

**Session 1 of 3.** No Bluetooth, no network. This session makes the project exist, look
right, and run.

## Objective

A debug APK that installs on a phone, shows a 3-screen onboarding on first launch, lets
the user pick a role, and renders the collaborator screen with the ticket card and all
four visual states switchable by hand. Everything below the UI is stubbed.

By the end, the design is *done* — sessions 2 and 3 wire real behavior into finished
screens rather than designing while debugging Bluetooth.

## Read first

`CLAUDE.md` → `context/project-overview.md` → `context/ui-context.md` →
`context/architecture.md` → `context/code-standards.md`. The mockups in `docs/assets/`
are the visual target: `check-in-screen.png`, `wait-check-in-screen.png`,
`wait-check-in-screen-offline.png`, `success-check-in-screen.png`.

## Skills

Invoke `ponytail` before implementing. Use `android-cli` to scaffold and to drive the
device. Use `compose-state-hoisting` and `compose-state-authoring` before writing screen
state, `navigation-3` for routing, `edge-to-edge` for insets.

## Versions

Verified July 2026. Scaffold with the `android` CLI and let it resolve transitive
versions; pin these in `gradle/libs.versions.toml`:

- AGP **9.2.0**
- Kotlin **2.3.21** (Compose Compiler plugin tracks the Kotlin version)
- Compose BOM **2026.06.01** (Material3 1.4.0, compose-ui 1.11.4)
- Navigation 3 **1.0** (stable)
- minSdk **26**, targetSdk current

## File manifest

```
settings.gradle.kts
build.gradle.kts
gradle/libs.versions.toml
app/
  build.gradle.kts
  src/main/AndroidManifest.xml
  src/main/kotlin/.../BukInApplication.kt
  src/main/kotlin/.../MainActivity.kt
  src/main/kotlin/.../navigation/BukInNavDisplay.kt
  src/main/res/values/strings.xml          ← ALL Spanish copy, single file
core/designsystem/
  src/main/kotlin/.../theme/Color.kt       ← the four brand values + derived tokens
  src/main/kotlin/.../theme/Type.kt
  src/main/kotlin/.../theme/Shape.kt
  src/main/kotlin/.../theme/Theme.kt       ← BukInTheme, light-only ColorScheme
  src/main/kotlin/.../component/TicketCard.kt
  src/main/kotlin/.../component/CheckInButton.kt      ← with halo rings
  src/main/kotlin/.../component/ProximityIllustration.kt
  src/main/kotlin/.../component/BukInFooter.kt
  src/main/res/drawable/                   ← wordmark + proximity vectors
domain/
  src/main/kotlin/.../model/Instancia.kt
  src/main/kotlin/.../model/Colaborador.kt
  src/main/kotlin/.../model/CheckInState.kt          ← sealed interface
features/onboarding/
  src/main/kotlin/.../OnboardingScreen.kt
  src/main/kotlin/.../OnboardingViewModel.kt
features/checkin/
  src/main/kotlin/.../CheckInScreen.kt
  src/main/kotlin/.../CheckInViewModel.kt            ← stub state source
```

`features/host` is created as an empty module with its Gradle file only. Session 2 fills
it.

## What to build

### Gradle and modules

Version catalog in `gradle/libs.versions.toml`. Modules per the graph in
`architecture.md`. Enforce the dependency direction — `:domain` must have no Android
dependency, and that is verified by the fact that it compiles as a pure Kotlin library.

### Theme

`Color.kt` defines exactly four brand constants — `#2F4DAA`, `#F7F9FF`, `#030819`,
`#2BAB51` — and derives everything else from them. Map into a Material 3 light
`ColorScheme`. **This is the only file in the project containing hex values.**

Edge-to-edge, light status bar icons over the light background.

### TicketCard

The signature component. Blue gradient fill, white text, the layout in
`ui-context.md`: title, `Ticket` pill, date + clock icon + large time, duration, and the
dotted-separator stub row with Check In / Salida times. The dotted line is what sells
it as a ticket — don't drop it. Add the "Necesito ayuda" strip tucked under the card.

Takes a domain model, holds no state.

### CheckInButton

Large, blue, bold label, with layered concentric halo rings at descending alpha behind
it, pulsing slowly via `rememberInfiniteTransition`. Respect reduced-motion.

### CheckInScreen

Renders `CheckInState` — `Scanning | Offline | Ready | Success | Error` — as a sealed
interface in `:domain`. Persistent ticket header and footer; only the center swaps.
Animate transitions, especially into `Success`.

This session the state comes from a stub in the ViewModel that cycles on tap or via a
debug control, so all four states are inspectable on-device without Bluetooth.

### Onboarding

Three screens. They introduce the app **and state honestly what this version does** —
but framed as preparation, not apology. A person who knows what to expect never feels a
seam; a person surprised mid-flow does. That is why the scope belongs here and nowhere
later.

Copy (final — put it in `strings.xml` as written):

**1 — One tap**
> **Tu asistencia, en un toque**
> Sin listas, sin fotos, sin códigos que escribir. Llegas, tocas una vez, listo.

**2 — Proximity**
> **Tu teléfono encuentra a tu anfitrión**
> Usamos Bluetooth para confirmar que estás en la sala. No usamos tu ubicación ni tu
> cámara. Solo deja el Bluetooth encendido.

**3 — What this version does**
> **Por ahora, registramos tu entrada**
> Marcas al llegar. La salida todavía la maneja tu anfitrión. Necesitas Bluetooth y
> conexión a internet en el momento de marcar.

Screen 3 is the honest one and it must not read as a disclaimer. "Por ahora" frames it
as a product in motion. Naming the two things a person needs (Bluetooth, internet) is
preparation that prevents a surprise later — which is the seamless choice, not the
cautious one.

Do **not** mention the identity limitation here. That anyone could select someone else's
name is a real limitation of the no-auth v1, but it is an architecture disclosure for the
presentation, not a warning to hand a user seconds before they check in. It is recorded
in `architecture.md` and `progress-tracker.md`.

Pager with indicators, a skip affordance, and a finish that routes onward. Onboarding
**explains permissions; it never requests them** — see the seamlessness rules in
`ui-context.md`. Persist the "seen onboarding" flag in `SharedPreferences` — no Room, no
DataStore dependency for one boolean.

### Role picker

A plain screen after onboarding: "Soy colaborador" / "Soy anfitrión". Not in the
mockups; keep it visually quiet so it doesn't compete with the check-in screen. Host
route is a placeholder this session.

### Strings

Every user-facing string in Spanish in one `strings.xml`. From the mockups, verbatim:
`Estamos localizando a tu anfitrión…`, `Registraste tu asistencia exitosamente.`,
`Usamos tecnología de proximidad, no desactives tu bluetooth.`, `Necesito ayuda`.

The offline card is the **one place the mockup copy changes.** The mockup's "Tranqui,
solo debes estar en la sala de tu anfitrión" promises something v1 cannot do — without
internet there is no way to save the attendance. Use:

> **Sin conexión**
> Podemos encontrar a tu anfitrión, pero necesitas internet para guardar tu asistencia.

Rationale is in `ui-context.md`. Warmth kept, promise corrected.

## Acceptance criteria

1. `./gradlew assembleDebug` passes.
2. `./gradlew installDebug` puts a running app on a physical device.
3. First launch shows onboarding; second launch skips it.
4. All four collaborator states render and are visually distinct at a glance.
5. Side-by-side with the mockups, the ticket card reads as the same design.
6. `Color.kt` is the only file containing a hex literal.
7. No user-facing string is hardcoded in a composable.
8. `:domain` compiles with no Android dependency.
9. Every screen-level composable has a `@Preview`.
10. **Seamlessness**, walked through on the device against `ui-context.md`:
    - Onboarding is 3 screens, swipeable, skippable, and never shown again
    - Onboarding requests **no** permissions — it explains only
    - The check-in screen has no "search" or "refresh" control
    - The success state has nothing to dismiss
    - Every state that blocks progress names its cause and offers a one-tap action
    - No spinner appears without a sentence next to it
11. The offline card uses the corrected copy, not the mockup's original line.

## Verification

```bash
./gradlew assembleDebug
./gradlew installDebug
```

Then use `android-cli` to screenshot each of the four states and compare against
`docs/assets/`. Attach or describe the comparison in your summary.

## Out of scope — do not build

Bluetooth of any kind. Supabase, networking, any dependency on `supabase-kt`. Host
roster. Manual registration. Check-out. Real data — hardcode one plausible `Instancia`
("Manejo de alimentos", 2 horas) to render the card.

## On completion

Update `context/progress-tracker.md`: move session 1 to Completed, set the current goal
to session 2, and record anything discovered that changes the plan.

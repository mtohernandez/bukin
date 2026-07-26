You are building **BukIn**, an Android BLE attendance app. This is session 1 of 3. All
planning is done — every decision you need has already been made and written down. Do
not re-plan, do not re-research settled questions, and do not widen the scope.

**Read in this order before writing code:**

1. `CLAUDE.md`
2. `context/project-overview.md` — what this is and what is explicitly out of scope
3. `context/ui-context.md` — the design system, in detail
4. `context/architecture.md` — module graph and invariants
5. `context/code-standards.md` — Kotlin and Compose conventions
6. `context/specs/01-foundation.md` — **your spec for this session**

Then look at the four mockups in `docs/assets/`. They are the visual target:
`check-in-screen.png`, `wait-check-in-screen.png`, `wait-check-in-screen-offline.png`,
`success-check-in-screen.png`.

**Your job:** execute `context/specs/01-foundation.md` completely — Gradle monorepo,
module skeleton, the Buk theme, the ticket card, the check-in screen with all four
states switchable by hand, 3-screen onboarding, and the role picker. No Bluetooth, no
network this session.

**The product goal for v1 is a seamless experience**, and it is defined as nine testable
rules in the Seamlessness section of `context/ui-context.md` — read them before building
any screen, and check them on the device before you call this done. The short version:
the happy path costs exactly one deliberate tap, nothing is manually started, nothing is
asked twice, and there is never a disabled control without a reason and a way out.

**Two copy decisions are already made — follow them exactly:**

- The onboarding copy is written verbatim in the spec. Screen 3 states what this version
  does (entry only; needs Bluetooth and internet). Frame it as preparation, not apology.
- The offline card **does not** use the mockup's line. The mockup promises presence alone
  is enough, which is false in v1. The corrected copy is in the spec and in
  `ui-context.md`.

Do **not** surface the identity limitation (that someone could pick another person's
name) anywhere in the UI. It is a presentation disclosure, not a user warning.

**Skills — invoke, don't work from memory:**

- `ponytail` before implementing anything non-trivial. Keep this codebase small.
- `android-cli` to scaffold the project and to drive the device
- `compose-state-hoisting` and `compose-state-authoring` before writing screen state
- `navigation-3` for routing
- `edge-to-edge` for insets

**First action:** add `Bash(./gradlew *)` to the allow-list in `.claude/settings.json`,
otherwise every build will prompt.

**Verification gate — you are not done until:**

- `./gradlew assembleDebug` passes
- `./gradlew installDebug` runs the app on a physical device
- All four collaborator states render and are unmistakably different at a glance
- Screenshots compared side by side with the mockups
- `Color.kt` is the only file in the project containing a hex literal
- No user-facing string is hardcoded in a composable

**Two rules that override defaults:**

- Commits are **never** co-authored. No `Co-Authored-By` trailer, no "Generated with
  Claude Code" line, no attribution of any kind.
- `docs/` is read-only human input.

**On completion:** update `context/progress-tracker.md` — move session 1 to Completed,
set the goal to session 2, and record anything you discovered that changes the plan.

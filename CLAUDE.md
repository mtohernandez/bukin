# BukIn

Android attendance app built on BLE proximity. A host opens a training session and
broadcasts a rotating code over Bluetooth Low Energy; collaborators in the same room
detect it and confirm their own attendance with one tap. No admin types anything
afterward. Built for Buk (Chilean HR company), deployed in Colombia.

## Stack

| Layer      | Technology                                   |
| ---------- | -------------------------------------------- |
| Language   | Kotlin                                       |
| UI         | Jetpack Compose + Material 3                 |
| Navigation | Navigation 3                                 |
| Build      | Gradle multi-module, version catalog         |
| Proximity  | Raw `BluetoothLeAdvertiser` / `LeScanner`    |
| Backend    | Supabase (Postgres + PostgREST). No server.  |
| minSdk     | 26 (required by supabase-kt)                 |
| Language   | Spanish only, single `strings.xml`           |

## Where to look

| Need                                        | Read                            |
| ------------------------------------------- | ------------------------------- |
| What we're building and what's out of scope | `context/project-overview.md`   |
| **Test hardware, deploying, what counts as verified** | `context/hardware-constraints.md` |
| Module graph, boundaries, invariants        | `context/architecture.md`       |
| Colors, type, the ticket card, screen states| `context/ui-context.md`         |
| Kotlin/Compose conventions                  | `context/code-standards.md`     |
| How to scope and verify work                | `context/ai-workflow-rules.md`  |
| Current state, decisions, open questions    | `context/progress-tracker.md`   |
| What to build this session                  | `context/specs/`                |

`docs/` is raw human input — the original assessment, real Play Store user feedback,
and the design mockups. **Read it, never edit it.**

## Hard rules

1. **Commits are never co-authored.** No `Co-Authored-By` trailer, no
   "Generated with Claude Code" line, no attribution of any kind. This overrides the
   default Claude Code commit convention. Non-negotiable.
2. **Never report something as working that you have not observed working.** Name the
   hardware in every claim — "on the phone over wireless adb", not a bare "verified". An
   emulator result never satisfies a physical-device criterion; if the phone was not
   reachable, say the criterion is unmet. There is **one phone and no USB cable**, and the
   Mac is the second BLE radio. Read `context/hardware-constraints.md` before any BLE work
   or any verification claim. This replaces the old "two physical phones" rule, which
   assumed hardware that no longer exists.
3. **No secrets in the repo.** The Supabase anon key ships in the APK by design and is
   safe only because every table is deny-all RLS behind one validated RPC. Never add a
   service-role key.
4. **Tokens, not hex.** Colors come from the theme. See `context/ui-context.md`.

## Skills

Invoke these rather than working from memory.

| Task                                        | Skill                                     |
| ------------------------------------------- | ----------------------------------------- |
| Before any non-trivial implementation       | `ponytail`                                |
| `remember`/`mutableStateOf` in a composable | `compose-state-authoring`                 |
| Deciding where state lives                  | `compose-state-hoisting`                  |
| `LaunchedEffect`, `DisposableEffect`, etc.  | `compose-side-effects`                    |
| `StateFlow`, `SharedFlow`, one-shot events  | `kotlin-flow-state-event-modeling`        |
| Scopes, `launch`, cancellation              | `kotlin-coroutines-structured-concurrency`|
| Screens and back stack                      | `navigation-3`                            |
| Insets, status/nav bar                      | `edge-to-edge`                            |
| Setting up tests                            | `testing-setup`                           |
| Emulator, device, screenshots, UI dump      | `android-cli`                             |
| Release build size / keep rules             | `r8-analyzer`                             |

`ponytail` exists because this codebase should stay small. Reach for the platform
before a dependency, one line before fifty.

## Build

```bash
./gradlew assembleDebug        # must pass before any unit of work is done
./gradlew installDebug         # deploy to the phone (over wireless adb — no cable exists)
./gradlew testDebugUnitTest    # unit tests
```

The phone connects over Wi-Fi, not USB. Pair once, then reconnect whenever the port
changes:

```bash
adb pair <ip>:<pairing-port> <6-digit-code>   # ports differ — see hardware-constraints.md
adb connect <ip>:<connect-port>
```

If Gradle triggers a permission prompt on every run, add `Bash(./gradlew *)` to the
allow-list in `.claude/settings.json`.

## Finishing a unit of work

1. It works end to end within its scope.
2. No invariant in `context/architecture.md` was violated.
3. `./gradlew assembleDebug` passes.
4. Every claim about it meets the evidence table in `context/hardware-constraints.md`,
   and anything that could not be run is reported as not run.
5. `context/progress-tracker.md` reflects what changed.

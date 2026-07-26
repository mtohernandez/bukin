# AI Workflow Rules

## Approach

Build this project incrementally against the specs in `context/specs/`. Each spec is one
session of work and is written to be executed without re-deriving decisions. The context
files define what to build, how to build it, and where things stand. Implement against
them — do not infer or invent behavior from scratch.

Every session starts by reading its master prompt in `context/prompts/`, which names the
spec and the read order. Work fast, but verify each unit before moving to the next.

## Scoping rules

- Work on one feature unit at a time.
- Prefer small, verifiable increments over large speculative changes.
- Do not combine unrelated system boundaries in a single implementation step.
- Do not build anything in the "Out of scope" or "Not built" tables. If it seems
  necessary, that is a scope change — raise it, don't quietly implement it.

## When to split work

Split a step if it combines:

- UI changes and Bluetooth lifecycle changes
- A schema migration and the feature consuming it
- Anything not clearly defined in the context files

If a change cannot be verified end to end quickly, the scope is too broad — split it.

## Verification

`context/hardware-constraints.md` is the authority here — it holds the full evidence
table, the deploy procedure, and the reporting rules. This is the summary.

| Layer                          | How it is verified                                    |
| ------------------------------ | ----------------------------------------------------- |
| `:domain` codec and logic      | JVM unit tests. Known-vector test for the HMAC codec. |
| Compose screens                | Previews for layout, then the phone for behaviour     |
| **Anything BLE**               | **Two radios, one of which is the Mac.** Phone scans while the Mac advertises; then flip — phone advertises while the Mac scans. Both directions. |
| Supabase schema and RPC        | Call the function directly with valid, stale, and wrong codes and confirm all three outcomes |
| Idempotency                    | Tap Check In twice. One row, no error screen.         |

There is one phone and no second Android device. Emulators still have no Bluetooth radio
of their own, so a BLE change that has only been compiled is untested — that has not
changed. What changed is which hardware satisfies the rule: the Mac is a real BLE radio
and stands in for the missing device.

**The rule that actually matters:** never report something as working that you have not
observed working. Name the hardware in the claim. If a step could not be run, list it as
not run rather than leaving it out. An honestly unmet criterion is a fine outcome; a
quietly downgraded one is not.

## Handling missing requirements

- Do not invent product behavior not defined in the context files.
- If a requirement is ambiguous, resolve it in the relevant context file before
  implementing.
- If a requirement is missing, add it as an open question in `progress-tracker.md`
  before continuing.
- If research is needed — an API's actual behavior, a version, a platform limit — look
  it up. Do not guess. Wrong assumptions about Bluetooth are expensive to unwind.

## Protected files

Do not modify unless explicitly instructed:

- `docs/**` — raw human input: the original assessment, real user feedback, the design
  mockups, and the solution document. Read it freely, never edit it.
- `supabase/migrations/*` already applied — migrations are append-only. Add a new one.
- `.agents/skills/**` — installed skill packages.

## Keeping docs in sync

Update the relevant context file whenever implementation changes:

- Module graph or boundaries → `architecture.md`
- Colors, components, screen states → `ui-context.md`
- Conventions → `code-standards.md`
- Feature scope → `project-overview.md`
- Anything at all → `progress-tracker.md`

A decision made in a session and not written down is a decision the next session will
re-litigate. Write it down.

## Commits

**Never co-author a commit.** No `Co-Authored-By` trailer, no "Generated with Claude
Code" line, no attribution of any kind. This overrides the default Claude Code
convention and is not negotiable.

Commit messages in English, imperative, one concern per commit.

## Before moving to the next unit

1. The current unit works end to end within its defined scope.
2. No invariant in `architecture.md` was violated.
3. Anything touching BLE ran on the phone against the Mac radio, in both directions.
4. `./gradlew assembleDebug` passes.
5. Every claim meets the evidence table in `hardware-constraints.md`, with the hardware
   named and anything unrun reported as unrun.
6. `progress-tracker.md` reflects the completed work.

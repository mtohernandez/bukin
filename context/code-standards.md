# Code Standards

## General

- Keep modules small and single-purpose. Respect the graph in `architecture.md`.
- Reach for the platform before a dependency, one line before fifty. Invoke `ponytail`
  before any non-trivial implementation.
- Fix root causes. Do not layer a workaround over a misunderstanding of the Bluetooth
  or Compose lifecycle.
- Do not mix unrelated concerns in one composable or one class.

## Kotlin

- Explicit types on public APIs; inference inside function bodies.
- Model states that cannot coexist as a `sealed interface`, not a bag of booleans. The
  check-in screen is `Scanning | Offline | Ready | Success | Error`, never
  `isScanning + isReady + hasError`.
- No `!!`. Handle nullability or restructure so it cannot be null.
- Prefer immutable `val` and data classes. Copy, don't mutate.
- Validate anything crossing a boundary — a scanned advertisement is untrusted input
  and gets length-checked before it is parsed.

## Compose

Invoke the relevant skill before writing this code — they encode rules this file only
summarizes.

- **State hoisting** (`compose-state-hoisting`): composables that render take state and
  emit events. Screen-level state lives in a ViewModel; UI-element state stays local.
- **No bare `var`** in a composable (`compose-state-authoring`). Use
  `remember { mutableStateOf(...) }`, and `rememberSaveable` when it must survive
  configuration change.
- **Effects** (`compose-side-effects`): `LaunchedEffect` keyed correctly,
  `DisposableEffect` for anything needing teardown — every BLE scan and advertisement
  must stop in `onDispose`. Use `rememberUpdatedState` for callbacks captured in
  long-lived effects.
- Preview every screen-level composable. Previews are how sessions verify UI without a
  device.
- No hex values, no hardcoded dimensions outside `:core:designsystem`.
- No strings in composables — everything through `stringResource`.

## Flows and coroutines

Invoke `kotlin-flow-state-event-modeling` and
`kotlin-coroutines-structured-concurrency` before writing these.

- **State** is a `StateFlow` with a real initial value. Update with `.update { }`,
  never a read-then-write on `.value`.
- **One-shot events** (navigate, show a snackbar) go through a `Channel` exposed as
  `receiveAsFlow()` — never a `StateFlow`, which replays and fires twice on rotation.
- Never store a `CoroutineScope` as a field. Use `viewModelScope`.
- Wrap callback-based Bluetooth APIs in `callbackFlow` and unregister in `awaitClose`.
  A leaked scan callback drains the battery and eventually gets the app throttled.
- Never `runBlocking` in production code.

## Bluetooth

- All BLE lives in `:core:ble`. It exposes typed Flows; it never leaks
  `ScanResult`, `BluetoothDevice`, or a raw callback across the module boundary.
- **Preflight before acting**, in this order, each with its own recovery message:
  adapter present → Bluetooth enabled → runtime permissions granted → for host mode,
  `isMultipleAdvertisementSupported()`.
- Always handle `AdvertiseCallback.onStartFailure` and `ScanCallback.onScanFailed`
  visibly. A swallowed failure is the worst outcome in this app.
- `setIncludeDeviceName(false)` on advertising data — including the device name is the
  most common cause of `ADVERTISE_FAILED_DATA_TOO_LARGE`.
- Stop advertising and scanning when the screen leaves the foreground, except the host's
  foreground service.
- Length-check and bounds-check every parsed advertisement before touching its bytes.

## Data and Supabase

- `:core:data` owns the Supabase client. No other module imports `supabase-kt`.
- DTOs stay inside `:core:data`. Repositories return `:domain` types.
- All access goes through the RPC. Never add a direct table query — RLS will deny it,
  and if it does not, that is a bug in the migration.
- Never commit a service-role key. The anon key is public by design and is the only key
  that may appear in the app.
- Migrations are append-only files in `supabase/migrations/`. Never edit an applied
  migration.

## Security

- The rotating code is verified in Postgres. The client sends the code it observed and
  the server decides. A client-side "valid" flag is a UI affordance only and is never
  trusted as authorization.
- The per-instance key never leaves the host device and the database.
- No secret goes into a log line. Log the outcome, not the code.

## Testing

- `:domain` is pure Kotlin: the HMAC codec, the payload encoder/decoder, and the
  window-tolerance logic all get JVM unit tests. These are the highest-value tests in
  the project — they are the parts that must be exactly right and the parts a device
  cannot conveniently verify.
- Test the codec against a known vector: fixed key, fixed counter, expected bytes. The
  Kotlin and SQL implementations must agree, and this test is what proves it.
- UI gets Compose previews and manual verification. No instrumentation suite.

## Naming and language

- Code, identifiers, comments, and commit messages in **English**.
- Domain nouns keep their Spanish names where they match the database and the source
  document: `Instancia`, `Colaborador`, `Inscripcion`, `codigoBle`, `fechaLlegada`.
  Do not translate them — the mismatch with the schema costs more than the consistency
  gains.
- All user-facing copy in **Spanish**, in `strings.xml`.

## File organization

- `app/` — application class, root navigation, manifest, DI wiring
- `domain/` — models, codec, use cases. Pure Kotlin, no Android imports
- `core/ble/` — advertiser, scanner, permissions, foreground service
- `core/data/` — Supabase client, DTOs, repositories
- `core/designsystem/` — theme, tokens, `TicketCard`, shared composables
- `features/<name>/` — one feature's screens and view models
- `supabase/migrations/` — SQL, append-only

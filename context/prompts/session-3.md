You are building **BukIn**, an Android BLE attendance app. This is session 3 of 3 — the
one that closes the loop and makes the demo real.

Session 1 delivered the design system and screens. Session 2 delivered working BLE
proximity: the host broadcasts a rotating code, the collaborator detects it and unlocks
the button. You are making that tap produce a verified row in Postgres.

**Read in this order before writing code:**

1. `CLAUDE.md`
2. `context/progress-tracker.md` — what sessions 1–2 delivered, especially which phone
   hosts and any Bluetooth quirks recorded
3. `context/architecture.md` — the auth/access model and the invariants
4. `context/code-standards.md` — the Supabase and security sections
5. `context/specs/03-supabase-checkin.md` — **your spec for this session**

Spec 03 contains the schema, the exact `ON CONFLICT` upsert, the SQL for recomputing the
rotating code, and the RLS posture. These were researched and verified during planning.
**Use them verbatim.**

**Your job:** Supabase schema and migrations, the `SECURITY DEFINER` verification
function, `supabase-kt` wiring, the full check-in submission, the host's live roster, and
manual registration.

**Skills — invoke, don't work from memory:**

- `ponytail` before implementing
- `kotlin-flow-state-event-modeling` for the roster stream and one-shot events
- `compose-side-effects` for the check-in submission

**Build order matters this session:**

1. Migrations and seed data first.
2. **Verify the RPC in the SQL editor before touching Kotlin.** Test a valid code, a
   one-window-stale code (accepted), a two-window-stale code (rejected), a wrong code
   (rejected), and a double call (one row, success both times).
3. Confirm the SQL code derivation agrees with the Kotlin known-vector test from
   session 2. Byte serialization must match exactly — big-endian, same widths. **This is
   the single most likely bug in the session.** Catch it here, not through the UI.
4. Only then wire the client.

**Security posture — do not deviate:** the anon key ships inside the APK and is public.
Every table gets RLS enabled with **no policies** (deny-all), table grants revoked from
`anon`, and `EXECUTE` granted on the functions only. If a direct table query works from
the client, the migration is wrong.

**Verification gate — you are not done until:**

- One phone plus the Mac beacon, one room (see @context/hardware-constraints.md):
  SCANNING → READY → tap → SUCCESS, and the row is in Postgres with
  `metodo_confirmacion = 'BLE'`
- Tapping Check In twice yields **one row and no error screen**
- A collaborator with no prior enrollment gets `origen = 'WALK_IN'`
- A replayed code, captured and submitted a minute later, is **rejected** — this is the
  proof the security model works and is worth demonstrating live
- A direct table query with the anon key is denied
- The host roster shows the arrival within seconds
- Manual registration writes `MANUAL` with `atestiguado_por_id` set
- Airplane mode produces the offline state, not a crash

**Two rules that override defaults:**

- Commits are **never** co-authored. No `Co-Authored-By` trailer, no "Generated with
  Claude Code" line, no attribution of any kind.
- `docs/` is read-only human input.

**On completion:** update `context/progress-tracker.md` to Complete with the demo
results. Note any limitation worth stating out loud in the presentation — particularly
the relay-attack limit documented in `architecture.md`. Disclosing it is worth more in an
architecture review than having it discovered.

1. Install package
Run this command to install the required dependencies.
Code:
File: Code
```
implementation("io.github.jan-tennert.supabase:supabase-kt:VERSION")
```

2. Add files
Copy the following code into your project.
Code:
File: MainActivity.kt
```
1val supabase = createSupabaseClient(
2    supabaseUrl = "https://nfysenajrfquusawyotc.supabase.co",
3    supabaseKey = "sb_publishable_E9INM8PvMJpKKKOjaqTP8Q_HWk0_tqS"
4  ) {
5    install(Postgrest)
6}
7
8class MainActivity : ComponentActivity() {
9    override fun onCreate(savedInstanceState: Bundle?) {
10        super.onCreate(savedInstanceState)
11        setContent {
12            MaterialTheme {
13                // A surface container using the 'background' color from the theme
14                Surface(
15                    modifier = Modifier.fillMaxSize(),
16                    color = MaterialTheme.colorScheme.background
17                ) {
18                    TodoList()
19                }
20            }
21        }
22    }
23}
24
25@Composable
26fun TodoList() {
27    var items by remember { mutableStateOf<List<TodoItem>>(listOf()) }
28    LaunchedEffect(Unit) {
29        withContext(Dispatchers.IO) {
30            items = supabase.from("todos")
31                              .select().decodeList<TodoItem>()
32        }
33    }
34    LazyColumn {
35        items(
36            items,
37            key = { item -> item.id },
38        ) { item ->
39            Text(
40                item.name,
41                modifier = Modifier.padding(8.dp),
42            )
43        }
44    }
45}
```

File: TodoItem.kt
```
1@Serializable
2data class TodoItem(val id: Int, val name: String)
```

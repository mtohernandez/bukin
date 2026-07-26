package com.buk.bukin.data

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

/**
 * The one Supabase client in the app.
 *
 * ## Why the key is in the source
 *
 * [PUBLISHABLE_KEY] is public by design. It ships inside the APK, and anyone with the APK
 * has it — there is no version of this app where it stays secret, so pretending otherwise
 * by loading it from a file would buy nothing but ceremony.
 *
 * What makes that safe is on the server, not here: every table has row level security
 * enabled with no policies at all, all table grants are revoked from `anon`, and the only
 * thing this key can do is `EXECUTE` the nine validated functions in
 * `supabase/migrations/0002_functions.sql`. It was verified by direct query — every table
 * answers `42501 permission denied`, and the ungranted internal helper does too.
 *
 * A service-role or secret key must never appear here. That one is not public, and it
 * bypasses row level security entirely.
 */
internal object BukInBackend {

    private const val URL = "https://nfysenajrfquusawyotc.supabase.co"
    private const val PUBLISHABLE_KEY = "sb_publishable_E9INM8PvMJpKKKOjaqTP8Q_HWk0_tqS"

    // Only Postgrest. There is no auth in v1, no storage, and no realtime — the host roster
    // polls, which spec 03 chose deliberately over a realtime subscription.
    val client = createSupabaseClient(supabaseUrl = URL, supabaseKey = PUBLISHABLE_KEY) {
        install(Postgrest)
    }
}

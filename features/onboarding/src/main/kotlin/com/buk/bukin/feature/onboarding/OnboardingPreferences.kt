package com.buk.bukin.feature.onboarding

import android.content.Context

/**
 * One boolean: has this person seen the intro.
 *
 * `SharedPreferences` rather than DataStore or Room — a single flag read once at launch
 * does not justify a dependency, a schema, or a coroutine.
 */
class OnboardingPreferences(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    var hasSeenOnboarding: Boolean
        get() = prefs.getBoolean(KEY_SEEN, false)
        set(value) = prefs.edit().putBoolean(KEY_SEEN, value).apply()

    private companion object {
        const val FILE_NAME = "bukin_onboarding"
        const val KEY_SEEN = "has_seen_onboarding"
    }
}

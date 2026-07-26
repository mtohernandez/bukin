package com.buk.bukin.feature.onboarding

import android.app.Application
import androidx.lifecycle.AndroidViewModel

/**
 * Owns the one thing onboarding persists. Which page is showing is UI element state and
 * stays in the composable with `rememberPagerState`; it has no business being here.
 */
class OnboardingViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = OnboardingPreferences(application)

    /** Nothing is asked twice — after this, onboarding never appears again. */
    fun markSeen() {
        preferences.hasSeenOnboarding = true
    }
}

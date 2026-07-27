package com.buk.bukin.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * The four moments in the app that vibrate.
 *
 * **Nothing else does.** A phone that buzzes on every tap is not premium, it is noisy — and
 * a haptic that fires constantly stops carrying information, which defeats the one place
 * this app genuinely needs it: telling someone their attendance registered without making
 * them look at the screen.
 *
 * Named rather than called inline so the map is enforceable by reading one file.
 */
class BukHaptics internal constructor(private val haptics: HapticFeedback) {

    /** Check-in confirmed. Fires at the *start* of the morph, not on the raw tap. */
    fun confirm() = haptics.performHapticFeedback(HapticFeedbackType.Confirm)

    /** The server recomputed the code and refused it. */
    fun reject() = haptics.performHapticFeedback(HapticFeedbackType.Reject)

    /** An onboarding page settles. */
    fun pageSettled() = haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)

    /** The help sheet opens. */
    fun sheetOpened() = haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
}

@Composable
fun rememberBukHaptics(): BukHaptics {
    val haptics = LocalHapticFeedback.current
    return remember(haptics) { BukHaptics(haptics) }
}

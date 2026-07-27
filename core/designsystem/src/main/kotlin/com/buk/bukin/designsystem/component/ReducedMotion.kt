package com.buk.bukin.designsystem.component

import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode

/**
 * Whether the system wants motion at all.
 *
 * Android exposes this as the developer/accessibility animation scale rather than a
 * "reduced motion" flag; a scale of zero means the user has turned animations off, and an
 * app that keeps pulsing anyway is ignoring an explicit setting.
 *
 * **This is observed, not sampled.** It used to read the setting once inside
 * `remember(resolver)`, so flipping the toggle in Developer options changed nothing until
 * the process restarted — the app was honouring a value from whenever the screen happened
 * to be composed. A `ContentObserver` costs one registration and makes the setting mean
 * what it says.
 *
 * Reduced motion collapses a transition to a **cut**, never to a missing state: the success
 * container still appears, filled, with the check complete. The information never depends
 * on the animation.
 */
@Composable
fun animationsEnabled(): Boolean {
    if (LocalInspectionMode.current) return false
    val resolver = LocalContext.current.contentResolver

    val enabled by produceState(initialValue = resolver.animatorScale() != 0f, resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                value = resolver.animatorScale() != 0f
            }
        }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        awaitDispose { resolver.unregisterContentObserver(observer) }
    }
    return enabled
}

private fun ContentResolver.animatorScale(): Float =
    Settings.Global.getFloat(this, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)

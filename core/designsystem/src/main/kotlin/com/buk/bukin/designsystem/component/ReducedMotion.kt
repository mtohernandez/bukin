package com.buk.bukin.designsystem.component

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode

/**
 * Whether the system wants motion at all.
 *
 * Android exposes this as the developer/accessibility animation scale rather than a
 * "reduced motion" flag; a scale of zero means the user has turned animations off, and an
 * app that keeps pulsing anyway is ignoring an explicit setting.
 */
@Composable
fun animationsEnabled(): Boolean {
    if (LocalInspectionMode.current) return false
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
}

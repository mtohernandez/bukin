package com.buk.bukin

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.buk.bukin.designsystem.theme.BukInTheme
import com.buk.bukin.feature.onboarding.OnboardingPreferences
import com.buk.bukin.navigation.BukInKey
import com.buk.bukin.navigation.BukInNavDisplay
import com.buk.bukin.ui.IdentityPreferences

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Forced light on both bars. The default `auto` style follows the system dark
        // theme, which on a dark-mode device would put white icons over this app's very
        // light background — the app itself has no dark variant.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        // Onboarding once, then a name once, then straight to the role picker forever.
        // Both are one synchronous SharedPreferences read; neither justifies a splash.
        val startKey = when {
            !OnboardingPreferences(this).hasSeenOnboarding -> BukInKey.Onboarding
            IdentityPreferences(this).colaborador == null -> BukInKey.NameEntry
            else -> BukInKey.RolePicker
        }

        setContent {
            BukInTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    BukInNavDisplay(startKey = startKey)
                }
            }
        }
    }
}

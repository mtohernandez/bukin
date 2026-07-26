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

        val startKey = if (OnboardingPreferences(this).hasSeenOnboarding) {
            BukInKey.RolePicker
        } else {
            BukInKey.Onboarding
        }

        setContent {
            BukInTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    BukInNavDisplay(
                        startKey = startKey,
                        // Session 1 has no Bluetooth to move the state machine.
                        showDebugStateControl = true,
                    )
                }
            }
        }
    }
}

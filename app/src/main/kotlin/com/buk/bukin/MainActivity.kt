package com.buk.bukin

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.buk.bukin.designsystem.theme.BukField
import com.buk.bukin.designsystem.theme.BukInTheme
import com.buk.bukin.feature.onboarding.OnboardingPreferences
import com.buk.bukin.navigation.BukInKey
import com.buk.bukin.navigation.BukInNavDisplay
import com.buk.bukin.ui.IdentityPreferences

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Before super.onCreate, which is the contract. The AVD writes `buk` on and then
        // `in` after it, which is the only indeterminate loading indicator the app has.
        //
        // Deliberately **not** held with an OnPreDrawListener: the two SharedPreferences
        // reads below are synchronous and there is nothing to wait for. Holding the splash
        // to look busy is the opposite of what this app is for.
        installSplashScreen()

        // Forced light on both bars. The default `auto` style follows the system dark
        // theme, which on a dark-mode device would put white icons over this app's very
        // light background — the app itself has no dark variant.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        // First run is now one flow: onboarding absorbs the name question, so there is no
        // separate NameEntry start key any more. A returning user goes straight to the role
        // picker — see RolePickerScreen for why that is a known cut rather than an oversight.
        val startKey = if (
            !OnboardingPreferences(this).hasSeenOnboarding ||
            IdentityPreferences(this).colaborador == null
        ) {
            BukInKey.Onboarding
        } else {
            BukInKey.RolePicker
        }

        setContent {
            BukInTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = BukField) {
                    BukInNavDisplay(startKey = startKey)
                }
            }
        }
    }
}

package com.hoofdirect.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.hoofdirect.app.designsystem.theme.HoofDirectTheme
import com.hoofdirect.app.feature.onboarding.data.OnboardingPreferencesManager
import com.hoofdirect.app.navigation.HoofDirectNavHost
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var onboardingPreferencesManager: OnboardingPreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            HoofDirectTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    HoofDirectNavHost(
                        onboardingPreferencesManager = onboardingPreferencesManager
                    )
                }
            }
        }
    }
}

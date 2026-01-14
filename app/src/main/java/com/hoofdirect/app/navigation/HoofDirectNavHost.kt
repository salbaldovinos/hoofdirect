package com.hoofdirect.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.hoofdirect.app.feature.auth.ui.AuthViewModel
import com.hoofdirect.app.feature.auth.ui.EmailVerificationScreen
import com.hoofdirect.app.feature.auth.ui.ForgotPasswordScreen
import com.hoofdirect.app.feature.auth.ui.ProfileSetupScreen
import com.hoofdirect.app.feature.auth.ui.SignInScreen
import com.hoofdirect.app.feature.onboarding.data.OnboardingPreferencesManager
import com.hoofdirect.app.feature.onboarding.navigation.OnboardingNavHost
import com.hoofdirect.app.ui.MainAppShell
import kotlinx.coroutines.flow.first

@Composable
fun HoofDirectNavHost(
    navController: NavHostController = rememberNavController(),
    authViewModel: AuthViewModel = hiltViewModel(),
    onboardingPreferencesManager: OnboardingPreferencesManager
) {
    val authState by authViewModel.authState.collectAsState()

    // Track onboarding completion state
    var isOnboardingComplete by remember { mutableStateOf<Boolean?>(null) }

    // Check onboarding status on first composition
    // For existing users with completed profiles, auto-complete onboarding
    LaunchedEffect(authState.isAuthenticated, authState.hasCompletedProfile) {
        if (authState.isAuthenticated) {
            val onboardingDone = onboardingPreferencesManager.isOnboardingComplete.first()

            // If user has completed their profile but hasn't done the new onboarding,
            // auto-mark it as complete (they're an existing user who set up before onboarding existed)
            if (!onboardingDone && authState.hasCompletedProfile) {
                onboardingPreferencesManager.setOnboardingComplete()
                isOnboardingComplete = true
            } else {
                isOnboardingComplete = onboardingDone
            }
        }
    }

    // Determine start destination based on auth state and onboarding completion
    val startDestination: Any = when {
        !authState.isAuthenticated -> NavDestination.SignIn
        !authState.hasCompletedProfile -> NavDestination.ProfileSetup
        isOnboardingComplete == false -> NavDestination.Onboarding
        else -> NavDestination.MainApp
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // Auth Flow
        composable<NavDestination.SignIn> {
            SignInScreen(
                onNavigateToForgotPassword = {
                    navController.navigate(NavDestination.ForgotPassword)
                },
                onSignInSuccess = { hasCompletedProfile ->
                    // After sign-in, check profile and onboarding status
                    // The startDestination logic will handle routing to the correct screen
                    val destination = when {
                        !hasCompletedProfile -> NavDestination.ProfileSetup
                        isOnboardingComplete == false -> NavDestination.Onboarding
                        else -> NavDestination.MainApp
                    }
                    navController.navigate(destination) {
                        popUpTo(NavDestination.SignIn) { inclusive = true }
                    }
                }
            )
        }

        // Note: SignUp is handled via marketing website (www.arieldigitalmarketing.com)
        // The SignUpScreen.kt file is kept but not connected to navigation

        composable<NavDestination.ForgotPassword> {
            ForgotPasswordScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onResetLinkSent = {
                    navController.popBackStack()
                }
            )
        }

        composable<NavDestination.EmailVerification> { backStackEntry ->
            val args = backStackEntry.toRoute<NavDestination.EmailVerification>()
            EmailVerificationScreen(
                email = args.email,
                onVerificationComplete = {
                    navController.navigate(NavDestination.ProfileSetup) {
                        popUpTo(NavDestination.SignIn) { inclusive = true }
                    }
                },
                onContinueWithoutVerifying = {
                    navController.navigate(NavDestination.ProfileSetup) {
                        popUpTo(NavDestination.SignIn) { inclusive = true }
                    }
                }
            )
        }

        composable<NavDestination.ProfileSetup> {
            ProfileSetupScreen(
                onProfileComplete = {
                    // After profile setup, go to onboarding
                    navController.navigate(NavDestination.Onboarding) {
                        popUpTo(NavDestination.ProfileSetup) { inclusive = true }
                    }
                }
            )
        }

        // Onboarding flow
        composable<NavDestination.Onboarding> {
            OnboardingNavHost(
                onOnboardingComplete = {
                    navController.navigate(NavDestination.MainApp) {
                        popUpTo(NavDestination.Onboarding) { inclusive = true }
                    }
                }
            )
        }

        // Main App with bottom navigation
        composable<NavDestination.MainApp> {
            MainAppShell(
                onSignOut = {
                    authViewModel.signOut()
                    navController.navigate(NavDestination.SignIn) {
                        popUpTo(NavDestination.MainApp) { inclusive = true }
                    }
                }
            )
        }
    }
}

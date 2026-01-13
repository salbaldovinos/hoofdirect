package com.hoofdirect.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.hoofdirect.app.ui.MainAppShell

@Composable
fun HoofDirectNavHost(
    navController: NavHostController = rememberNavController(),
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val authState by authViewModel.authState.collectAsState()

    // Determine start destination based on auth state
    val startDestination: Any = when {
        authState.isAuthenticated && !authState.hasCompletedProfile -> NavDestination.ProfileSetup
        authState.isAuthenticated -> NavDestination.MainApp
        else -> NavDestination.SignIn
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
                    val destination = if (hasCompletedProfile) {
                        NavDestination.MainApp
                    } else {
                        NavDestination.ProfileSetup
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
                    navController.navigate(NavDestination.MainApp) {
                        popUpTo(NavDestination.ProfileSetup) { inclusive = true }
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

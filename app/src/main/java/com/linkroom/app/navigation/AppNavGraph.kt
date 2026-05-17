package com.linkroom.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.linkroom.app.feature.emulator.EmulatorScreen
import com.linkroom.app.feature.library.GameLibraryScreen
import com.linkroom.app.feature.library.GameLibraryViewModel
import com.linkroom.app.feature.onboarding.OnboardingScreen
import com.linkroom.app.feature.settings.SettingsScreen

private object Routes {
    const val Onboarding = "onboarding"
    const val Library = "library"
    const val Settings = "settings"
    const val Emulator = "emulator/{romId}"

    fun emulator(romId: String) = "emulator/$romId"
}

@Composable
fun AppNavGraph(
    libraryViewModel: GameLibraryViewModel,
    onboardingComplete: Boolean,
    onOnboardingComplete: () -> Unit
) {
    val navController = rememberNavController()
    val libraryState by libraryViewModel.uiState.collectAsStateWithLifecycle()

    NavHost(
        navController = navController,
        startDestination = if (onboardingComplete) Routes.Library else Routes.Onboarding
    ) {
        composable(Routes.Onboarding) {
            OnboardingScreen(
                onContinue = {
                    onOnboardingComplete()
                    navController.navigate(Routes.Library) {
                        popUpTo(Routes.Onboarding) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.Library) {
            GameLibraryScreen(
                state = libraryState,
                onImportRom = libraryViewModel::importRom,
                onImportCancelled = libraryViewModel::onImportCancelled,
                onDismissImportError = libraryViewModel::dismissImportError,
                onOpenSettings = { navController.navigate(Routes.Settings) },
                onToggleFavorite = libraryViewModel::toggleFavorite,
                onRefreshCoverArt = libraryViewModel::refreshCoverArt,
                onRemoveCoverArt = libraryViewModel::removeCoverArt,
                onOpenRom = { rom ->
                    libraryViewModel.markRomPlayed(rom.id)
                    navController.navigate(Routes.emulator(rom.id))
                }
            )
        }
        composable(Routes.Settings) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.Emulator,
            arguments = listOf(navArgument("romId") { type = NavType.StringType })
        ) { backStackEntry ->
            val romId = backStackEntry.arguments?.getString("romId")
            val rom = libraryState.importedRoms.firstOrNull { it.id == romId }
            EmulatorScreen(
                rom = rom,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

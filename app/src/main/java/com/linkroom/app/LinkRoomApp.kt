package com.linkroom.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.linkroom.app.feature.library.GameLibraryViewModel
import com.linkroom.app.navigation.AppNavGraph
import com.linkroom.app.persistence.AppPreferences
import com.linkroom.app.ui.theme.LinkRoomTheme
import androidx.compose.ui.platform.LocalContext

@Composable
fun LinkRoomApp() {
    LinkRoomTheme {
        val context = LocalContext.current
        val libraryViewModel: GameLibraryViewModel = viewModel()
        val appPreferences = remember(context) {
            AppPreferences(context.applicationContext)
        }
        var onboardingComplete by remember {
            mutableStateOf(appPreferences.isOnboardingComplete())
        }

        LaunchedEffect(libraryViewModel, context) {
            libraryViewModel.initialize(context.applicationContext)
        }

        AppNavGraph(
            libraryViewModel = libraryViewModel,
            onboardingComplete = onboardingComplete,
            onOnboardingComplete = {
                appPreferences.setOnboardingComplete(true)
                onboardingComplete = true
            }
        )
    }
}

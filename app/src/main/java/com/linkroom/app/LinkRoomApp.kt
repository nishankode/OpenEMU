package com.linkroom.app

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.linkroom.app.feature.library.GameLibraryViewModel
import com.linkroom.app.navigation.AppNavGraph
import com.linkroom.app.ui.theme.LinkRoomTheme

@Composable
fun LinkRoomApp() {
    LinkRoomTheme {
        val libraryViewModel: GameLibraryViewModel = viewModel()
        AppNavGraph(libraryViewModel = libraryViewModel)
    }
}

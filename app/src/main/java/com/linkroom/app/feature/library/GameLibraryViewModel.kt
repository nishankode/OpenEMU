package com.linkroom.app.feature.library

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class GameLibraryViewModel : ViewModel() {
    private companion object {
        const val TAG = "GameLibraryViewModel"
    }

    private val _uiState = MutableStateFlow(RomImportState())
    val uiState: StateFlow<RomImportState> = _uiState.asStateFlow()

    fun importRom(context: Context, uri: Uri) {
        Log.i(TAG, "Import requested for URI: $uri")
        RomPicker.createRomHandle(context, uri)
            .onSuccess { rom ->
                Log.i(TAG, "Imported ROM metadata for Phase 0: ${rom.filename}")
                _uiState.update { state ->
                    state.copy(
                        importedRoms = state.importedRoms + rom,
                        importError = null
                    )
                }
            }
            .onFailure { error ->
                Log.w(TAG, "ROM import rejected.", error)
                _uiState.update { state ->
                    state.copy(importError = error.message ?: "Unable to import this file.")
                }
            }
    }

    fun onImportCancelled() {
        Log.i(TAG, "ROM import cancelled by user.")
    }

    fun dismissImportError() {
        _uiState.update { it.copy(importError = null) }
    }
}

package com.linkroom.app.feature.library

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GameLibraryViewModel : ViewModel() {
    private companion object {
        const val TAG = "GameLibraryViewModel"
    }

    private val _uiState = MutableStateFlow(RomImportState())
    val uiState: StateFlow<RomImportState> = _uiState.asStateFlow()
    private var metadataStore: RomMetadataStore? = null

    fun initialize(context: Context) {
        if (metadataStore != null) {
            return
        }

        val appContext = context.applicationContext
        metadataStore = RomMetadataStore(appContext)
        viewModelScope.launch {
            val restoredRoms = withContext(Dispatchers.IO) {
                metadataStore?.loadRoms().orEmpty()
            }
            _uiState.update { state ->
                state.copy(importedRoms = (state.importedRoms + restoredRoms).distinctBy { it.id })
            }
        }
    }

    fun importRom(context: Context, uri: Uri) {
        Log.i(TAG, "Import requested for URI: $uri")
        val appContext = context.applicationContext
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                RomPicker.createRomHandle(appContext, uri)
            }

            result
                .onSuccess { rom ->
                    Log.i(TAG, "Imported ROM metadata: ${rom.filename}; localPath=${rom.localRomPath}")
                    val updatedRoms = (_uiState.value.importedRoms.filterNot { it.id == rom.id } + rom)
                    withContext(Dispatchers.IO) {
                        metadataStore?.saveRoms(updatedRoms)
                    }
                    _uiState.update { state ->
                        state.copy(
                            importedRoms = updatedRoms,
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
    }

    fun onImportCancelled() {
        Log.i(TAG, "ROM import cancelled by user.")
    }

    fun dismissImportError() {
        _uiState.update { it.copy(importError = null) }
    }
}

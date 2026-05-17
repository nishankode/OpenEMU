package com.linkroom.app.feature.library

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
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
    private var coverArtLookupService: CoverArtLookupService? = null

    fun initialize(context: Context) {
        if (metadataStore != null) {
            return
        }

        val appContext = context.applicationContext
        metadataStore = RomMetadataStore(appContext)
        coverArtLookupService = CoverArtLookupService(appContext)
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
                    val existingRom = _uiState.value.importedRoms.firstOrNull { it.id == rom.id }
                    val mergedRom = existingRom?.let { existing ->
                        rom.copy(
                            isFavorite = existing.isFavorite,
                            lastPlayedAt = existing.lastPlayedAt,
                            playCount = existing.playCount,
                            coverImagePath = existing.coverImagePath,
                            thumbnailImagePath = existing.thumbnailImagePath
                        )
                    } ?: rom
                    val updatedRoms = (_uiState.value.importedRoms.filterNot { it.id == rom.id } + mergedRom)
                    withContext(Dispatchers.IO) {
                        metadataStore?.saveRoms(updatedRoms)
                    }
                    _uiState.update { state ->
                        state.copy(
                            importedRoms = updatedRoms,
                            importError = null
                        )
                    }
                    fetchCoverArt(mergedRom, forceRefresh = false)
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

    fun markRomPlayed(romId: String) {
        updateRomMetadata(romId) { rom ->
            rom.copy(
                lastPlayedAt = System.currentTimeMillis(),
                playCount = rom.playCount + 1
            )
        }
    }

    fun toggleFavorite(romId: String) {
        updateRomMetadata(romId) { rom ->
            rom.copy(isFavorite = !rom.isFavorite)
        }
    }

    fun refreshCoverArt(romId: String) {
        val rom = _uiState.value.importedRoms.firstOrNull { it.id == romId } ?: return
        fetchCoverArt(rom, forceRefresh = true)
    }

    fun removeCoverArt(romId: String) {
        val rom = _uiState.value.importedRoms.firstOrNull { it.id == romId } ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                coverArtLookupService?.removeCover(rom)
            }
            updateRomMetadata(romId) { existingRom ->
                existingRom.copy(coverImagePath = null)
            }
        }
    }

    private fun fetchCoverArt(rom: RomHandle, forceRefresh: Boolean) {
        if (!forceRefresh && rom.coverImagePath?.let { File(it).isFile } == true) {
            Log.i(TAG, "Cover lookup skipped; ROM already has cover path: ${rom.coverImagePath}")
            return
        }

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                coverArtLookupService?.lookupAndCacheCover(rom, forceRefresh)
            }
            when (result) {
                is CoverArtLookupResult.Success -> {
                    Log.i(
                        TAG,
                        "Cover lookup success: rom=${rom.filename}; matched=${result.matchedFilename}; path=${result.localPath}"
                    )
                    updateRomMetadata(rom.id) { existingRom ->
                        existingRom.copy(coverImagePath = result.localPath)
                    }
                }
                is CoverArtLookupResult.NotFound -> {
                    Log.i(TAG, "Cover lookup failed: rom=${rom.filename}; candidates=${result.candidates}")
                    if (forceRefresh) {
                        updateRomMetadata(rom.id) { existingRom ->
                            existingRom.copy(coverImagePath = null)
                        }
                    }
                }
                null -> {
                    Log.w(TAG, "Cover lookup service unavailable for ${rom.filename}.")
                }
            }
        }
    }

    private fun updateRomMetadata(romId: String, transform: (RomHandle) -> RomHandle) {
        val currentRoms = _uiState.value.importedRoms
        val updatedRoms = currentRoms.map { rom ->
            if (rom.id == romId) transform(rom) else rom
        }
        if (updatedRoms == currentRoms) {
            return
        }

        _uiState.update { state -> state.copy(importedRoms = updatedRoms) }
        viewModelScope.launch(Dispatchers.IO) {
            metadataStore?.saveRoms(updatedRoms)
        }
    }
}

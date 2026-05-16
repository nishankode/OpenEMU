package com.linkroom.app.feature.library

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

class RomMetadataStore(context: Context) {
    private val metadataFile = File(context.applicationContext.filesDir, METADATA_FILENAME)

    fun loadRoms(): List<RomHandle> {
        if (!metadataFile.exists()) {
            Log.i(TAG, "No persisted ROM metadata found.")
            return emptyList()
        }

        return runCatching {
            val array = JSONArray(metadataFile.readText())
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    val localPath = item.optString(KEY_LOCAL_ROM_PATH).takeIf { it.isNotBlank() }
                    val available = localPath?.let { File(it).isFile } ?: false
                    add(
                        RomHandle(
                            id = item.getString(KEY_ID),
                            uri = Uri.EMPTY,
                            filename = item.getString(KEY_FILENAME),
                            localRomPath = localPath,
                            extension = item.optString(KEY_EXTENSION, "gba"),
                            importedAtMillis = item.optLong(KEY_IMPORTED_AT, 0L),
                            isAvailable = available
                        )
                    )
                }
            }
        }.onSuccess { roms ->
            Log.i(TAG, "Restored ${roms.size} ROM metadata item(s).")
        }.onFailure { error ->
            Log.w(TAG, "Unable to restore ROM metadata.", error)
        }.getOrDefault(emptyList())
    }

    fun saveRoms(roms: List<RomHandle>) {
        runCatching {
            val array = JSONArray()
            roms.forEach { rom ->
                array.put(
                    JSONObject()
                        .put(KEY_ID, rom.id)
                        .put(KEY_FILENAME, rom.filename)
                        .put(KEY_LOCAL_ROM_PATH, rom.localRomPath.orEmpty())
                        .put(KEY_EXTENSION, rom.extension)
                        .put(KEY_IMPORTED_AT, rom.importedAtMillis)
                )
            }
            metadataFile.writeText(array.toString())
        }.onSuccess {
            Log.i(TAG, "Saved ${roms.size} ROM metadata item(s).")
        }.onFailure { error ->
            Log.w(TAG, "Unable to save ROM metadata.", error)
        }
    }

    private companion object {
        const val TAG = "RomMetadataStore"
        const val METADATA_FILENAME = "rom_library.json"
        const val KEY_ID = "id"
        const val KEY_FILENAME = "filename"
        const val KEY_LOCAL_ROM_PATH = "localRomPath"
        const val KEY_EXTENSION = "extension"
        const val KEY_IMPORTED_AT = "importedAtMillis"
    }
}

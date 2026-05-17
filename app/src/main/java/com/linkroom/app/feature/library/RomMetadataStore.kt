package com.linkroom.app.feature.library

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

class RomMetadataStore(context: Context) {
    private val filesDir = context.applicationContext.filesDir
    private val metadataFile = File(filesDir, METADATA_FILENAME)

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
                    val id = item.getString(KEY_ID)
                    val localPath = item.optString(KEY_LOCAL_ROM_PATH).takeIf { it.isNotBlank() }
                    val gameRootPath = item.optString(KEY_GAME_ROOT_PATH)
                        .takeIf { it.isNotBlank() }
                        ?: File(filesDir, "games/$id").absolutePath
                    val available = localPath?.let { File(it).isFile } ?: false
                    add(
                        RomHandle(
                            id = id,
                            uri = Uri.EMPTY,
                            filename = item.getString(KEY_FILENAME),
                            localRomPath = localPath,
                            gameRootPath = gameRootPath,
                            extension = item.optString(KEY_EXTENSION, "gba"),
                            importedAtMillis = item.optLong(KEY_IMPORTED_AT, 0L),
                            isAvailable = available,
                            isFavorite = item.optBoolean(KEY_IS_FAVORITE, false),
                            lastPlayedAt = item.optNullableLong(KEY_LAST_PLAYED_AT),
                            playCount = item.optInt(KEY_PLAY_COUNT, 0),
                            coverImagePath = item.optNullableString(KEY_COVER_IMAGE_PATH),
                            thumbnailImagePath = item.optNullableString(KEY_THUMBNAIL_IMAGE_PATH)
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
                        .put(KEY_GAME_ROOT_PATH, rom.gameRootPath.orEmpty())
                        .put(KEY_EXTENSION, rom.extension)
                        .put(KEY_IMPORTED_AT, rom.importedAtMillis)
                        .put(KEY_IS_FAVORITE, rom.isFavorite)
                        .put(KEY_LAST_PLAYED_AT, rom.lastPlayedAt ?: JSONObject.NULL)
                        .put(KEY_PLAY_COUNT, rom.playCount)
                        .put(KEY_COVER_IMAGE_PATH, rom.coverImagePath ?: JSONObject.NULL)
                        .put(KEY_THUMBNAIL_IMAGE_PATH, rom.thumbnailImagePath ?: JSONObject.NULL)
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
        const val KEY_GAME_ROOT_PATH = "gameRootPath"
        const val KEY_EXTENSION = "extension"
        const val KEY_IMPORTED_AT = "importedAtMillis"
        const val KEY_IS_FAVORITE = "isFavorite"
        const val KEY_LAST_PLAYED_AT = "lastPlayedAt"
        const val KEY_PLAY_COUNT = "playCount"
        const val KEY_COVER_IMAGE_PATH = "coverImagePath"
        const val KEY_THUMBNAIL_IMAGE_PATH = "thumbnailImagePath"
    }
}

private fun JSONObject.optNullableString(key: String): String? {
    return if (isNull(key)) {
        null
    } else {
        optString(key).takeIf { it.isNotBlank() }
    }
}

private fun JSONObject.optNullableLong(key: String): Long? {
    return if (isNull(key) || !has(key)) {
        null
    } else {
        optLong(key)
    }
}

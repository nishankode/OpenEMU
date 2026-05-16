package com.linkroom.app.feature.library

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import java.util.Locale
import java.util.UUID

object RomPicker {
    private const val TAG = "RomPicker"
    private val allowedExtensions = setOf("gba", "zip")

    fun createRomHandle(context: Context, uri: Uri): Result<RomHandle> {
        val filename = resolveDisplayName(context, uri)?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return Result.failure(
                IllegalArgumentException("Unable to read the selected file name. Choose a .gba or .zip file.")
            )

        val extension = filename.substringAfterLast('.', missingDelimiterValue = "")
            .lowercase(Locale.US)

        if (extension !in allowedExtensions) {
            return Result.failure(
                IllegalArgumentException("That file is not supported yet. Select a .gba or .zip file.")
            )
        }

        Log.i(TAG, "Accepted ROM import candidate: $filename")
        return Result.success(
            RomHandle(
                id = UUID.randomUUID().toString(),
                uri = uri,
                filename = filename
            )
        )
    }

    private fun resolveDisplayName(context: Context, uri: Uri): String? {
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    return cursor.getString(nameIndex)
                }
            }
        }.onFailure { error ->
            Log.w(TAG, "Unable to query display name for selected URI.", error)
        }

        return uri.lastPathSegment
            ?.substringAfterLast('/')
            ?.let(Uri::decode)
    }
}

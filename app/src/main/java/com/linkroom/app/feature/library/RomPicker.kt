package com.linkroom.app.feature.library

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import java.io.File
import java.io.IOException
import java.security.MessageDigest
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
        var id = UUID.randomUUID().toString()
        val localRomPath = if (extension == "gba") {
            copyGbaToPrivateStorage(context, uri, id)
                .getOrElse { error ->
                    return Result.failure(
                        IllegalArgumentException(
                            error.message ?: "Unable to copy this .gba file into app storage."
                        )
                    )
                }.let { temporaryPath ->
                    val temporaryFile = File(temporaryPath)
                    id = sha256(temporaryFile)
                    val finalFile = File(temporaryFile.parentFile, "$id.gba")
                    if (temporaryFile.absolutePath != finalFile.absolutePath) {
                        temporaryFile.copyTo(finalFile, overwrite = true)
                        temporaryFile.delete()
                    }
                    Log.i(TAG, "Stable ROM identity: $id")
                    finalFile.absolutePath
                }
        } else {
            Log.i(TAG, "ZIP import accepted for library only; native boot supports .gba in this phase.")
            null
        }
        val gameRootPath = localRomPath?.let { File(context.filesDir, "games/$id").absolutePath }

        return Result.success(
            RomHandle(
                id = id,
                uri = uri,
                filename = filename,
                localRomPath = localRomPath,
                gameRootPath = gameRootPath,
                extension = extension,
                importedAtMillis = System.currentTimeMillis(),
                isAvailable = localRomPath != null
            )
        )
    }

    private fun copyGbaToPrivateStorage(context: Context, uri: Uri, id: String): Result<String> {
        return runCatching {
            val romDirectory = File(context.filesDir, "imported_roms")
            if (!romDirectory.exists() && !romDirectory.mkdirs()) {
                throw IOException("Unable to prepare private ROM storage.")
            }

            val destination = File(romDirectory, "$id.gba")
            var copiedBytes = 0L
            context.contentResolver.openInputStream(uri)?.use { input ->
                destination.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) {
                            break
                        }
                        output.write(buffer, 0, read)
                        copiedBytes += read
                    }
                }
            } ?: throw IOException("Unable to open the selected file.")

            if (copiedBytes <= 0L) {
                destination.delete()
                throw IOException("The selected .gba file is empty.")
            }

            Log.i(TAG, "Copied ROM into private storage: ${destination.absolutePath} ($copiedBytes bytes)")
            destination.absolutePath
        }.onFailure { error ->
            Log.w(TAG, "Failed to copy selected ROM into private storage.", error)
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) {
                    break
                }
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }
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

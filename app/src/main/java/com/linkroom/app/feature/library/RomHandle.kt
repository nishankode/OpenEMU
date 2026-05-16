package com.linkroom.app.feature.library

import android.net.Uri

data class RomHandle(
    val id: String,
    val uri: Uri,
    val filename: String,
    val localRomPath: String?,
    val gameRootPath: String?,
    val extension: String,
    val importedAtMillis: Long,
    val isAvailable: Boolean = true
)

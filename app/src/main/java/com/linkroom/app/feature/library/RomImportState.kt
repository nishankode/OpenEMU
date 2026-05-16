package com.linkroom.app.feature.library

data class RomImportState(
    val importedRoms: List<RomHandle> = emptyList(),
    val importError: String? = null
)

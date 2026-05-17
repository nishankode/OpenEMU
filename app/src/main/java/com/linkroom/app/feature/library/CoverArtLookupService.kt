package com.linkroom.app.feature.library

import android.content.Context
import android.util.Log
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.Normalizer
import java.util.Locale

class CoverArtLookupService(context: Context) {
    private val filesDir = context.applicationContext.filesDir

    fun lookupAndCacheCover(rom: RomHandle, forceRefresh: Boolean = false): CoverArtLookupResult {
        val artDirectory = File(filesDir, "games/${rom.id}/art")
        val cachedCover = File(artDirectory, BOXART_FILENAME)
        if (!forceRefresh && cachedCover.isFile && cachedCover.length() > 0L) {
            Log.i(TAG, "Using cached cover for ${rom.filename}: ${cachedCover.absolutePath}")
            return CoverArtLookupResult.Success(
                localPath = cachedCover.absolutePath,
                matchedFilename = cachedCover.name,
                candidates = generateCandidateFilenames(rom.filename)
            )
        }

        if (!artDirectory.exists() && !artDirectory.mkdirs()) {
            Log.w(TAG, "Unable to create cover cache directory: ${artDirectory.absolutePath}")
            return CoverArtLookupResult.NotFound(generateCandidateFilenames(rom.filename))
        }

        val candidates = generateCandidateFilenames(rom.filename)
        Log.i(TAG, "Cover lookup requested: original=${rom.filename}; candidates=$candidates")

        candidates.forEach { candidate ->
            val url = coverUrl(candidate)
            Log.i(TAG, "Attempting cover URL: $url")
            if (downloadCandidate(url, cachedCover)) {
                Log.i(
                    TAG,
                    "Cover matched: original=${rom.filename}; matched=$candidate; cached=${cachedCover.absolutePath}"
                )
                return CoverArtLookupResult.Success(
                    localPath = cachedCover.absolutePath,
                    matchedFilename = candidate,
                    candidates = candidates
                )
            }
        }

        val indexMatch = findIndexMatch(candidates)
        if (indexMatch != null) {
            val url = coverUrl(indexMatch)
            Log.i(TAG, "Attempting indexed cover URL: $url")
            if (downloadCandidate(url, cachedCover)) {
                Log.i(
                    TAG,
                    "Indexed cover matched: original=${rom.filename}; matched=$indexMatch; cached=${cachedCover.absolutePath}"
                )
                return CoverArtLookupResult.Success(
                    localPath = cachedCover.absolutePath,
                    matchedFilename = indexMatch,
                    candidates = candidates
                )
            }
        }

        cachedCover.delete()
        Log.i(TAG, "No confident cover match found for ${rom.filename}.")
        return CoverArtLookupResult.NotFound(candidates)
    }

    fun removeCover(rom: RomHandle): Boolean {
        val cachedCover = File(filesDir, "games/${rom.id}/art/$BOXART_FILENAME")
        val removed = !cachedCover.exists() || cachedCover.delete()
        Log.i(TAG, "Remove cover requested: rom=${rom.filename}; removed=$removed; path=${cachedCover.absolutePath}")
        return removed
    }

    private fun findIndexMatch(candidates: List<String>): String? {
        val candidateSet = candidates.map { normalizeForCompare(it) }.toSet()
        return runCatching {
            val connection = URL(BASE_URL).openConnection() as HttpURLConnection
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.requestMethod = "GET"
            connection.inputStream.bufferedReader().use { it.readText() }
        }.onFailure { error ->
            Log.w(TAG, "Unable to fetch Libretro thumbnail index.", error)
        }.getOrNull()
            ?.let(::parsePngLinks)
            ?.firstOrNull { filename -> normalizeForCompare(filename) in candidateSet }
    }

    private fun parsePngLinks(html: String): List<String> {
        return HREF_REGEX.findAll(html)
            .mapNotNull { match ->
                val href = match.groupValues[1]
                val decoded = URLDecoder.decode(href.substringAfterLast('/'), StandardCharsets.UTF_8.name())
                decoded.takeIf { it.endsWith(".png", ignoreCase = true) }
            }
            .toList()
    }

    private fun downloadCandidate(url: String, destination: File): Boolean {
        val temporaryDestination = File(destination.parentFile, "${destination.name}.tmp")
        return runCatching {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", USER_AGENT)

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.i(TAG, "Cover URL not available: url=$url; response=${connection.responseCode}")
                return@runCatching false
            }

            connection.inputStream.use { input ->
                temporaryDestination.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            if (temporaryDestination.length() <= 0L) {
                temporaryDestination.delete()
                return@runCatching false
            }
            temporaryDestination.copyTo(destination, overwrite = true)
            temporaryDestination.delete()
            true
        }.onFailure { error ->
            temporaryDestination.delete()
            Log.w(TAG, "Cover download failed: url=$url", error)
        }.getOrDefault(false)
    }

    private fun coverUrl(filename: String): String {
        val encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8.name())
            .replace("+", "%20")
        return BASE_URL + encodedFilename
    }

    companion object {
        private const val TAG = "CoverArtLookup"
        private const val BOXART_FILENAME = "boxart.png"
        private const val BASE_URL =
            "https://thumbnails.libretro.com/Nintendo%20-%20Game%20Boy%20Advance/Named_Boxarts/"
        private const val USER_AGENT = "LinkRoom/0.1 cover-art-lookup"
        private const val CONNECT_TIMEOUT_MS = 8_000
        private const val READ_TIMEOUT_MS = 10_000
        private val HREF_REGEX = Regex("href=\"([^\"]+\\.png)\"", RegexOption.IGNORE_CASE)

        fun generateCandidateFilenames(displayName: String): List<String> {
            val exactTitle = normalizeTitle(displayName.removeKnownExtension())
            val withoutDumpTags = normalizeTitle(removeDumpTags(exactTitle))
            val withoutRegionTags = normalizeTitle(removeUsefulRegionTags(withoutDumpTags))
            val candidates = mutableListOf<String>()

            candidates += exactTitle.toPngFilename()
            candidates += withoutDumpTags.toPngFilename()
            candidates += regionSplitCandidates(withoutDumpTags)
            candidates += withoutRegionTags.toPngFilename()

            return candidates
                .filter { it != ".png" }
                .distinct()
        }

        private fun regionSplitCandidates(title: String): List<String> {
            val matches = PAREN_TAG_REGEX.findAll(title).toList()
            return matches.flatMap { match ->
                val regions = match.groupValues[1]
                    .split(',')
                    .map { it.trim() }
                    .filter { it in USEFUL_REGIONS }
                    .sortedWith(compareBy { REGION_PRIORITY.indexOf(it).let { index -> if (index >= 0) index else Int.MAX_VALUE } })

                if (regions.size <= 1) {
                    emptyList()
                } else {
                    val prefix = normalizeTitle(title.removeRange(match.range))
                    regions.map { region -> "$prefix ($region)".toPngFilename() }
                }
            }
        }

        private fun removeDumpTags(title: String): String {
            return title
                .replace(BRACKET_DUMP_TAG_REGEX, " ")
                .replace(PAREN_DUMP_TAG_REGEX, " ")
        }

        private fun removeUsefulRegionTags(title: String): String {
            return PAREN_TAG_REGEX.replace(title) { match ->
                val parts = match.groupValues[1].split(',').map { it.trim() }
                if (parts.all { it in USEFUL_REGIONS }) " " else match.value
            }
        }

        private fun normalizeTitle(value: String): String {
            val withoutDiacritics = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "")
            return withoutDiacritics
                .replace(Regex("Pokemon", RegexOption.IGNORE_CASE), "Pokemon")
                .replace(Regex("\\s*[-\u2013\u2014]\\s*"), " - ")
                .replace(Regex("\\s+"), " ")
                .trim()
        }

        private fun normalizeForCompare(value: String): String {
            return normalizeTitle(value)
                .lowercase(Locale.US)
                .replace(Regex("\\s+"), " ")
        }

        private fun String.removeKnownExtension(): String {
            return replace(Regex("\\.(gba|zip)$", RegexOption.IGNORE_CASE), "")
        }

        private fun String.toPngFilename(): String = "$this.png"

        private val PAREN_TAG_REGEX = Regex("\\(([^)]*)\\)")
        private val PAREN_DUMP_TAG_REGEX =
            Regex("\\s*\\((Rev\\s*\\d+|Beta|Proto|Demo)\\)\\s*", RegexOption.IGNORE_CASE)
        private val BRACKET_DUMP_TAG_REGEX =
            Regex("\\s*\\[[^]]*([!]|\\bb\\b|\\bh\\b|\\bt\\b|\\bf\\b)[^]]*]\\s*", RegexOption.IGNORE_CASE)
        private val USEFUL_REGIONS = setOf("USA", "World", "Europe", "Japan")
        private val REGION_PRIORITY = listOf("USA", "World", "Europe", "Japan")
    }
}

sealed class CoverArtLookupResult {
    data class Success(
        val localPath: String,
        val matchedFilename: String,
        val candidates: List<String>
    ) : CoverArtLookupResult()

    data class NotFound(
        val candidates: List<String>
    ) : CoverArtLookupResult()
}

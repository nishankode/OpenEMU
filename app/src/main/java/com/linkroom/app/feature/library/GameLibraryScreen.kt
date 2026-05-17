package com.linkroom.app.feature.library

import android.graphics.BitmapFactory
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private enum class LibraryTab(val label: String) {
    All("All"),
    Recent("Recent"),
    Favorites("Favorites")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameLibraryScreen(
    state: RomImportState,
    onImportRom: (context: android.content.Context, uri: android.net.Uri) -> Unit,
    onImportCancelled: () -> Unit,
    onDismissImportError: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleFavorite: (String) -> Unit,
    onRefreshCoverArt: (String) -> Unit,
    onRemoveCoverArt: (String) -> Unit,
    onOpenRom: (RomHandle) -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(LibraryTab.All) }
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onImportRom(context, uri)
        } else {
            onImportCancelled()
        }
    }
    val launchPicker = {
        pickerLauncher.launch(
            arrayOf(
                "application/octet-stream",
                "application/zip",
                "*/*"
            )
        )
    }

    val allRoms = state.importedRoms
    val recentRoms = allRoms
        .filter { it.lastPlayedAt != null }
        .sortedByDescending { it.lastPlayedAt }
    val favoriteRoms = allRoms.filter { it.isFavorite }
    val featuredRom = recentRoms.firstOrNull() ?: allRoms.firstOrNull()
    val visibleRoms = when (selectedTab) {
        LibraryTab.All -> allRoms
        LibraryTab.Recent -> recentRoms
        LibraryTab.Favorites -> favoriteRoms
    }

    Scaffold(
        containerColor = LibraryBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "OpenEMU",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = { }) {
                        SearchGlyph()
                    }
                    IconButton(onClick = onOpenSettings) {
                        OverflowGlyph()
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = LibraryBackground,
                    titleContentColor = TextPrimary
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = launchPicker,
                containerColor = AccentBlue,
                contentColor = Color.White
            ) {
                Text("+ Import ROM", fontWeight = FontWeight.SemiBold)
            }
        },
        bottomBar = {
            LibraryBottomNavigation(
                hasPlayableRom = allRoms.any { it.isAvailable },
                onPlay = {
                    val romToPlay = recentRoms.firstOrNull { it.isAvailable }
                        ?: allRoms.firstOrNull { it.isAvailable }
                    if (romToPlay != null) {
                        onOpenRom(romToPlay)
                    }
                },
                onSettings = onOpenSettings
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                SummarySection(
                    totalGames = allRoms.size,
                    recentGames = recentRoms.size
                )
            }
            item {
                FeaturedGameCard(
                    rom = featuredRom,
                    onOpenRom = onOpenRom,
                    onToggleFavorite = onToggleFavorite,
                    onImport = launchPicker
                )
            }
            item {
                LibraryTabs(
                    selectedTab = selectedTab,
                    onSelectTab = { selectedTab = it }
                )
            }
            if (visibleRoms.isEmpty()) {
                item {
                    EmptyLibraryCard(
                        selectedTab = selectedTab,
                        onImport = launchPicker
                    )
                }
            } else {
                visibleRoms.chunked(2).forEach { rowItems ->
                    item(key = rowItems.joinToString { it.id }) {
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            rowItems.forEach { rom ->
                                GameTile(
                                    rom = rom,
                                    onOpenRom = onOpenRom,
                                    onToggleFavorite = onToggleFavorite,
                                    onRefreshCoverArt = onRefreshCoverArt,
                                    onRemoveCoverArt = onRemoveCoverArt,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (rowItems.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }

    if (state.importError != null) {
        AlertDialog(
            onDismissRequest = onDismissImportError,
            confirmButton = {
                TextButton(onClick = onDismissImportError) {
                    Text("OK")
                }
            },
            title = { Text("File not supported") },
            text = { Text(state.importError) }
        )
    }
}

@Composable
private fun SummarySection(totalGames: Int, recentGames: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        SummaryCard(
            label = "Total games",
            value = totalGames.toString(),
            modifier = Modifier.weight(1f)
        )
        SummaryCard(
            label = "Recently played",
            value = recentGames.toString(),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SummaryCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.heightIn(min = 92.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = label,
                color = TextSecondary,
                style = MaterialTheme.typography.labelLarge
            )
            Text(
                text = value,
                color = TextPrimary,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun FeaturedGameCard(
    rom: RomHandle?,
    onOpenRom: (RomHandle) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onImport: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardElevated),
        shape = RoundedCornerShape(18.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(HeroBrush)
                .padding(18.dp)
        ) {
            if (rom == null) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Build your handheld library",
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Import a legally obtained .gba file to start playing.",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(onClick = onImport) {
                        Text("Import ROM")
                    }
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GameArtwork(
                        rom = rom,
                        modifier = Modifier
                            .width(108.dp)
                            .aspectRatio(0.78f)
                            .clip(RoundedCornerShape(14.dp))
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Featured",
                            color = AccentBlue,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = rom.filename,
                            color = TextPrimary,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = rom.playSummary(),
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { onOpenRom(rom) },
                                enabled = rom.isAvailable
                            ) {
                                Text(if (rom.isAvailable) "Play" else "Missing")
                            }
                            FavoriteButton(
                                isFavorite = rom.isFavorite,
                                onClick = { onToggleFavorite(rom.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryTabs(selectedTab: LibraryTab, onSelectTab: (LibraryTab) -> Unit) {
    Surface(
        color = CardDark,
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            LibraryTab.entries.forEach { tab ->
                val selected = tab == selectedTab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) AccentBlue else Color.Transparent)
                        .clickable { onSelectTab(tab) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tab.label,
                        color = if (selected) Color.White else TextSecondary,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun GameTile(
    rom: RomHandle,
    onOpenRom: (RomHandle) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onRefreshCoverArt: (String) -> Unit,
    onRemoveCoverArt: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Card(
        modifier = modifier
            .heightIn(min = 248.dp)
            .clickable(enabled = rom.isAvailable) { onOpenRom(rom) },
        colors = CardDefaults.cardColors(containerColor = CardDark),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Box {
                GameArtwork(
                    rom = rom,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.82f)
                        .clip(RoundedCornerShape(12.dp))
                )
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    color = Color.Black.copy(alpha = 0.44f),
                    shape = CircleShape
                ) {
                    FavoriteButton(
                        isFavorite = rom.isFavorite,
                        onClick = { onToggleFavorite(rom.id) }
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.44f),
                        shape = CircleShape
                    ) {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            OverflowGlyph()
                        }
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (rom.isFavorite) "Remove favorite" else "Add favorite") },
                            onClick = {
                                menuExpanded = false
                                onToggleFavorite(rom.id)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Refresh cover art") },
                            onClick = {
                                menuExpanded = false
                                onRefreshCoverArt(rom.id)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Remove cover art") },
                            onClick = {
                                menuExpanded = false
                                onRemoveCoverArt(rom.id)
                            },
                            enabled = rom.coverImagePath != null
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = rom.filename,
                color = TextPrimary,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (rom.isAvailable) rom.playSummary() else "File missing",
                color = if (rom.isAvailable) TextSecondary else WarningOrange,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun GameArtwork(rom: RomHandle, modifier: Modifier = Modifier) {
    val imagePath = rom.coverImagePath?.takeIf { File(it).isFile }
        ?: rom.thumbnailImagePath?.takeIf { File(it).isFile }
    val bitmap = remember(imagePath) {
        imagePath?.let { BitmapFactory.decodeFile(it) }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else {
        FallbackArtwork(rom = rom, modifier = modifier)
    }
}

@Composable
private fun FallbackArtwork(rom: RomHandle, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(FallbackBrush),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = rom.extension.uppercase(Locale.US).take(3),
                    color = AccentBlue,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
            Text(
                text = "No cover",
                color = TextSecondary,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
private fun FavoriteButton(isFavorite: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .height(36.dp)
            .widthIn(min = 36.dp),
        contentPadding = PaddingValues(horizontal = 8.dp)
    ) {
        Text(
            text = if (isFavorite) "Fav" else "Add",
            color = if (isFavorite) AccentBlue else TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun EmptyLibraryCard(selectedTab: LibraryTab, onImport: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardDark),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = when (selectedTab) {
                    LibraryTab.All -> "No games imported"
                    LibraryTab.Recent -> "No recent games yet"
                    LibraryTab.Favorites -> "No favorites yet"
                },
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = when (selectedTab) {
                    LibraryTab.All -> "Import your own legally obtained .gba file to begin."
                    LibraryTab.Recent -> "Games appear here after you open them."
                    LibraryTab.Favorites -> "Tap the star on any game card to pin it here."
                },
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
            if (selectedTab == LibraryTab.All) {
                Button(onClick = onImport) {
                    Text("Import ROM")
                }
            }
        }
    }
}

@Composable
private fun LibraryBottomNavigation(
    hasPlayableRom: Boolean,
    onPlay: () -> Unit,
    onSettings: () -> Unit
) {
    NavigationBar(containerColor = Color(0xFF0C111D)) {
        NavigationBarItem(
            selected = true,
            onClick = { },
            icon = { Text("L") },
            label = { Text("Library") }
        )
        NavigationBarItem(
            selected = false,
            enabled = hasPlayableRom,
            onClick = onPlay,
            icon = { Text("P") },
            label = { Text("Play") }
        )
        NavigationBarItem(
            selected = false,
            onClick = onSettings,
            icon = { Text("S") },
            label = { Text("Settings") }
        )
    }
}

@Composable
private fun SearchGlyph() {
    Canvas(modifier = Modifier.size(22.dp)) {
        val strokeWidth = 2.dp.toPx()
        val radius = size.minDimension * 0.28f
        val center = androidx.compose.ui.geometry.Offset(
            x = size.width * 0.43f,
            y = size.height * 0.43f
        )
        drawCircle(
            color = TextSecondary,
            radius = radius,
            center = center,
            style = Stroke(width = strokeWidth)
        )
        drawLine(
            color = TextSecondary,
            start = androidx.compose.ui.geometry.Offset(
                x = size.width * 0.62f,
                y = size.height * 0.62f
            ),
            end = androidx.compose.ui.geometry.Offset(
                x = size.width * 0.84f,
                y = size.height * 0.84f
            ),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun OverflowGlyph() {
    Canvas(modifier = Modifier.size(22.dp)) {
        val radius = 2.dp.toPx()
        val x = size.width / 2f
        listOf(0.28f, 0.5f, 0.72f).forEach { yPercent ->
            drawCircle(
                color = TextSecondary,
                radius = radius,
                center = androidx.compose.ui.geometry.Offset(x, size.height * yPercent)
            )
        }
    }
}

private fun RomHandle.playSummary(): String {
    val lastPlayed = lastPlayedAt?.let { timestamp ->
        "Last played ${shortDate(timestamp)}"
    } ?: "Not played yet"
    val countText = when (playCount) {
        0 -> "0 plays"
        1 -> "1 play"
        else -> "$playCount plays"
    }
    return "$lastPlayed - $countText"
}

private fun shortDate(timestamp: Long): String {
    return SimpleDateFormat("MMM d", Locale.US).format(Date(timestamp))
}

private val LibraryBackground = Color(0xFF070B12)
private val CardDark = Color(0xFF111827)
private val CardElevated = Color(0xFF151F32)
private val AccentBlue = Color(0xFF3E9BFF)
private val TextPrimary = Color(0xFFF4F7FB)
private val TextSecondary = Color(0xFF98A6BA)
private val WarningOrange = Color(0xFFFFB86B)
private val HeroBrush = Brush.linearGradient(
    colors = listOf(
        Color(0xFF172842),
        Color(0xFF101724),
        Color(0xFF0D1320)
    )
)
private val FallbackBrush = Brush.linearGradient(
    colors = listOf(
        Color(0xFF1A2942),
        Color(0xFF101827)
    )
)

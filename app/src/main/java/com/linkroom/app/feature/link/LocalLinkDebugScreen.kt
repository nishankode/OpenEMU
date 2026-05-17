package com.linkroom.app.feature.link

import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.linkroom.app.feature.library.RomHandle
import com.linkroom.app.runtime.NativeEmulatorBridge
import com.linkroom.app.ui.theme.AppAccent
import com.linkroom.app.ui.theme.AppBackground
import com.linkroom.app.ui.theme.AppCard
import com.linkroom.app.ui.theme.AppDanger
import com.linkroom.app.ui.theme.AppSpacing
import com.linkroom.app.ui.theme.AppSuccess
import com.linkroom.app.ui.theme.AppSurface
import com.linkroom.app.ui.theme.AppTextMuted
import com.linkroom.app.ui.theme.AppTextPrimary
import com.linkroom.app.ui.theme.AppTextSecondary
import com.linkroom.app.ui.theme.AppWarning
import com.linkroom.app.ui.theme.StatusPill
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "LocalLinkDebugScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalLinkDebugScreen(
    importedRoms: List<RomHandle>,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val playableRoms = remember(importedRoms) {
        importedRoms.filter { rom ->
            rom.isAvailable && rom.localRomPath != null && rom.extension.equals("gba", ignoreCase = true)
        }
    }
    val baseTestDir = remember { File(context.filesDir, "link_tests").absolutePath }
    val slot1Root = remember(baseTestDir) { File(baseTestDir, "slot_1").absolutePath }
    val slot2Root = remember(baseTestDir) { File(baseTestDir, "slot_2").absolutePath }

    var selectedSlot1Id by remember { mutableStateOf<String?>(null) }
    var selectedSlot2Id by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf(NativeEmulatorBridge.getLocalLinkStatus()) }
    var starting by remember { mutableStateOf(false) }
    var startedAtMillis by remember { mutableLongStateOf(0L) }
    var clockTick by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }

    LaunchedEffect(playableRoms) {
        if (playableRoms.isEmpty()) {
            selectedSlot1Id = null
            selectedSlot2Id = null
            return@LaunchedEffect
        }
        if (selectedSlot1Id == null || playableRoms.none { it.id == selectedSlot1Id }) {
            selectedSlot1Id = playableRoms.first().id
        }
        if (selectedSlot2Id == null || playableRoms.none { it.id == selectedSlot2Id }) {
            selectedSlot2Id = (playableRoms.getOrNull(1) ?: playableRoms.first()).id
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            status = NativeEmulatorBridge.getLocalLinkStatus()
            clockTick = SystemClock.elapsedRealtime()
            delay(1000)
        }
    }

    val slot1Rom = playableRoms.firstOrNull { it.id == selectedSlot1Id }
    val slot2Rom = playableRoms.firstOrNull { it.id == selectedSlot2Id } ?: slot1Rom
    val phase = remember(status, starting) { derivePhase(status, starting) }
    val isRunning = phase == "scheduler running"
    val runtimeSeconds = if (startedAtMillis > 0L) {
        ((clockTick - startedAtMillis).coerceAtLeast(0L) / 1000L)
    } else {
        0L
    }

    Scaffold(
        containerColor = AppBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Local Link Debug",
                        color = AppTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppSurface)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
        ) {
            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Hidden two-core harness",
                            style = MaterialTheme.typography.titleMedium,
                            color = AppTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        StatusPill(
                            text = phase,
                            color = when (phase) {
                                "scheduler running", "lockstep attached" -> AppSuccess
                                "failed" -> AppDanger
                                "loading slot 1", "loading slot 2" -> AppWarning
                                else -> AppAccent
                            }
                        )
                    }
                    Text(
                        text = "Developer-only local link test. Slot 1 and Slot 2 run as separate mGBA cores with separate save roots. Rendering stays headless in this harness.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTextSecondary
                    )
                }
            }

            if (playableRoms.isEmpty()) {
                AppCard {
                    Text(
                        text = "Import at least one available .gba file before starting a local link test.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppTextSecondary
                    )
                }
            } else {
                AppCard {
                    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
                        RomDropdown(
                            label = "Slot 1 ROM",
                            selectedRom = slot1Rom,
                            roms = playableRoms,
                            onSelected = { selectedSlot1Id = it.id }
                        )
                        RomDropdown(
                            label = "Slot 2 ROM",
                            selectedRom = slot2Rom,
                            roms = playableRoms,
                            onSelected = { selectedSlot2Id = it.id }
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                enabled = !starting && slot1Rom?.localRomPath != null && slot2Rom?.localRomPath != null,
                                onClick = {
                                    val primaryPath = slot1Rom?.localRomPath
                                    val secondaryPath = slot2Rom?.localRomPath ?: primaryPath
                                    if (primaryPath == null || secondaryPath == null) {
                                        status = "local link failed: missing ROM path"
                                        return@Button
                                    }
                                    starting = true
                                    status = "loading slot 1"
                                    Log.i(TAG, "Starting local link debug: slot1=$primaryPath slot2=$secondaryPath base=$baseTestDir")
                                    scope.launch {
                                        status = withContext(Dispatchers.IO) {
                                            NativeEmulatorBridge.startLocalLinkTest(primaryPath, secondaryPath, baseTestDir)
                                        }
                                        starting = false
                                        if (status.contains("running", ignoreCase = true)) {
                                            startedAtMillis = SystemClock.elapsedRealtime()
                                        }
                                        Log.i(TAG, "Local link debug start result: $status")
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)
                            ) {
                                Text("Start Link Test")
                            }
                            OutlinedButton(
                                onClick = {
                                    Log.i(TAG, "Stopping local link debug")
                                    NativeEmulatorBridge.stopLocalLinkTest()
                                    status = NativeEmulatorBridge.getLocalLinkStatus()
                                    startedAtMillis = 0L
                                },
                                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)
                            ) {
                                Text("Stop")
                            }
                        }
                    }
                }
            }

            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                    Text(
                        text = "Live status",
                        style = MaterialTheme.typography.titleMedium,
                        color = AppTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    DetailRow("Runtime", formatDuration(runtimeSeconds))
                    DetailRow("Frame count", extractStatusValue(status, "slot1Frames") ?: "not available")
                    DetailRow("Slot 2 frames", extractStatusValue(status, "slot2Frames") ?: "not available")
                    DetailRow("SIO attached", extractStatusValue(status, "attached") ?: "not available")
                    DetailRow("Transfer phase", extractStatusValue(status, "transferPhase") ?: "not available")
                    DetailRow("Scheduler ticks", extractStatusValue(status, "ticks") ?: "not available")
                    DetailRow("Stall warning", if (status.contains("fell behind", ignoreCase = true)) "detected" else "none reported")
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (status.contains("failed", ignoreCase = true)) AppDanger else AppTextMuted
                    )
                }
            }

            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                    Text(
                        text = "Debug details",
                        style = MaterialTheme.typography.titleMedium,
                        color = AppTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    DetailRow("Slot 1 name", slot1Rom?.filename ?: "none")
                    DetailRow("Slot 1 path", slot1Rom?.localRomPath ?: "none")
                    DetailRow("Slot 1 save root", slot1Root)
                    DetailRow("Slot 2 name", slot2Rom?.filename ?: "none")
                    DetailRow("Slot 2 path", slot2Rom?.localRomPath ?: "none")
                    DetailRow("Slot 2 save root", slot2Root)
                    Text(
                        text = "Save-state controls are intentionally absent during local link debug tests.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun RomDropdown(
    label: String,
    selectedRom: RomHandle?,
    roms: List<RomHandle>,
    onSelected: (RomHandle) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = AppTextSecondary,
            fontWeight = FontWeight.SemiBold
        )
        Box {
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { expanded = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AppTextPrimary)
            ) {
                Text(
                    text = selectedRom?.filename ?: "Choose ROM",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                roms.forEach { rom ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = rom.filename,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        onClick = {
                            expanded = false
                            onSelected(rom)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            modifier = Modifier.weight(0.34f),
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = AppTextSecondary,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            modifier = Modifier.weight(0.66f),
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = AppTextPrimary
        )
    }
}

private fun derivePhase(status: String, starting: Boolean): String {
    if (starting) return "loading slot 2"
    return when {
        status.contains("failed", ignoreCase = true) -> "failed"
        status.contains("running", ignoreCase = true) && status.contains("scheduler=running", ignoreCase = true) -> "scheduler running"
        status.contains("lockstep attached", ignoreCase = true) -> "lockstep attached"
        status.contains("stopped", ignoreCase = true) -> "stopped"
        status.contains("idle", ignoreCase = true) -> "idle"
        else -> "idle"
    }
}

private fun extractStatusValue(status: String, key: String): String? {
    return Regex("""\b$key=([^\s]+)""").find(status)?.groupValues?.getOrNull(1)
}

private fun formatDuration(seconds: Long): String {
    val minutes = seconds / 60
    val remainder = seconds % 60
    return "${minutes}m ${remainder}s"
}

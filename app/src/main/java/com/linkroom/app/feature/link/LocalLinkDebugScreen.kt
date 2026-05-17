package com.linkroom.app.feature.link

import android.graphics.SurfaceTexture
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.linkroom.app.feature.library.RomHandle
import com.linkroom.app.runtime.NativeEmulatorBridge
import com.linkroom.app.ui.theme.AppAccent
import com.linkroom.app.ui.theme.AppBackground
import com.linkroom.app.ui.theme.AppBorder
import com.linkroom.app.ui.theme.AppCard
import com.linkroom.app.ui.theme.AppShapes
import com.linkroom.app.ui.theme.AppDanger
import com.linkroom.app.ui.theme.AppSpacing
import com.linkroom.app.ui.theme.AppSuccess
import com.linkroom.app.ui.theme.AppSurface
import com.linkroom.app.ui.theme.AppSurfaceHigh
import com.linkroom.app.ui.theme.AppTextMuted
import com.linkroom.app.ui.theme.AppTextPrimary
import com.linkroom.app.ui.theme.AppTextSecondary
import com.linkroom.app.ui.theme.AppWarning
import com.linkroom.app.ui.theme.StatusPill
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.atomic.AtomicReference
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
    val clipboardManager = LocalClipboardManager.current
    val lifecycleOwner = LocalLifecycleOwner.current
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
    var activeViewSlot by remember { mutableStateOf(1) }
    var activeInputSlot by remember { mutableStateOf(1) }
    var schedulerMode by remember { mutableStateOf(0) }
    var savePrepStatus by remember { mutableStateOf("Save prep: choose ROMs, then copy saves before starting.") }
    var saveInfoRefresh by remember { mutableLongStateOf(0L) }

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

    LaunchedEffect(activeViewSlot) {
        Log.i(TAG, "Switching local link video view to Slot $activeViewSlot")
        NativeEmulatorBridge.clearLocalLinkInput()
        NativeEmulatorBridge.setLocalLinkRenderSlot(activeViewSlot)
    }

    LaunchedEffect(activeInputSlot) {
        Log.i(TAG, "Switching local link input target to Slot $activeInputSlot")
        NativeEmulatorBridge.clearLocalLinkInput()
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
    val slot1MainSave = remember(slot1Rom?.gameRootPath, saveInfoRefresh) { mainSaveFile(slot1Rom) }
    val slot2MainSave = remember(slot2Rom?.gameRootPath, saveInfoRefresh) { mainSaveFile(slot2Rom) }
    val slot1LinkSave = remember(slot1Root, saveInfoRefresh) { linkSaveFile(slot1Root) }
    val slot2LinkSave = remember(slot2Root, saveInfoRefresh) { linkSaveFile(slot2Root) }

    fun refreshSaveInfo() {
        saveInfoRefresh = SystemClock.elapsedRealtime()
    }

    fun copyMainSaveToSlot(slot: Int): String {
        val source = if (slot == 1) slot1MainSave else slot2MainSave
        val destination = if (slot == 1) slot1LinkSave else slot2LinkSave
        if (source == null || !source.exists() || source.length() <= 0L) {
            return "Slot $slot copy skipped: main save is missing."
        }
        return runCatching {
            destination.parentFile?.mkdirs()
            source.copyTo(destination, overwrite = true)
            Log.i(TAG, "Copied main save to link slot $slot: source=${source.absolutePath} destination=${destination.absolutePath} size=${destination.length()}")
            "Slot $slot link save copied (${destination.length()} bytes)."
        }.getOrElse { error ->
            Log.w(TAG, "Unable to copy main save to slot $slot", error)
            "Slot $slot copy failed: ${error.javaClass.simpleName}"
        }.also {
            refreshSaveInfo()
        }
    }

    fun resetLinkSave(slot: Int): String {
        val target = if (slot == 1) slot1LinkSave else slot2LinkSave
        return runCatching {
            val existed = target.exists()
            val deleted = !existed || target.delete()
            Log.i(TAG, "Clear link save slot $slot: path=${target.absolutePath} existed=$existed deleted=$deleted")
            if (deleted) "Slot $slot link save cleared." else "Slot $slot clear failed: could not delete file."
        }.getOrElse { error ->
            Log.w(TAG, "Unable to clear link save slot $slot", error)
            "Slot $slot clear failed: ${error.javaClass.simpleName}"
        }.also {
            refreshSaveInfo()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    NativeEmulatorBridge.clearLocalLinkInput()
                    Log.i(TAG, "Local link debug paused; inputs cleared")
                }
                Lifecycle.Event.ON_DESTROY -> {
                    NativeEmulatorBridge.clearLocalLinkInput()
                    NativeEmulatorBridge.detachLocalLinkSurface()
                    NativeEmulatorBridge.stopLocalLinkTest()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            NativeEmulatorBridge.clearLocalLinkInput()
            NativeEmulatorBridge.detachLocalLinkSurface()
            NativeEmulatorBridge.stopLocalLinkTest()
        }
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
                        text = "Developer-only local link test. Toggle the view and control target while both slots remain connected to one lockstep session.",
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
                        SchedulerModeSelector(
                            selectedMode = schedulerMode,
                            enabled = !isRunning && !starting,
                            onSelected = { schedulerMode = it }
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
                                            NativeEmulatorBridge.startLocalLinkTest(
                                                primaryPath,
                                                secondaryPath,
                                                baseTestDir,
                                                schedulerMode
                                            )
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
                                    NativeEmulatorBridge.clearLocalLinkInput()
                                    NativeEmulatorBridge.stopLocalLinkTest()
                                    status = NativeEmulatorBridge.getLocalLinkStatus()
                                    startedAtMillis = 0L
                                    refreshSaveInfo()
                                },
                                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)
                            ) {
                                Text("Stop")
                            }
                        }
                    }
                }
            }

            AppCard(contentPadding = PaddingValues(10.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                    SlotToggleRow(
                        label = "Viewing",
                        selectedSlot = activeViewSlot,
                        onSelected = { slot ->
                            NativeEmulatorBridge.clearLocalLinkInput()
                            activeViewSlot = slot
                        }
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp)
                            .background(AppSurface, AppShapes.large)
                            .border(1.dp, AppBorder, AppShapes.large)
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        LocalLinkSlotSurface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(240f / 160f)
                                .background(Color.Black, RoundedCornerShape(10.dp))
                        )
                    }
                }
            }

            AppCard(contentPadding = PaddingValues(12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                    Text(
                        text = "Controls",
                        color = AppTextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    SlotToggleRow(
                        label = "Controls",
                        selectedSlot = activeInputSlot,
                        onSelected = { slot ->
                            NativeEmulatorBridge.clearLocalLinkInput()
                            activeInputSlot = slot
                        }
                    )
                    LocalLinkControlOverlay(enabled = isRunning, activeSlot = activeInputSlot)
                    Text(
                        text = "Controls route only to the active input slot. The other slot keeps running.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTextSecondary
                    )
                }
            }

            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                    Text(
                        text = "Link-test save preparation",
                        style = MaterialTheme.typography.titleMedium,
                        color = AppTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Main save is one protected source for the selected ROM. Slot 1 and Slot 2 link saves are independent debug copies.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTextSecondary
                    )
                    DetailRow("Slot 1 main save", saveFileSummary(slot1MainSave))
                    DetailRow("Slot 1 link save", saveFileSummary(slot1LinkSave))
                    DetailRow("Slot 2 main save", saveFileSummary(slot2MainSave))
                    DetailRow("Slot 2 link save", saveFileSummary(slot2LinkSave))
                    Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                        OutlinedButton(
                            enabled = !isRunning && slot1Rom != null,
                            onClick = { savePrepStatus = copyMainSaveToSlot(1) }
                        ) {
                            Text("Copy main save to Slot 1")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                        OutlinedButton(
                            enabled = !isRunning && slot2Rom != null,
                            onClick = { savePrepStatus = copyMainSaveToSlot(2) }
                        ) {
                            Text("Copy main save to Slot 2")
                        }
                    }
                    Text(
                        text = "Copying to both slots makes both players use the same save/profile.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppWarning
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                        OutlinedButton(
                            enabled = !isRunning && slot1Rom != null && slot2Rom != null,
                            onClick = {
                                val first = copyMainSaveToSlot(1)
                                val second = copyMainSaveToSlot(2)
                                savePrepStatus = "$first $second"
                            }
                        ) {
                            Text("Copy main save to both slots")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                        OutlinedButton(
                            enabled = !isRunning,
                            onClick = { savePrepStatus = resetLinkSave(1) }
                        ) {
                            Text("Clear Slot 1 link save")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                        OutlinedButton(
                            enabled = !isRunning,
                            onClick = { savePrepStatus = resetLinkSave(2) }
                        ) {
                            Text("Clear Slot 2 link save")
                        }
                    }
                    Text(
                        text = "To make a different Slot 2 player: clear Slot 2 link save, start the link test, switch Controls to Slot 2, create a new in-game profile, save in-game, then stop the test.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTextSecondary
                    )
                    Text(
                        text = savePrepStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTextSecondary
                    )
                }
            }

            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Link Activity",
                            style = MaterialTheme.typography.titleMedium,
                            color = AppTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(
                            onClick = {
                                clipboardManager.setText(
                                    AnnotatedString(
                                        buildDebugSnapshot(
                                            status,
                                            runtimeSeconds,
                                            activeViewSlot,
                                            activeInputSlot,
                                            schedulerMode
                                        )
                                    )
                                )
                            }
                        ) {
                            Text("Copy Debug Snapshot")
                        }
                    }
                    Text(
                        text = "Switch Viewing and Controls to guide both games toward the in-game link room, then watch for SIO mode and transfer activity.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTextSecondary
                    )
                    DetailRow("Runtime", formatDuration(runtimeSeconds))
                    DetailRow("Scheduler mode", schedulerModeLabel(extractStatusValue(status, "schedulerMode"), schedulerMode))
                    DetailRow("Active input", "Slot $activeInputSlot")
                    DetailRow("Active view", "Slot $activeViewSlot")
                    DetailRow("Scheduler ticks", extractStatusValue(status, "ticks") ?: "not available")
                    DetailRow("Scheduler tick rate", "${extractStatusValue(status, "tickRate") ?: "0"}/s")
                    DetailRow("Slot 1 frames", extractStatusValue(status, "slot1Frames") ?: "not available")
                    DetailRow("Slot 2 frames", extractStatusValue(status, "slot2Frames") ?: "not available")
                    DetailRow("Frame delta", extractStatusValue(status, "frameDelta") ?: "not available")
                    DetailRow("Slot 1 rendered", extractStatusValue(status, "slot1Rendered") ?: "not available")
                    DetailRow("Slot 2 rendered", extractStatusValue(status, "slot2Rendered") ?: "not available")
                    DetailRow("SIO attached", extractStatusValue(status, "attached") ?: "not available")
                    DetailRow("SIO mode 1", extractStatusValue(status, "sioMode1") ?: "not available")
                    DetailRow("SIO mode 2", extractStatusValue(status, "sioMode2") ?: "not available")
                    DetailRow("SIOCNT 1", extractStatusValue(status, "siocnt1") ?: "not available")
                    DetailRow("SIOCNT 2", extractStatusValue(status, "siocnt2") ?: "not available")
                    DetailRow("RCNT 1", extractStatusValue(status, "rcnt1") ?: "not available")
                    DetailRow("RCNT 2", extractStatusValue(status, "rcnt2") ?: "not available")
                    DetailRow("Active driver 1", extractStatusValue(status, "activeDriver1") ?: "not available")
                    DetailRow("Active driver 2", extractStatusValue(status, "activeDriver2") ?: "not available")
                    DetailRow("Transfer phase", extractStatusValue(status, "transferPhase") ?: "not available")
                    DetailRow("Transfer attempts", extractStatusValue(status, "transferAttempts") ?: "0")
                    DetailRow("Transfer completes", extractStatusValue(status, "transferCompletions") ?: "0")
                    DetailRow("Last transfer", "${extractStatusValue(status, "lastTransferMsAgo") ?: "0"} ms ago")
                    DetailRow("Lockstep signals", extractStatusValue(status, "lockstepSignals") ?: "0")
                    DetailRow("Lockstep waits", extractStatusValue(status, "lockstepWaits") ?: "0")
                    DetailRow("Signal rate", "${extractStatusValue(status, "signalRate") ?: "0"}/s")
                    DetailRow("Wait rate", "${extractStatusValue(status, "waitRate") ?: "0"}/s")
                    DetailRow("Slices last tick", extractStatusValue(status, "slicesLastTick") ?: "not available")
                    DetailRow("Slice cap hits", extractStatusValue(status, "sliceLimitHits") ?: "0")
                    DetailRow("Link warning", extractStatusValue(status, "linkWarning") ?: "none")
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
                        text = "Test guidance",
                        style = MaterialTheme.typography.titleMedium,
                        color = AppTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "1. Copy the main save into each link slot before starting. 2. Start Link Test. 3. Switch Viewing and Controls to move each slot into the in-game link room. 4. Watch Link Activity for SIO mode changes or transfer attempts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTextSecondary
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
private fun LocalLinkSlotSurface(modifier: Modifier = Modifier) {
    val textureViewRef = remember { AtomicReference<TextureView?>(null) }
    val attachedSurfaceRef = remember { AtomicReference<Surface?>(null) }
    val listener = remember {
        object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
                Log.i(TAG, "Local link surface available: $width x $height")
                val surface = Surface(surfaceTexture)
                attachedSurfaceRef.getAndSet(surface)?.release()
                NativeEmulatorBridge.attachLocalLinkSurface(surface)
                NativeEmulatorBridge.resizeLocalLinkSurface(width, height)
            }

            override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
                Log.i(TAG, "Local link surface changed: $width x $height")
                NativeEmulatorBridge.resizeLocalLinkSurface(width, height)
            }

            override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                Log.i(TAG, "Local link surface destroyed")
                NativeEmulatorBridge.detachLocalLinkSurface()
                attachedSurfaceRef.getAndSet(null)?.release()
                return true
            }

            override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) = Unit
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            TextureView(context).apply {
                surfaceTextureListener = listener
                textureViewRef.set(this)
            }
        }
    )

    DisposableEffect(listener) {
        onDispose {
            textureViewRef.getAndSet(null)?.surfaceTextureListener = null
            NativeEmulatorBridge.detachLocalLinkSurface()
            attachedSurfaceRef.getAndSet(null)?.release()
        }
    }
}

@Composable
private fun SlotToggleRow(
    label: String,
    selectedSlot: Int,
    onSelected: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = label,
            color = AppTextSecondary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
        for (slot in 1..2) {
            OutlinedButton(onClick = { onSelected(slot) }) {
                Text(if (selectedSlot == slot) "Slot $slot*" else "Slot $slot")
            }
        }
    }
}

@Composable
private fun SchedulerModeSelector(
    selectedMode: Int,
    enabled: Boolean,
    onSelected: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
        Text(
            text = "Scheduler mode",
            style = MaterialTheme.typography.labelLarge,
            color = AppTextSecondary,
            fontWeight = FontWeight.SemiBold
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                modifier = Modifier.weight(1f),
                enabled = enabled,
                onClick = { onSelected(0) }
            ) {
                Text(if (selectedMode == 0) "Stable*" else "Stable")
            }
            OutlinedButton(
                modifier = Modifier.weight(1f),
                enabled = enabled,
                onClick = { onSelected(1) }
            ) {
                Text(if (selectedMode == 1) "Experimental*" else "Experimental")
            }
        }
        Text(
            text = if (selectedMode == 0) {
                "Stable uses the previous playable two-core frame scheduler."
            } else {
                "Experimental uses smaller lockstep slices for timing diagnostics and may run slower."
            },
            style = MaterialTheme.typography.bodySmall,
            color = AppTextSecondary
        )
        if (!enabled) {
            Text(
                text = "Stop the link test before changing scheduler mode.",
                style = MaterialTheme.typography.bodySmall,
                color = AppWarning
            )
        }
    }
}

@Composable
private fun LocalLinkControlOverlay(enabled: Boolean, activeSlot: Int) {
    var pressedButtons by remember { mutableStateOf<Set<Int>>(emptySet()) }

    LaunchedEffect(enabled, activeSlot) {
        pressedButtons = emptySet()
        NativeEmulatorBridge.clearLocalLinkInput()
    }

    fun setPressed(bit: Int, pressed: Boolean) {
        val updatedButtons = if (pressed) {
            pressedButtons + bit
        } else {
            pressedButtons - bit
        }
        pressedButtons = updatedButtons
        val inputMask = if (enabled) updatedButtons.fold(0) { mask, button -> mask or button } else 0
        Log.d(TAG, "Slot $activeSlot input mask: 0x${inputMask.toString(16)}")
        NativeEmulatorBridge.setLocalLinkInput(activeSlot, inputMask)
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LocalLinkDPad(enabled = enabled, activeSlot = activeSlot, onPressedChange = ::setPressed)
            LocalLinkShoulderAndFaceButtons(enabled = enabled, activeSlot = activeSlot, onPressedChange = ::setPressed)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LocalLinkControlButton("Select", INPUT_SELECT, enabled, activeSlot, ::setPressed, wide = true)
            Spacer(modifier = Modifier.width(16.dp))
            LocalLinkControlButton("Start", INPUT_START, enabled, activeSlot, ::setPressed, wide = true)
        }
    }
}

@Composable
private fun LocalLinkDPad(
    enabled: Boolean,
    activeSlot: Int,
    onPressedChange: (Int, Boolean) -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        LocalLinkControlButton("Up", INPUT_UP, enabled, activeSlot, onPressedChange)
        Row {
            LocalLinkControlButton("Left", INPUT_LEFT, enabled, activeSlot, onPressedChange)
            Spacer(modifier = Modifier.width(36.dp))
            LocalLinkControlButton("Right", INPUT_RIGHT, enabled, activeSlot, onPressedChange)
        }
        LocalLinkControlButton("Down", INPUT_DOWN, enabled, activeSlot, onPressedChange)
    }
}

@Composable
private fun LocalLinkShoulderAndFaceButtons(
    enabled: Boolean,
    activeSlot: Int,
    onPressedChange: (Int, Boolean) -> Unit
) {
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LocalLinkControlButton("L", INPUT_L, enabled, activeSlot, onPressedChange)
            LocalLinkControlButton("R", INPUT_R, enabled, activeSlot, onPressedChange)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            LocalLinkControlButton("B", INPUT_B, enabled, activeSlot, onPressedChange)
            LocalLinkControlButton("A", INPUT_A, enabled, activeSlot, onPressedChange)
        }
    }
}

@Composable
private fun LocalLinkControlButton(
    label: String,
    bit: Int,
    enabled: Boolean,
    activeSlot: Int,
    onPressedChange: (Int, Boolean) -> Unit,
    wide: Boolean = false
) {
    val isFaceButton = label == "A" || label == "B"
    Box(
        modifier = Modifier
            .then(if (wide) Modifier.width(84.dp).height(40.dp) else Modifier.size(48.dp))
            .background(
                color = when {
                    !enabled -> AppSurfaceHigh.copy(alpha = 0.46f)
                    isFaceButton -> AppAccent.copy(alpha = 0.24f)
                    else -> AppSurfaceHigh
                },
                shape = if (wide) RoundedCornerShape(24.dp) else CircleShape
            )
            .border(
                width = 1.dp,
                color = if (isFaceButton) AppAccent.copy(alpha = 0.68f) else AppBorder,
                shape = if (wide) RoundedCornerShape(24.dp) else CircleShape
            )
            .pointerInput(bit, enabled, activeSlot) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (enabled) {
                        onPressedChange(bit, true)
                    }
                    try {
                        var stillPressed: Boolean
                        do {
                            val event = awaitPointerEvent()
                            stillPressed = event.changes.any { it.id == down.id && it.pressed }
                        } while (stillPressed)
                    } finally {
                        onPressedChange(bit, false)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = AppTextPrimary)
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

private fun mainSaveFile(rom: RomHandle?): File? {
    val root = rom?.gameRootPath ?: return null
    return File(File(root, "battery"), "current.sav")
}

private fun linkSaveFile(slotRoot: String): File {
    return File(File(slotRoot, "battery"), "current.sav")
}

private fun saveFileSummary(file: File?): String {
    if (file == null) {
        return "missing: no ROM save root"
    }
    return if (file.exists()) {
        val modifiedAt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            .format(Date(file.lastModified()))
        "exists (${file.length()} bytes), modified $modifiedAt - ${file.absolutePath}"
    } else {
        "missing - ${file.absolutePath}"
    }
}

private fun buildDebugSnapshot(
    status: String,
    runtimeSeconds: Long,
    activeViewSlot: Int,
    activeInputSlot: Int,
    selectedSchedulerMode: Int
): String {
    return buildString {
        appendLine("Local Link Debug Snapshot")
        appendLine("Runtime: ${formatDuration(runtimeSeconds)}")
        appendLine("Scheduler: ${schedulerModeLabel(extractStatusValue(status, "schedulerMode"), selectedSchedulerMode)}")
        appendLine("Viewing: Slot $activeViewSlot")
        appendLine("Controls: Slot $activeInputSlot")
        appendLine("Status: $status")
    }
}

private fun schedulerModeLabel(statusValue: String?, selectedMode: Int): String {
    return when (statusValue ?: if (selectedMode == 1) "experimental_lockstep" else "stable") {
        "experimental_lockstep" -> "Experimental lockstep"
        else -> "Stable"
    }
}

private const val INPUT_A = 1 shl 0
private const val INPUT_B = 1 shl 1
private const val INPUT_SELECT = 1 shl 2
private const val INPUT_START = 1 shl 3
private const val INPUT_RIGHT = 1 shl 4
private const val INPUT_LEFT = 1 shl 5
private const val INPUT_UP = 1 shl 6
private const val INPUT_DOWN = 1 shl 7
private const val INPUT_R = 1 shl 8
private const val INPUT_L = 1 shl 9

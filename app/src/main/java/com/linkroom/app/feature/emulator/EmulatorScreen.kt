package com.linkroom.app.feature.emulator

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.linkroom.app.feature.library.RomHandle
import com.linkroom.app.runtime.EmulatorRuntime
import android.util.Log
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmulatorScreen(
    rom: RomHandle?,
    onBack: () -> Unit
) {
    val runtime = remember { EmulatorRuntime() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    var bootStatus by remember(rom?.id) {
        mutableStateOf("loading: waiting for ROM load")
    }
    var saveStatus by remember(rom?.id) {
        mutableStateOf("save: waiting for ROM load")
    }
    var audioStatus by remember(rom?.id) {
        mutableStateOf("audio: waiting for ROM load")
    }

    DisposableEffect(runtime, rom?.id) {
        bootStatus = "loading: preparing emulator runtime"
        bootStatus = when {
            rom == null -> "No ROM selected."
            !rom.isAvailable -> "failed: copied ROM file is missing from private app storage"
            rom.localRomPath == null -> "Native boot supports copied .gba files only in this phase. ZIP import is library-only for now."
            rom.gameRootPath == null -> "failed: save directory is unavailable for this ROM"
            else -> {
                val batteryDirectory = File(rom.gameRootPath, "battery")
                val batterySaveFile = File(batteryDirectory, "current.sav")
                Log.i(
                    TAG,
                    "Opening ROM id=${rom.id}; name=${rom.filename}; privatePath=${rom.localRomPath}; appFilesDir=${context.filesDir.absolutePath}; expectedSaveDir=${batteryDirectory.absolutePath}; expectedSaveFile=${batterySaveFile.absolutePath}; saveExistsBeforeBoot=${batterySaveFile.exists()}; saveSizeBeforeBoot=${batterySaveFile.length()}"
                )
                runtime.loadRom(rom.localRomPath, rom.gameRootPath)
            }
        }
        saveStatus = runtime.saveStatusMessage
        audioStatus = runtime.audioStatusMessage
        onDispose { }
    }

    DisposableEffect(runtime) {
        onDispose {
            runtime.release()
        }
    }

    DisposableEffect(lifecycleOwner, runtime) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    bootStatus = runtime.resume()
                    audioStatus = runtime.audioStatusMessage
                }
                Lifecycle.Event.ON_PAUSE -> {
                    bootStatus = runtime.pause()
                    saveStatus = runtime.saveStatusMessage
                    audioStatus = runtime.audioStatusMessage
                }
                Lifecycle.Event.ON_DESTROY -> {
                    runtime.release()
                    bootStatus = "released: emulator runtime resources released"
                    saveStatus = runtime.saveStatusMessage
                    audioStatus = runtime.audioStatusMessage
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Player") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(20.dp),
            verticalArrangement = Arrangement.Top
        ) {
            if (rom == null) {
                MissingRomContent(onBack = onBack)
                return@Column
            }

            Text(
                text = rom.filename,
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 2f)
                    .background(Color.Black)
            ) {
                EmulatorSurface(
                    runtime = runtime,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = runtime.nativeStatusMessage,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "mGBA runtime status: $bootStatus",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Battery save status: $saveStatus",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Audio status: $audioStatus",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Video and basic audio run after a .gba loads. Placeholder rendering remains the fallback for empty or failed loads.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
            GbaControlOverlay(runtime = runtime)
        }
    }
}

@Composable
private fun GbaControlOverlay(runtime: EmulatorRuntime) {
    var pressedButtons by remember { mutableStateOf<Set<Int>>(emptySet()) }

    fun setPressed(bit: Int, pressed: Boolean) {
        val updatedButtons = if (pressed) {
            pressedButtons + bit
        } else {
            pressedButtons - bit
        }
        pressedButtons = updatedButtons
        runtime.setInputMask(updatedButtons.fold(0) { mask, button -> mask or button })
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            DPad(onPressedChange = ::setPressed)
            ShoulderAndFaceButtons(onPressedChange = ::setPressed)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ControlButton(label = "Select", bit = INPUT_SELECT, onPressedChange = ::setPressed, wide = true)
            Spacer(modifier = Modifier.width(16.dp))
            ControlButton(label = "Start", bit = INPUT_START, onPressedChange = ::setPressed, wide = true)
        }
    }
}

@Composable
private fun DPad(onPressedChange: (Int, Boolean) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        ControlButton(label = "Up", bit = INPUT_UP, onPressedChange = onPressedChange)
        Row {
            ControlButton(label = "Left", bit = INPUT_LEFT, onPressedChange = onPressedChange)
            Spacer(modifier = Modifier.width(48.dp))
            ControlButton(label = "Right", bit = INPUT_RIGHT, onPressedChange = onPressedChange)
        }
        ControlButton(label = "Down", bit = INPUT_DOWN, onPressedChange = onPressedChange)
    }
}

@Composable
private fun ShoulderAndFaceButtons(onPressedChange: (Int, Boolean) -> Unit) {
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ControlButton(label = "L", bit = INPUT_L, onPressedChange = onPressedChange)
            ControlButton(label = "R", bit = INPUT_R, onPressedChange = onPressedChange)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ControlButton(label = "B", bit = INPUT_B, onPressedChange = onPressedChange)
            ControlButton(label = "A", bit = INPUT_A, onPressedChange = onPressedChange)
        }
    }
}

@Composable
private fun ControlButton(
    label: String,
    bit: Int,
    onPressedChange: (Int, Boolean) -> Unit,
    wide: Boolean = false
) {
    Box(
        modifier = Modifier
            .then(if (wide) Modifier.width(84.dp).height(42.dp) else Modifier.size(52.dp))
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = if (wide) RoundedCornerShape(24.dp) else CircleShape
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = if (wide) RoundedCornerShape(24.dp) else CircleShape
            )
            .pointerInput(bit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    onPressedChange(bit, true)
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
        Text(text = label, style = MaterialTheme.typography.labelLarge)
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
private const val TAG = "EmulatorScreen"

@Composable
private fun MissingRomContent(onBack: () -> Unit) {
    Text(
        text = "Selected file is no longer available",
        style = MaterialTheme.typography.titleLarge
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "Return to the library and select an available imported file.",
        style = MaterialTheme.typography.bodyMedium
    )
    Spacer(modifier = Modifier.height(16.dp))
    OutlinedButton(onClick = onBack) {
        Text("Back to library")
    }
}

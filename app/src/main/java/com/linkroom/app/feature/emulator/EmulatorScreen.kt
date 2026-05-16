package com.linkroom.app.feature.emulator

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.linkroom.app.feature.library.RomHandle
import com.linkroom.app.runtime.EmulatorRuntime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmulatorScreen(
    rom: RomHandle?,
    onBack: () -> Unit
) {
    val runtime = remember { EmulatorRuntime() }
    val lifecycleOwner = LocalLifecycleOwner.current
    var bootStatus by remember(rom?.id) {
        mutableStateOf("Waiting for ROM load.")
    }

    DisposableEffect(runtime, rom?.id) {
        bootStatus = when {
            rom == null -> "No ROM selected."
            rom.localRomPath == null -> "Native boot supports copied .gba files only in this phase. ZIP import is library-only for now."
            else -> runtime.loadRom(rom.localRomPath)
        }
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
                Lifecycle.Event.ON_RESUME -> runtime.resume()
                Lifecycle.Event.ON_PAUSE -> runtime.pause()
                Lifecycle.Event.ON_DESTROY -> runtime.release()
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
                text = "mGBA boot status: $bootStatus",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Placeholder renderer remains active; mGBA video frames are not displayed yet.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun MissingRomContent(onBack: () -> Unit) {
    Text(
        text = "Selected file is no longer available",
        style = MaterialTheme.typography.titleLarge
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "Phase 0 keeps imports in memory only. Return to the library and select the file again.",
        style = MaterialTheme.typography.bodyMedium
    )
    Spacer(modifier = Modifier.height(16.dp))
    OutlinedButton(onClick = onBack) {
        Text("Back to library")
    }
}

package com.linkroom.app.feature.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameLibraryScreen(
    state: RomImportState,
    onImportRom: (context: android.content.Context, uri: android.net.Uri) -> Unit,
    onImportCancelled: () -> Unit,
    onDismissImportError: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenRom: (RomHandle) -> Unit
) {
    val context = LocalContext.current
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onImportRom(context, uri)
        } else {
            onImportCancelled()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Library") },
                actions = {
                    TextButton(onClick = onOpenSettings) {
                        Text("Settings")
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
            Text(
                text = "Import your own legally obtained game file to begin.",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Imported .gba files are copied into private app storage. Game progress saving is not implemented yet.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    pickerLauncher.launch(
                        arrayOf(
                            "application/octet-stream",
                            "application/zip",
                            "*/*"
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Import .gba or .zip")
            }
            Spacer(modifier = Modifier.height(24.dp))

            if (state.importedRoms.isEmpty()) {
                EmptyLibrary()
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(state.importedRoms, key = { it.id }) { rom ->
                        RomRow(rom = rom, onClick = { onOpenRom(rom) })
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
private fun EmptyLibrary() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp)
    ) {
        Text(
            text = "No games imported",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Import a .gba file to keep it in this device's private app storage.",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun RomRow(rom: RomHandle, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = rom.isAvailable,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(rom.filename)
            Text(if (rom.isAvailable) "Open" else "Missing")
        }
    }
}

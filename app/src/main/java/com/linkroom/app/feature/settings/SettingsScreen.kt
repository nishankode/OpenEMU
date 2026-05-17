package com.linkroom.app.feature.settings

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.linkroom.app.ui.theme.AppAccent
import com.linkroom.app.ui.theme.AppBackground
import com.linkroom.app.ui.theme.AppCard
import com.linkroom.app.ui.theme.AppSpacing
import com.linkroom.app.ui.theme.AppSuccess
import com.linkroom.app.ui.theme.AppSurface
import com.linkroom.app.ui.theme.AppTextPrimary
import com.linkroom.app.ui.theme.AppTextSecondary
import com.linkroom.app.ui.theme.StatusPill

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenLocalLinkDebug: () -> Unit = {}
) {
    var localLinkUnlockCount by remember { mutableIntStateOf(0) }

    Scaffold(
        containerColor = AppBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
        ) {
            item {
                SettingsSection(title = "Library") {
                    SettingRow("ROM import", "Import .gba files through Android file picker.", "Ready")
                    SettingRow("Cover art", "Automatic Libretro lookup with local cache.", "Ready")
                    SettingRow("Persistence", "Library metadata is restored on app launch.", "Ready")
                }
            }
            item {
                SettingsSection(title = "Emulator") {
                    SettingRow("Core", "mGBA native core is integrated through the runtime boundary.", "Ready")
                    SettingRow("Video", "Software frames render to the Android surface.", "Ready")
                    SettingRow("Controls", "Touch controls map to native input.", "Ready")
                    SettingRow("Fast-forward", "2x mode is supported; audio is muted during fast-forward.", "2x")
                }
            }
            item {
                SettingsSection(title = "Audio") {
                    SettingRow("Output", "Basic game audio is enabled during normal gameplay.", "Ready")
                }
            }
            item {
                SettingsSection(title = "Saves") {
                    SettingRow("Battery saves", "Normal in-game saves are stored per imported ROM.", "Ready")
                    SettingRow("Save states", "Three manual slots are available on the player screen.", "Ready")
                }
            }
            item {
                SettingsSection(title = "About") {
                    Text(
                        text = "No games are included. Users must import their own legally obtained files. This app is not affiliated with any console manufacturer or game publisher.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppTextSecondary
                    )
                    SettingRow("Native status", "mGBA linked through the native runtime.", "Core")
                    SettingRow(
                        title = "Version",
                        body = "0.1.0",
                        status = "Debug",
                        modifier = Modifier.pointerInput(Unit) {
                            detectTapGestures(
                                onLongPress = {
                                    localLinkUnlockCount = (localLinkUnlockCount + 1).coerceAtMost(7)
                                }
                            )
                        }
                    )
                    if (localLinkUnlockCount >= 7) {
                        TextButton(onClick = onOpenLocalLinkDebug) {
                            Text("Open Local Link Debug")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = AppTextPrimary,
                fontWeight = FontWeight.Bold
            )
            content()
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    body: String,
    status: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = AppTextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = AppTextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        StatusPill(text = status, color = AppSuccess.takeIf { status == "Ready" } ?: AppAccent)
    }
}

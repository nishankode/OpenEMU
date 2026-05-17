package com.linkroom.app.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.linkroom.app.ui.theme.AppAccent
import com.linkroom.app.ui.theme.AppBackground
import com.linkroom.app.ui.theme.AppCard
import com.linkroom.app.ui.theme.AppHeroBrush
import com.linkroom.app.ui.theme.AppSpacing
import com.linkroom.app.ui.theme.AppTextPrimary
import com.linkroom.app.ui.theme.AppTextSecondary
import com.linkroom.app.ui.theme.StatusPill

@Composable
fun OnboardingScreen(onContinue: () -> Unit) {
    Scaffold(containerColor = AppBackground) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.lg)
        ) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AppHeroBrush, shape = MaterialTheme.shapes.large)
                        .padding(24.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
                        StatusPill(text = "Local emulator", color = AppAccent, filled = true)
                        Text(
                            text = "LinkRoom",
                            style = MaterialTheme.typography.displaySmall,
                            color = AppTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "A polished Android emulator for your own legally obtained handheld game files.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = AppTextSecondary
                        )
                    }
                }
            }
            item {
                AppCard {
                    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
                        OnboardingPoint(
                            title = "No games included",
                            body = "The app does not include ROMs, downloads, or copyrighted game assets."
                        )
                        OnboardingPoint(
                            title = "Import your own files",
                            body = "Use Android's file picker to import legally obtained .gba files into private app storage."
                        )
                        OnboardingPoint(
                            title = "Local saves and covers",
                            body = "Battery saves, save states, and downloaded cover thumbnails are stored locally on this device."
                        )
                    }
                }
            }
            item {
                Button(
                    onClick = onContinue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text("Continue", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(AppSpacing.sm))
                Text(
                    text = "This app is not affiliated with any console manufacturer or game publisher.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun OnboardingPoint(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = AppTextPrimary,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = AppTextSecondary
        )
    }
}

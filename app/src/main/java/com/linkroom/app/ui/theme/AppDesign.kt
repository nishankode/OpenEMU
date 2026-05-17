package com.linkroom.app.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object AppSpacing {
    val xxs: Dp = 4.dp
    val xs: Dp = 8.dp
    val sm: Dp = 12.dp
    val md: Dp = 16.dp
    val lg: Dp = 20.dp
    val xl: Dp = 24.dp
}

object AppShapes {
    val small = RoundedCornerShape(10.dp)
    val medium = RoundedCornerShape(14.dp)
    val large = RoundedCornerShape(18.dp)
    val pill = RoundedCornerShape(999.dp)
}

val AppHeroBrush = Brush.linearGradient(
    listOf(
        Color(0xFF182B48),
        Color(0xFF111827),
        Color(0xFF0B111D)
    )
)

val AppCoverFallbackBrush = Brush.linearGradient(
    listOf(
        Color(0xFF203352),
        Color(0xFF111A2C),
        Color(0xFF0D1320)
    )
)

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(AppSpacing.md),
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier,
        shape = AppShapes.medium,
        colors = CardDefaults.cardColors(containerColor = AppSurfaceHigh),
        border = BorderStroke(1.dp, AppBorder.copy(alpha = 0.72f))
    ) {
        Box(modifier = Modifier.padding(contentPadding)) {
            content()
        }
    }
}

@Composable
fun StatusPill(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = AppAccent,
    filled: Boolean = false
) {
    val background = if (filled) color.copy(alpha = 0.18f) else AppSurfaceHigher.copy(alpha = 0.78f)
    Box(
        modifier = modifier
            .background(background, AppShapes.pill)
            .border(1.dp, color.copy(alpha = 0.32f), AppShapes.pill)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            color = if (filled) AppTextPrimary else AppTextSecondary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

package com.nightread.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean,
    shape: Shape = RoundedCornerShape(16.dp),
    elevation: Dp = if (isDarkTheme) 0.dp else 4.dp,
    content: @Composable () -> Unit
) {
    // Анимация цвета фона стекла
    val targetBackgroundColor = if (isDarkTheme) {
        Color(0x991A1A2E) // Dark: 60% opacity dark violet (#1A1A2E)
    } else {
        Color(0xB3FFFFFF) // Light: 70% opacity white (#FFFFFF)
    }
    val backgroundColor by animateColorAsState(
        targetValue = targetBackgroundColor,
        animationSpec = tween(durationMillis = 500),
        label = "glass_bg_color"
    )

    // Анимация цвета обводки
    val targetBorderColor = if (isDarkTheme) {
        Color(0x33FFFFFF) // Dark: White stroke with 20% opacity
    } else {
        Color(0x99FFFFFF) // Light: White stroke with 60% opacity (or light gray)
    }
    val borderColor by animateColorAsState(
        targetValue = targetBorderColor,
        animationSpec = tween(durationMillis = 500),
        label = "glass_border_color"
    )

    // Для светлой темы используем тень, для темной нет, но Elevation в Surface
    // может не поддерживать размытие фона, поэтому мы комбинируем эффекты.
    
    val currentElevation = if (isDarkTheme) 0.dp else elevation

    Surface(
        modifier = modifier.shadow(
            elevation = currentElevation,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = 0.05f),
            spotColor = Color.Black.copy(alpha = 0.1f)
        ),
        shape = shape,
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor),
        contentColor = if (isDarkTheme) Color.White else Color(0xFF1A1A1A)
    ) {
        Box(
            modifier = Modifier
                // Опционально можно добавить Modifier.blur() для SDK 31+, 
                // если нужно реальное размытие (на Android это часто делают через RenderEffect)
                .clip(shape)
        ) {
            content()
        }
    }
}

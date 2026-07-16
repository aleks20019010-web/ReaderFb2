package com.nightread.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.nightread.app.data.YandexSyncState
import kotlinx.coroutines.delay
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun SyncAnimationScreen(state: YandexSyncState) {
    val infiniteTransition = rememberInfiniteTransition(label = "SyncInfinite")
    
    // Gradient colors matching the app's style (Night/Cosmic theme)
    val colorAccent = Color(0xFF00BFA5) // Teal accent
    val colorPurple = Color(0xFF7C4DFF) // Purple accent
    val colorGold = Color(0xFFFFD600)   // Gold accent
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp),
        contentAlignment = Alignment.Center
    ) {
        // Background Glow
        val glowScale by infiniteTransition.animateFloat(
            initialValue = 0.8f,
            targetValue = 1.2f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = LinearOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "GlowScale"
        )
        
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(colorPurple.copy(alpha = 0.15f), Color.Transparent),
                    center = center,
                    radius = 120.dp.toPx() * glowScale
                ),
                center = center,
                radius = 120.dp.toPx() * glowScale
            )
        }

        // The Central Object (Cloud/Planet)
        CentralIcon(colorPurple, infiniteTransition)

        // Flying Books
        when (state.stage) {
            YandexSyncState.Stage.SCANNING -> ScanningAnimation(colorAccent, colorGold)
            YandexSyncState.Stage.DOWNLOADING -> FlightAnimation(direction = -1, color = colorAccent)
            YandexSyncState.Stage.UPLOADING -> FlightAnimation(direction = 1, color = colorGold)
            else -> if (state.isRunning) {
                ScanningAnimation(colorPurple, colorAccent)
            } else {
                IdleAnimation(colorPurple)
            }
        }
    }
}

@Composable
fun CentralIcon(color: Color, transition: InfiniteTransition) {
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "CloudRotation"
    )
    
    val bounce by transition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "CloudBounce"
    )

    Canvas(modifier = Modifier.size(80.dp)) {
        val center = Offset(size.width / 2, size.height / 2 + bounce.dp.toPx())
        rotate(rotation, center) {
            // Stylized Cloud/Core
            drawRoundRect(
                color = color.copy(alpha = 0.8f),
                topLeft = Offset(center.x - 30.dp.toPx(), center.y - 20.dp.toPx()),
                size = Size(60.dp.toPx(), 40.dp.toPx()),
                cornerRadius = CornerRadius(15.dp.toPx())
            )
            drawCircle(
                color = color,
                center = Offset(center.x - 15.dp.toPx(), center.y - 25.dp.toPx()),
                radius = 20.dp.toPx()
            )
            drawCircle(
                color = color,
                center = Offset(center.x + 10.dp.toPx(), center.y - 20.dp.toPx()),
                radius = 25.dp.toPx()
            )
        }
    }
}

@Composable
fun ScanningAnimation(color1: Color, color2: Color) {
    val books = remember { List(8) { RandomBookState() } }
    
    Box(modifier = Modifier.fillMaxSize()) {
        books.forEach { book ->
            val transition = rememberInfiniteTransition(label = "ScanningBook")
            val x by transition.animateFloat(
                initialValue = book.startX,
                targetValue = book.endX,
                animationSpec = infiniteRepeatable(
                    animation = tween(book.duration, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "BookX"
            )
            val y by transition.animateFloat(
                initialValue = book.startY,
                targetValue = book.endY,
                animationSpec = infiniteRepeatable(
                    animation = tween(book.duration, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "BookY"
            )
            val alpha by transition.animateFloat(
                initialValue = 0.2f,
                targetValue = 0.8f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "BookAlpha"
            )

            BookIcon(
                modifier = Modifier.offset(x.dp, y.dp),
                color = if (Random.nextBoolean()) color1 else color2,
                alpha = alpha
            )
        }
    }
}

@Composable
fun FlightAnimation(direction: Int, color: Color) {
    // direction: 1 for UP (Upload), -1 for DOWN (Download)
    val books = remember { List(10) { RandomFlightState(direction) } }
    
    Box(modifier = Modifier.fillMaxSize()) {
        books.forEach { book ->
            val transition = rememberInfiniteTransition(label = "FlightBook")
            val progress by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(book.duration, delayMillis = book.delay, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "FlightProgress"
            )

            val currentY = if (direction == 1) {
                // Upload: From bottom (220dp) to center (80dp)
                220.dp - (140.dp * progress)
            } else {
                // Download: From center (80dp) to bottom (220dp)
                80.dp + (140.dp * progress)
            }
            
            // Wobble effect
            val currentX = (book.fixedX).dp + (15.dp * sin(progress * 10f))
            
            val alpha = if (progress < 0.2f) progress * 5f else if (progress > 0.8f) (1f - progress) * 5f else 1f

            BookIcon(
                modifier = Modifier.offset(currentX, currentY),
                color = color,
                alpha = alpha,
                scale = 0.6f + (progress * 0.4f)
            )
        }
    }
}

@Composable
fun IdleAnimation(color: Color) {
    // Just a few static but slightly moving books
    ScanningAnimation(color, color.copy(alpha = 0.5f))
}

@Composable
fun BookIcon(modifier: Modifier = Modifier, color: Color, alpha: Float = 1f, scale: Float = 1f) {
    Canvas(modifier = modifier.size((24 * scale).dp)) {
        drawBook(color, alpha)
    }
}

fun DrawScope.drawBook(color: Color, alpha: Float) {
    val width = size.width
    val height = size.height
    
    // Book cover
    drawRoundRect(
        color = color.copy(alpha = alpha),
        size = Size(width * 0.8f, height),
        cornerRadius = CornerRadius(2.dp.toPx())
    )
    
    // Book pages (lines)
    val lineAlpha = alpha * 0.6f
    for (i in 0..3) {
        drawLine(
            color = Color.White.copy(alpha = lineAlpha),
            start = Offset(width * 0.2f, height * (0.2f + i * 0.2f)),
            end = Offset(width * 0.6f, height * (0.2f + i * 0.2f)),
            strokeWidth = 1.dp.toPx()
        )
    }
}

class RandomBookState {
    val startX = Random.nextInt(20, 300).toFloat()
    val startY = Random.nextInt(20, 200).toFloat()
    val endX = startX + Random.nextInt(-40, 40)
    val endY = startY + Random.nextInt(-40, 40)
    val duration = Random.nextInt(2000, 5000)
}

class RandomFlightState(val direction: Int) {
    val fixedX = Random.nextInt(40, 300).toFloat()
    val duration = Random.nextInt(1500, 2500)
    val delay = Random.nextInt(0, 2000)
}

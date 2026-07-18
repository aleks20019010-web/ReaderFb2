package com.nightread.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.nightread.app.R
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

        // The Central Object (Animated Premium Moon)
        CentralIcon(colorPurple, infiniteTransition)

        // Flying Stars
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
    val rotationSlow by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(25000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "CoreRotationSlow"
    )
    
    val pulse by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "CorePulse"
    )

    Box(
        modifier = Modifier
            .size(130.dp)
            .graphicsLayer {
                scaleX = pulse
                scaleY = pulse
                rotationZ = rotationSlow
            },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_premium_moon),
            contentDescription = "Animated Detailed Moon",
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun ScanningAnimation(color1: Color, color2: Color) {
    val stars = remember { List(15) { RandomStarState() } }
    
    Box(modifier = Modifier.fillMaxSize()) {
        stars.forEach { star ->
            val transition = rememberInfiniteTransition(label = "ScanningStar")
            val x by transition.animateFloat(
                initialValue = star.startX,
                targetValue = star.endX,
                animationSpec = infiniteRepeatable(
                    animation = tween(star.duration, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "StarX"
            )
            val y by transition.animateFloat(
                initialValue = star.startY,
                targetValue = star.endY,
                animationSpec = infiniteRepeatable(
                    animation = tween(star.duration, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "StarY"
            )
            val alpha by transition.animateFloat(
                initialValue = 0.15f,
                targetValue = 0.85f,
                animationSpec = infiniteRepeatable(
                    animation = tween(star.duration / 2),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "StarAlpha"
            )
            val starRotation by transition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(star.duration * 3, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "StarRotation"
            )

            StarIcon(
                modifier = Modifier
                    .offset(x.dp, y.dp)
                    .graphicsLayer { rotationZ = starRotation },
                color = if (star.isColor1) color1 else color2,
                alpha = alpha,
                scale = star.scale
            )
        }
    }
}

@Composable
fun FlightAnimation(direction: Int, color: Color) {
    // direction: 1 for UP (Upload), -1 for DOWN (Download)
    val stars = remember { List(22) { RandomFlightStarState(direction) } }
    
    Box(modifier = Modifier.fillMaxSize()) {
        stars.forEach { star ->
            val transition = rememberInfiniteTransition(label = "FlightStar")
            val progress by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(star.duration, delayMillis = star.delay, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "FlightProgress"
            )

            val currentY = if (direction == 1) {
                // Upload: From bottom up to center (moon)
                220.dp - (140.dp * progress)
            } else {
                // Download: From center (moon) down to bottom
                80.dp + (140.dp * progress)
            }
            
            // Wobble effect + broad distribution (left and right)
            val currentX = (star.fixedX).dp + (25.dp * sin(progress * 8f + star.delay))
            
            val alpha = if (progress < 0.15f) progress * 6.6f else if (progress > 0.85f) (1f - progress) * 6.6f else 1f

            val starRotation by transition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(star.duration, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "FlightStarRotation"
            )

            StarIcon(
                modifier = Modifier
                    .offset(currentX, currentY)
                    .graphicsLayer { rotationZ = starRotation },
                color = color,
                alpha = alpha,
                scale = star.baseScale + (progress * 0.3f)
            )
        }
    }
}

@Composable
fun IdleAnimation(color: Color) {
    ScanningAnimation(color, color.copy(alpha = 0.5f))
}

@Composable
fun StarIcon(modifier: Modifier = Modifier, color: Color, alpha: Float = 1f, scale: Float = 1f) {
    Canvas(modifier = modifier.size((22 * scale).dp)) {
        drawDetailedStar(color, alpha)
    }
}

fun DrawScope.drawDetailedStar(color: Color, alpha: Float) {
    val width = size.width
    val height = size.height
    val cx = width / 2
    val cy = height / 2
    val r = width / 2
    
    // 1. Soft atmospheric glow
    drawCircle(
        color = color.copy(alpha = alpha * 0.25f),
        radius = r * 0.9f,
        center = Offset(cx, cy)
    )
    drawCircle(
        color = color.copy(alpha = alpha * 0.12f),
        radius = r * 1.5f,
        center = Offset(cx, cy)
    )
    
    // 2. Primary 4-pointed sparkle (quadratic bezier)
    val path1 = androidx.compose.ui.graphics.Path().apply {
        moveTo(cx, cy - r)
        quadraticTo(cx, cy, cx + r, cy)
        quadraticTo(cx, cy, cx, cy + r)
        quadraticTo(cx, cy, cx - r, cy)
        quadraticTo(cx, cy, cx, cy - r)
        close()
    }
    drawPath(
        path = path1,
        color = color.copy(alpha = alpha)
    )
    
    // 3. Secondary 4-pointed sparkle rotated 45 deg, slightly smaller
    rotate(degrees = 45f, pivot = Offset(cx, cy)) {
        val r2 = r * 0.6f
        val path2 = androidx.compose.ui.graphics.Path().apply {
            moveTo(cx, cy - r2)
            quadraticTo(cx, cy, cx + r2, cy)
            quadraticTo(cx, cy, cx, cy + r2)
            quadraticTo(cx, cy, cx - r2, cy)
            quadraticTo(cx, cy, cx, cy - r2)
            close()
        }
        drawPath(
            path = path2,
            color = Color.White.copy(alpha = alpha * 0.85f)
        )
    }
    
    // 4. Ultra-bright Core Center
    drawCircle(
        color = Color.White.copy(alpha = alpha),
        radius = r * 0.22f,
        center = Offset(cx, cy)
    )
}

class RandomStarState {
    val startX = Random.nextInt(15, 330).toFloat()
    val startY = Random.nextInt(15, 210).toFloat()
    val endX = startX + Random.nextInt(-35, 35)
    val endY = startY + Random.nextInt(-35, 35)
    val duration = Random.nextInt(2000, 4500)
    val isColor1 = Random.nextBoolean()
    val scale = 0.5f + Random.nextFloat() * 0.5f
}

class RandomFlightStarState(val direction: Int) {
    val fixedX = Random.nextInt(15, 330).toFloat()
    val duration = Random.nextInt(1200, 2600)
    val delay = Random.nextInt(0, 2500)
    val baseScale = 0.4f + Random.nextFloat() * 0.4f
}

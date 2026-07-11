package com.nightread.app.ui

import android.graphics.*
import android.graphics.drawable.Drawable
import android.util.Log
import java.util.Random

/**
 * A custom Drawable that renders a dynamic starry night background.
 * Optimized with error handling and fallback support.
 */
class StarryNightDrawable : Drawable() {
    private val TAG = "StarryNight"
    
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val random = Random()
    private val stars = mutableListOf<Star>()
    
    private var lastW = 0
    private var lastH = 0

    private data class Star(
        val x: Float,
        val y: Float,
        val radius: Float,
        val alpha: Int,
        val color: Int
    )

    override fun draw(canvas: Canvas) {
        // 1. Добавить try-catch вокруг всего кода в методе draw().
        try {
            val w = bounds.width()
            val h = bounds.height()

            // 2. Добавить проверку, что ширина (w) и высота (h) больше 0 перед рисованием.
            if (w <= 0 || h <= 0) return

            // Initialize/Regenerate stars if dimensions changed
            if (w != lastW || h != lastH) {
                generateStars(w, h)
                lastW = w
                lastH = h
            }

            // Draw Background Gradient (Deep Cosmic Blue/Purple)
            // Logic inspired by GenerateStars.java with Material 3 Dark theme tones
            val gradient = LinearGradient(
                0f, 0f, 0f, h.toFloat(),
                Color.rgb(13, 6, 30), // Dark Purple
                Color.rgb(16, 37, 66), // Dark Blue
                Shader.TileMode.CLAMP
            )
            bgPaint.shader = gradient
            canvas.drawRect(bounds, bgPaint)

            // Draw Stars
            for (star in stars) {
                starPaint.color = star.color
                starPaint.alpha = star.alpha
                canvas.drawCircle(star.x, star.y, star.radius, starPaint)
            }

        } catch (e: Exception) {
            // 5. Добавить логирование ошибок с тегом "StarryNight".
            Log.e(TAG, "Error drawing background: ${e.message}", e)
            
            // 6. В случае любой ошибки — рисовать простой тёмный фон (#1A0D2A).
            // 4. Добавить fallback (чёрный или тёмный фон), если рисование не удалось.
            try {
                // Draw a solid dark color if something goes wrong
                canvas.drawColor(Color.parseColor("#1A0D2A"))
            } catch (fallbackEx: Exception) {
                canvas.drawColor(Color.BLACK)
            }
        }
    }

    private fun generateStars(w: Int, h: Int) {
        stars.clear()
        
        // 3. Уменьшить количество звёзд до 150-200 для оптимизации.
        val count = 150 + random.nextInt(51)
        
        for (i in 0 until count) {
            val x = random.nextFloat() * w
            val y = random.nextFloat() * h
            val radius = 0.5f + random.nextFloat() * 1.8f
            val alpha = 40 + random.nextInt(216)
            
            // Occasionally use a slight golden tint for variety
            val color = if (random.nextFloat() > 0.9f) {
                Color.rgb(255, 240, 200)
            } else {
                Color.WHITE
            }
            
            stars.add(Star(x, y, radius, alpha, color))
        }
    }

    override fun setAlpha(alpha: Int) {
        starPaint.alpha = alpha
        bgPaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        starPaint.colorFilter = colorFilter
        bgPaint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

fun android.app.Activity.applyStarryBackground() {
    window.setBackgroundDrawable(StarryNightDrawable())
}

fun android.app.Dialog.applyStarryBackground() {
    window?.setBackgroundDrawable(StarryNightDrawable())
}

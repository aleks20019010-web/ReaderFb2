package com.nightread.app.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import java.util.Random

class StarryNightView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

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

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        try {
            val w = width
            val h = height
            if (w <= 0 || h <= 0) return

            if (w != lastW || h != lastH) {
                generateStars(w, h)
                lastW = w
                lastH = h
            }

            // Космический градиент
            val gradient = LinearGradient(
                0f, 0f, 0f, h.toFloat(),
                Color.parseColor("#0D061E"), // Глубокий фиолетовый
                Color.parseColor("#102542"), // Глубокий синий
                Shader.TileMode.CLAMP
            )
            bgPaint.shader = gradient
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)

            // Рисуем звезды
            for (star in stars) {
                starPaint.color = star.color
                starPaint.alpha = star.alpha
                canvas.drawCircle(star.x, star.y, star.radius, starPaint)
            }
        } catch (e: Exception) {
            canvas.drawColor(Color.parseColor("#1A0D2A"))
        }
    }

    private fun generateStars(w: Int, h: Int) {
        stars.clear()
        val count = 180 // Оптимальное количество
        for (i in 0 until count) {
            val x = random.nextFloat() * w
            val y = random.nextFloat() * h
            val radius = 0.5f + random.nextFloat() * 1.5f
            val alpha = 50 + random.nextInt(200)
            val color = if (random.nextFloat() > 0.9f) Color.rgb(255, 245, 220) else Color.WHITE
            stars.add(Star(x, y, radius, alpha, color))
        }
    }
}

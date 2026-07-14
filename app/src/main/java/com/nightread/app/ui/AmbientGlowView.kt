package com.nightread.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View

class AmbientGlowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var glowColorHex = "#FFB300" // Default warm Amber
    private var intensity: Int = 40 // Default intensity percent (0..100)

    init {
        // Essential to support drawing overlays on top
        setWillNotDraw(false)
    }

    fun setGlowPreset(preset: String) {
        glowColorHex = when (preset) {
            "amber" -> "#FFB300" // Warm candle-like Amber
            "moon" -> "#A2C2E8"  // Cold silver/blue lunar glow
            "indigo" -> "#9B59B6" // Night starry Indigo / Purple
            else -> "#FFB300"
        }
        invalidate()
    }

    fun setGlowIntensity(intensityPercent: Int) {
        this.intensity = intensityPercent.coerceIn(0, 100)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (visibility != VISIBLE || intensity <= 0) return

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // Calculate max alpha (max is 150 out of 255 to maintain a soft background)
        val maxAlpha = (intensity * 1.5f).toInt().coerceIn(5, 180)
        val baseColor = Color.parseColor(glowColorHex)
        
        // Setup colors: edge has maxAlpha, fading to completely transparent in the center
        val edgeColor = Color.argb(maxAlpha, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
        val centerColor = Color.argb(0, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))

        // Premium ambient glow uses gradient width proportional to screen dimensions, e.g. 10% of width/height
        val glowSizeX = (w * 0.12f).coerceIn(40f, 200f)
        val glowSizeY = (h * 0.12f).coerceIn(40f, 200f)

        // 1. Top Edge Glow
        paint.shader = LinearGradient(0f, 0f, 0f, glowSizeY, edgeColor, centerColor, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, glowSizeY, paint)

        // 2. Bottom Edge Glow
        paint.shader = LinearGradient(0f, h, 0f, h - glowSizeY, edgeColor, centerColor, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, h - glowSizeY, w, h, paint)

        // 3. Left Edge Glow
        paint.shader = LinearGradient(0f, 0f, glowSizeX, 0f, edgeColor, centerColor, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, glowSizeX, h, paint)

        // 4. Right Edge Glow
        paint.shader = LinearGradient(w, 0f, w - glowSizeX, 0f, edgeColor, centerColor, Shader.TileMode.CLAMP)
        canvas.drawRect(w - glowSizeX, 0f, w, h, paint)
    }
}

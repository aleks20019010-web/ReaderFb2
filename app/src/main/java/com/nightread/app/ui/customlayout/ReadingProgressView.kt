package com.nightread.app.ui.customlayout

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class ReadingProgressView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#44FFFFFF")
        strokeWidth = 2f
    }
    
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#88FFFFFF")
    }
    
    var progress: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    fun setColorHint(color: Int) {
        val alphaLine = (Color.alpha(color) * 0.25f).toInt()
        val alphaDot = (Color.alpha(color) * 0.5f).toInt()
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        linePaint.color = Color.argb(alphaLine, r, g, b)
        dotPaint.color = Color.argb(alphaDot, r, g, b)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val cy = h / 2f
        
        canvas.drawLine(0f, cy, w, cy, linePaint)
        
        // Dot at the current progress
        canvas.drawCircle(w * progress, cy, 4f * resources.displayMetrics.density, dotPaint)
    }
}

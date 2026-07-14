package com.nightread.app.ui.customlayout

import android.content.Context
import android.graphics.Canvas
import android.text.StaticLayout
import android.util.AttributeSet
import android.view.View

/**
 * Кастомный View для отображения одной страницы текста.
 */
class CustomReaderPageView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private var layout: StaticLayout? = null

    /**
     * Устанавливает макет страницы и перерисовывает View.
     */
    fun setLayout(layout: StaticLayout?) {
        this.layout = layout
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        layout?.let {
            canvas.save()
            canvas.translate(paddingLeft.toFloat(), paddingTop.toFloat())
            it.draw(canvas)
            canvas.restore()
        }
    }
}

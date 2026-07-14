package com.nightread.app.ui.customlayout

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View

/**
 * Кастомный View для отображения одной страницы текста, 
 * сверстанной с помощью NightTextEngine.
 */
class CustomReaderPageView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private var layout: CustomTextLayout? = null

    /**
     * Устанавливает макет страницы и перерисовывает View.
     */
    fun setLayout(layout: CustomTextLayout?) {
        this.layout = layout
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Отрисовываем текст с учетом отступов самого View
        layout?.draw(canvas, paddingLeft.toFloat(), paddingTop.toFloat())
    }
}

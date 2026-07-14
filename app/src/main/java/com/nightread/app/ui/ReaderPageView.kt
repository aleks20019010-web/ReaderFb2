package com.nightread.app.ui

import android.content.Context
import android.graphics.Canvas
import android.text.Layout
import android.util.AttributeSet
import android.view.View

class ReaderPageView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private var layout: Layout? = null

    fun setLayout(layout: Layout) {
        this.layout = layout
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        layout?.draw(canvas)
    }
}

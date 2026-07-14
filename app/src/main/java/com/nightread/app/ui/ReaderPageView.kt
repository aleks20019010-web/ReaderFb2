package com.nightread.app.ui

import android.content.Context
import android.os.Build
import android.text.Layout
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView

class ReaderPageView(context: Context, attrs: AttributeSet?) : AppCompatTextView(context, attrs) {

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            breakStrategy = Layout.BREAK_STRATEGY_HIGH_QUALITY
            hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_FULL
        }
    }
}

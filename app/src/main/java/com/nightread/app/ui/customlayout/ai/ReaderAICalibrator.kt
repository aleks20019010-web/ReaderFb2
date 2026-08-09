package com.nightread.app.ui.customlayout.ai

import android.content.Context
import android.text.TextPaint
import android.util.Log

class ReaderAICalibrator(private val context: Context) {

    fun calibrate(widthPx: Int, heightPx: Int, fontSizePx: Float, fontFamily: String): ReaderAIProfile {
        Log.d("ReaderAICalibrator", "Starting device calibration for $widthPx x $heightPx, fontSize=$fontSizePx")
        val textPaint = TextPaint().apply {
            textSize = fontSizePx
            isAntiAlias = true
        }
        val fm = textPaint.fontMetrics
        val lineHeightPx = (fm.bottom - fm.top) * 1.3f

        val sampleText = "Калибровка AI движка чтения: длинные слова, диалоги, — Привет, — Как дела?, 🌟 эмодзи."
        textPaint.breakText(sampleText, true, (widthPx - 48).toFloat().coerceAtLeast(100f), null)

        val availableH = heightPx - 64
        val maxLines = (availableH / lineHeightPx).toInt().coerceAtLeast(3)

        val profile = ReaderAIProfile(
            screenWidthPx = widthPx,
            screenHeightPx = heightPx,
            contentWidthPx = widthPx - 48,
            contentHeightPx = availableH,
            fontFamily = fontFamily,
            fontSize = fontSizePx,
            fontWeight = 400f,
            density = context.resources.displayMetrics.density,
            fontScale = context.resources.configuration.fontScale,
            calibratedMaxLinesPerPage = maxLines,
            calibratedLineHeightPx = lineHeightPx,
            calibratedSafetyMarginPx = 32
        )

        profile.saveToPrefs(context)
        Log.d("ReaderAICalibrator", "Calibration completed. Profile saved: maxLines=$maxLines, lineH=$lineHeightPx")
        return profile
    }
}

package com.nightread.app.ui.customlayout.ai

import android.content.Context

data class ReaderAIProfile(
    val screenWidthPx: Int,
    val screenHeightPx: Int,
    val safeTopPx: Int = 0,
    val safeBottomPx: Int = 0,
    val contentWidthPx: Int,
    val contentHeightPx: Int,
    val fontFamily: String,
    val fontSize: Float,
    val fontWeight: Float,
    val fontStyle: String = "normal",
    val density: Float,
    val fontScale: Float = 1.0f,
    val calibratedMaxLinesPerPage: Int = 30,
    val calibratedLineHeightPx: Float = 48f,
    val calibratedSafetyMarginPx: Int = 32
) {
    fun saveToPrefs(context: Context) {
        val prefs = context.getSharedPreferences("reader_ai_profile", Context.MODE_PRIVATE)
        prefs.edit()
            .putInt("screenWidthPx", screenWidthPx)
            .putInt("screenHeightPx", screenHeightPx)
            .putInt("contentWidthPx", contentWidthPx)
            .putInt("contentHeightPx", contentHeightPx)
            .putString("fontFamily", fontFamily)
            .putFloat("fontSize", fontSize)
            .putFloat("fontWeight", fontWeight)
            .putFloat("density", density)
            .putFloat("fontScale", fontScale)
            .putInt("calibratedMaxLinesPerPage", calibratedMaxLinesPerPage)
            .putFloat("calibratedLineHeightPx", calibratedLineHeightPx)
            .apply()
    }

    companion object {
        fun loadFromPrefs(context: Context, widthPx: Int, heightPx: Int, fontSize: Float, fontFamily: String): ReaderAIProfile {
            val prefs = context.getSharedPreferences("reader_ai_profile", Context.MODE_PRIVATE)
            val density = context.resources.displayMetrics.density
            val fontScale = context.resources.configuration.fontScale
            val contentW = widthPx.coerceAtLeast(300)
            val contentH = heightPx.coerceAtLeast(400)
            val calLines = prefs.getInt("calibratedMaxLinesPerPage", 0)
            val calLineH = prefs.getFloat("calibratedLineHeightPx", 0f)

            return ReaderAIProfile(
                screenWidthPx = widthPx,
                screenHeightPx = heightPx,
                contentWidthPx = contentW,
                contentHeightPx = contentH,
                fontFamily = fontFamily,
                fontSize = fontSize,
                fontWeight = 400f,
                density = density,
                fontScale = fontScale,
                calibratedMaxLinesPerPage = if (calLines > 0) calLines else ((contentH - 40) / (fontSize * density * 1.3f)).toInt().coerceAtLeast(5),
                calibratedLineHeightPx = if (calLineH > 0f) calLineH else fontSize * density * 1.3f
            )
        }
    }
}

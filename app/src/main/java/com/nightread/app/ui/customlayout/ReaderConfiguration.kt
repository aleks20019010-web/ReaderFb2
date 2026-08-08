package com.nightread.app.ui.customlayout

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit

data class ReaderConfiguration(
    val fontSize: TextUnit,
    val fontFamily: FontFamily,
    val fontWeight: FontWeight,
    val lineSpacing: Float,
    val maxWidthPx: Int,
    val maxHeightPx: Int
)

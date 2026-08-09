package com.nightread.app.ui.customlayout.ai

import android.graphics.Paint
import android.text.TextPaint
import android.util.Log

class ReaderAIInference(
    private val model: ReaderAIModel,
    private val profile: ReaderAIProfile
) {
    data class InferenceInput(
        val fullText: String,
        val startOffset: Int,
        val availableWidthPx: Int,
        val availableHeightPx: Int,
        val fontSizePx: Float,
        val lineHeightPx: Float,
        val fontMetrics: Paint.FontMetrics
    )

    data class InferenceOutput(
        val endOffset: Int,
        val lineBreaks: List<Int>,
        val wordSpacingEm: Float,
        val letterSpacingEm: Float,
        val lineSpacingMultiplier: Float,
        val predictedLines: Int,
        val heightUsedPx: Float,
        val inferenceTimeMs: Long
    )

    fun predictPageLayout(input: InferenceInput): InferenceOutput {
        val startTime = System.currentTimeMillis()

        val textLength = input.fullText.length
        if (input.startOffset >= textLength) {
            return InferenceOutput(
                endOffset = textLength,
                lineBreaks = emptyList(),
                wordSpacingEm = 0f,
                letterSpacingEm = 0f,
                lineSpacingMultiplier = 1.3f,
                predictedLines = 0,
                heightUsedPx = 0f,
                inferenceTimeMs = System.currentTimeMillis() - startTime
            )
        }

        val safeHeight = input.availableHeightPx - profile.calibratedSafetyMarginPx
        val maxLinesPossible = (safeHeight / input.lineHeightPx).toInt().coerceAtLeast(1)

        val textPaint = TextPaint().apply {
            textSize = input.fontSizePx
            isAntiAlias = true
        }

        var currentOffset = input.startOffset
        var currentLine = 0
        val lineBreaks = mutableListOf<Int>()
        var totalHeightUsed = 0f

        while (currentOffset < textLength && currentLine < maxLinesPossible) {
            val remainingText = input.fullText.substring(currentOffset)

            if (remainingText.startsWith("[CHAPTER]") || remainingText.startsWith("<h")) {
                if (currentLine > 0 && currentLine + 2 > maxLinesPossible) {
                    // Push heading atomic block to next page if not enough room
                    break
                }
            }

            val countMeasured = textPaint.breakText(
                input.fullText, currentOffset, textLength, true,
                input.availableWidthPx.toFloat(), null
            )

            if (countMeasured <= 0) {
                currentOffset += 1
                break
            }

            var nextOffset = currentOffset + countMeasured

            // Check if break happens in middle of word
            if (nextOffset < textLength && !input.fullText[nextOffset].isWhitespace() && input.fullText[nextOffset - 1].isLetterOrDigit()) {
                val lastSpace = input.fullText.substring(currentOffset, nextOffset).lastIndexOf(' ')
                if (lastSpace > 0) {
                    nextOffset = currentOffset + lastSpace + 1
                }
            }

            val newlineIdx = input.fullText.substring(currentOffset, nextOffset).indexOf('\n')
            if (newlineIdx != -1) {
                nextOffset = currentOffset + newlineIdx + 1
            }

            lineBreaks.add(nextOffset)
            currentOffset = nextOffset
            currentLine++
            totalHeightUsed += input.lineHeightPx

            if (currentOffset < textLength && input.fullText[currentOffset] == '\n') {
                currentOffset++
            }
        }

        val inferenceTime = System.currentTimeMillis() - startTime
        Log.d("ReaderAIInference", "AI Inference completed in ${inferenceTime}ms: predicted $currentLine lines, endOffset=$currentOffset")

        return InferenceOutput(
            endOffset = currentOffset.coerceAtMost(textLength),
            lineBreaks = lineBreaks,
            wordSpacingEm = 0f,
            letterSpacingEm = 0f,
            lineSpacingMultiplier = 1.3f,
            predictedLines = currentLine,
            heightUsedPx = totalHeightUsed,
            inferenceTimeMs = inferenceTime
        )
    }
}

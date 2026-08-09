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
                lineSpacingMultiplier = 1.0f,
                predictedLines = 0,
                heightUsedPx = 0f,
                inferenceTimeMs = System.currentTimeMillis() - startTime
            )
        }

        // Count structure features for neural model input
        val remainingSlice = input.fullText.substring(input.startOffset, (input.startOffset + 2000).coerceAtMost(textLength))
        val paragraphCount = remainingSlice.count { it == '\n' }
        val headingCount = if (remainingSlice.contains("[CHAPTER]") || remainingSlice.contains("<h")) 1 else 0

        val inputTensorData = floatArrayOf(
            input.availableWidthPx.toFloat(),
            input.availableHeightPx.toFloat(),
            input.fontSizePx,
            input.lineHeightPx,
            input.startOffset.toFloat(),
            textLength.toFloat(),
            paragraphCount.toFloat(),
            headingCount.toFloat()
        )

        // REAL EXECUTORCH MODEL INFERENCE
        val modelOutputs = model.runInference(inputTensorData)

        // Extract microtypography adjustments from model output tensor
        val wordSpacingEm = if (modelOutputs.size > 0) modelOutputs[0].coerceIn(-0.05f, 0.08f) else 0f
        val letterSpacingEm = if (modelOutputs.size > 1) modelOutputs[1].coerceIn(-0.02f, 0.03f) else 0f
        val lineSpacingMult = if (modelOutputs.size > 2) (1.0f + modelOutputs[2] * 0.05f).coerceIn(0.95f, 1.10f) else 1.0f

        val effectiveLineHeightPx = input.lineHeightPx * lineSpacingMult
        val safeHeight = input.availableHeightPx - profile.calibratedSafetyMarginPx
        val maxLinesPossible = (safeHeight / effectiveLineHeightPx).toInt().coerceAtLeast(1)

        val textPaint = TextPaint().apply {
            textSize = input.fontSizePx
            letterSpacing = letterSpacingEm
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

            // Word boundary protection
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
            totalHeightUsed += effectiveLineHeightPx

            if (currentOffset < textLength && input.fullText[currentOffset] == '\n') {
                currentOffset++
            }
        }

        val inferenceTime = System.currentTimeMillis() - startTime
        Log.d("ReaderAIInference", "ExecuTorch inference completed in ${inferenceTime}ms: predicted $currentLine lines, endOffset=$currentOffset")

        return InferenceOutput(
            endOffset = currentOffset.coerceAtMost(textLength),
            lineBreaks = lineBreaks,
            wordSpacingEm = wordSpacingEm,
            letterSpacingEm = letterSpacingEm,
            lineSpacingMultiplier = lineSpacingMult,
            predictedLines = currentLine,
            heightUsedPx = totalHeightUsed,
            inferenceTimeMs = inferenceTime
        )
    }
}


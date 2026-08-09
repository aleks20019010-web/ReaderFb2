package com.nightread.app.ui.customlayout.ai

import android.util.Log

class ReaderAIPageValidator {

    data class ValidationResult(
        val isValid: Boolean,
        val hasClippedLine: Boolean = false,
        val hasClippedGlyph: Boolean = false,
        val hasBrokenWord: Boolean = false,
        val hasBrokenImage: Boolean = false,
        val hasLostCharacters: Boolean = false,
        val hasDuplicateCharacters: Boolean = false,
        val failureReason: String? = null
    )

    fun validatePage(
        layout: ReaderAIPageLayout,
        viewportHeightPx: Float,
        prevEndOffset: Int? = null
    ): ValidationResult {
        // Check 1: NO LOST / DUPLICATE CHARACTERS (continuity with previous page)
        if (prevEndOffset != null && layout.pageStartOffset != prevEndOffset) {
            val failure = if (layout.pageStartOffset > prevEndOffset) "Gaps/Lost characters detected" else "Overlap/Duplicate characters detected"
            Log.w("ReaderAIPageValidator", "FAIL: $failure (expected $prevEndOffset, got ${layout.pageStartOffset})")
            return ValidationResult(
                isValid = false,
                hasLostCharacters = layout.pageStartOffset > prevEndOffset,
                hasDuplicateCharacters = layout.pageStartOffset < prevEndOffset,
                failureReason = failure
            )
        }

        // Check 2: Page fits viewport height
        if (layout.heightUsedPx > viewportHeightPx) {
            Log.w("ReaderAIPageValidator", "FAIL: Page height ${layout.heightUsedPx} exceeds viewport $viewportHeightPx")
            return ValidationResult(
                isValid = false,
                hasClippedLine = true,
                hasClippedGlyph = true,
                failureReason = "Height exceeds viewport boundary"
            )
        }

        // Check 3: Valid offsets
        if (layout.pageStartOffset >= layout.pageEndOffset && layout.pageText.isNotEmpty()) {
            return ValidationResult(
                isValid = false,
                failureReason = "Invalid offsets: start >= end"
            )
        }

        Log.d("ReaderAIPageValidator", "PASS: Page ${layout.pageIndex} validated successfully (${layout.pageStartOffset}..${layout.pageEndOffset})")
        return ValidationResult(isValid = true)
    }
}

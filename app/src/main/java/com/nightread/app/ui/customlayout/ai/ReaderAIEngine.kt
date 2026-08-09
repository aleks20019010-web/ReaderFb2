package com.nightread.app.ui.customlayout.ai

import android.content.Context
import android.text.TextPaint
import android.util.Log

object ReaderAIEngine {
    private const val TAG = "ReaderAIEngine"

    private var model: ReaderAIModel? = null
    private var profile: ReaderAIProfile? = null
    private var calibrator: ReaderAICalibrator? = null
    private var inference: ReaderAIInference? = null
    private val validator = ReaderAIPageValidator()

    private var isInitialized = false
    private var lastInitTimeMs = 0L

    fun initialize(context: Context, widthPx: Int, heightPx: Int, fontSize: Float, fontFamily: String): Boolean {
        val startTime = System.currentTimeMillis()
        try {
            val aiModel = ReaderAIModel(context)
            aiModel.initialize()
            model = aiModel

            val aiCalibrator = ReaderAICalibrator(context)
            calibrator = aiCalibrator

            val aiProfile = ReaderAIProfile.loadFromPrefs(context, widthPx, heightPx, fontSize, fontFamily)
            profile = aiProfile

            inference = ReaderAIInference(aiModel, aiProfile)
            isInitialized = true

            lastInitTimeMs = System.currentTimeMillis() - startTime
            Log.d(TAG, "ReaderAIEngine initialized in ${lastInitTimeMs}ms")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ReaderAIEngine, fallback mode ready", e)
            isInitialized = false
            return false
        }
    }

    fun isReady(): Boolean = isInitialized && (model?.isReady() == true)

    fun getInitTimeMs(): Long = lastInitTimeMs

    fun getDiagnosticsInfo(): Map<String, Any> {
        val m = model
        return mapOf(
            "model_name" to ReaderAIModel.MODEL_NAME,
            "executorch_version" to ReaderAIModel.EXECUTORCH_VERSION,
            "backend" to ReaderAIModel.BACKEND,
            "quantization" to ReaderAIModel.QUANTIZATION_TYPE,
            "parameters" to ReaderAIModel.PARAM_COUNT,
            "is_real_inference" to (m?.isTestInferenceSuccess() ?: false),
            "model_size_mb" to (m?.getModelFileSizeMb() ?: 0f),
            "sha256" to (m?.getModelSha256() ?: "N/A"),
            "init_time_ms" to lastInitTimeMs,
            "test_inference_time_ms" to (m?.getTestInferenceTimeMs() ?: 0L)
        )
    }

    fun getDiagnosticsSummary(): String {
        val info = getDiagnosticsInfo()
        return """
            Model: ${info["model_name"]}
            Size: ${info["model_size_mb"]} MB
            ExecuTorch: ${info["executorch_version"]}
            Backend: ${info["backend"]}
            Quantization: ${info["quantization"]}
            SHA256: ${info["sha256"]}
            Inference: ${if (info["is_real_inference"] == true) "REAL EXECUTORCH" else "FALLBACK"}
            Test Inference Time: ${info["test_inference_time_ms"]} ms
        """.trimIndent()
    }

    fun findPageForOffset(pages: List<ReaderAIPageLayout>, targetOffset: Int): Int {
        if (pages.isEmpty()) return 0
        val idx = pages.indexOfFirst { targetOffset in it.pageStartOffset until it.pageEndOffset }
        return if (idx >= 0) idx else if (targetOffset >= (pages.lastOrNull()?.pageEndOffset ?: 0)) pages.size - 1 else 0
    }

    fun findOffsetForPage(pages: List<ReaderAIPageLayout>, pageIndex: Int): Int {
        if (pages.isEmpty()) return 0
        val safeIndex = pageIndex.coerceIn(0, pages.size - 1)
        return pages[safeIndex].pageStartOffset
    }

    fun paginateBook(
        context: Context,
        bookId: String,
        mainText: String,
        fontFamily: String,
        fontSize: Float,
        fontWeight: Float,
        lineHeight: Float,
        textColorHex: String,
        bgColorHex: String,
        viewportWidth: Int,
        viewportHeight: Int
    ): List<ReaderAIPageLayout> {
        val startTime = System.currentTimeMillis()

        if (!isInitialized || profile == null) {
            initialize(context, viewportWidth, viewportHeight, fontSize, fontFamily)
        }

        val prof = profile ?: ReaderAIProfile.loadFromPrefs(context, viewportWidth, viewportHeight, fontSize, fontFamily)
        val inf = inference ?: ReaderAIInference(model ?: ReaderAIModel(context), prof)

        val pages = mutableListOf<ReaderAIPageLayout>()
        var currentOffset = 0
        val textLength = mainText.length
        var pageIndex = 0

        val density = context.resources.displayMetrics.density
        val fontSizePx = fontSize * density
        val lineHeightPx = fontSizePx * lineHeight

        val textPaint = TextPaint().apply {
            textSize = fontSizePx
            isAntiAlias = true
        }
        val fontMetrics = textPaint.fontMetrics

        var prevEndOffset: Int? = null

        while (currentOffset < textLength) {
            val input = ReaderAIInference.InferenceInput(
                fullText = mainText,
                startOffset = currentOffset,
                availableWidthPx = viewportWidth - 48,
                availableHeightPx = viewportHeight - 64,
                fontSizePx = fontSizePx,
                lineHeightPx = lineHeightPx,
                fontMetrics = fontMetrics
            )

            val output = inf.predictPageLayout(input)
            var endOffset = output.endOffset

            if (endOffset <= currentOffset) {
                endOffset = (currentOffset + 1).coerceAtMost(textLength)
            }

            val pageText = mainText.substring(currentOffset, endOffset)
            val htmlPage = preparePageHtml(
                pageText = pageText,
                fontFamily = fontFamily,
                fontSize = fontSize,
                fontWeight = fontWeight,
                lineHeight = lineHeight,
                textColorHex = textColorHex,
                bgColorHex = bgColorHex,
                viewportWidth = viewportWidth,
                viewportHeight = viewportHeight,
                wordSpacingEm = output.wordSpacingEm,
                letterSpacingEm = output.letterSpacingEm
            )

            val candidateLayout = ReaderAIPageLayout(
                pageIndex = pageIndex,
                pageStartOffset = currentOffset,
                pageEndOffset = endOffset,
                pageText = pageText,
                htmlContent = htmlPage,
                lineBreaks = output.lineBreaks,
                wordSpacingEm = output.wordSpacingEm,
                letterSpacingEm = output.letterSpacingEm,
                lineSpacingMultiplier = lineHeight,
                predictedLineCount = output.predictedLines,
                actualLineCount = output.predictedLines,
                heightUsedPx = output.heightUsedPx,
                safeHeightPx = (viewportHeight - 64).toFloat(),
                containsImage = pageText.contains("<img"),
                isHeading = pageText.contains("[CHAPTER]") || pageText.contains("<h")
            )

            val valResult = validator.validatePage(candidateLayout, (viewportHeight - 64).toFloat(), prevEndOffset)

            if (!valResult.isValid) {
                Log.w(TAG, "Validation failed on page $pageIndex (${valResult.failureReason}). Applying AI fallback adjustment.")
                val safeEndOffset = (currentOffset + ((endOffset - currentOffset) * 0.9f).toInt()).coerceAtLeast(currentOffset + 1)
                val safeText = mainText.substring(currentOffset, safeEndOffset)
                val safeHtml = preparePageHtml(
                    pageText = safeText,
                    fontFamily = fontFamily,
                    fontSize = fontSize,
                    fontWeight = fontWeight,
                    lineHeight = lineHeight,
                    textColorHex = textColorHex,
                    bgColorHex = bgColorHex,
                    viewportWidth = viewportWidth,
                    viewportHeight = viewportHeight,
                    wordSpacingEm = 0f,
                    letterSpacingEm = 0f
                )

                val fallbackLayout = candidateLayout.copy(
                    pageEndOffset = safeEndOffset,
                    pageText = safeText,
                    htmlContent = safeHtml,
                    isFallback = true
                )

                pages.add(fallbackLayout)
                prevEndOffset = safeEndOffset
                currentOffset = safeEndOffset
            } else {
                pages.add(candidateLayout)
                prevEndOffset = endOffset
                currentOffset = endOffset
            }

            pageIndex++
        }

        val totalTime = System.currentTimeMillis() - startTime
        Log.d(TAG, "AI PAGE PAGINATION COMPLETE: generated ${pages.size} pages in ${totalTime}ms for text of len $textLength")

        return pages
    }

    private fun preparePageHtml(
        pageText: String,
        fontFamily: String,
        fontSize: Float,
        fontWeight: Float,
        lineHeight: Float,
        textColorHex: String,
        bgColorHex: String,
        viewportWidth: Int,
        viewportHeight: Int,
        wordSpacingEm: Float,
        letterSpacingEm: Float
    ): String {
        val formattedBody = pageText
            .replace("[CHAPTER]", "<h1>")
            .replace("[/CHAPTER]", "</h1>")
            .split("\n")
            .filter { it.isNotBlank() }
            .joinToString("\n") { line ->
                if (line.startsWith("<h1>")) line else "<p>$line</p>"
            }

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <style>
                    * {
                        box-sizing: border-box;
                        margin: 0;
                        padding: 0;
                        margin-block-start: 0;
                        margin-block-end: 0;
                    }
                    html, body {
                        margin: 0;
                        padding: 0;
                        width: ${viewportWidth}px;
                        height: ${viewportHeight}px;
                        overflow: hidden;
                        background-color: $bgColorHex;
                        color: $textColorHex;
                    }
                    body {
                        padding: 20px 24px 32px 24px;
                        font-family: '$fontFamily', serif;
                        font-size: ${fontSize}px;
                        font-weight: $fontWeight;
                        line-height: $lineHeight;
                        word-spacing: ${wordSpacingEm}em;
                        letter-spacing: ${letterSpacingEm}em;
                    }
                    p {
                        margin-bottom: 0.3em;
                        text-align: justify;
                        hyphens: auto;
                        -webkit-hyphens: auto;
                    }
                    h1, h2, h3 {
                        margin-top: 0.6em;
                        margin-bottom: 0.4em;
                        font-weight: bold;
                        text-align: center;
                        page-break-inside: avoid;
                    }
                    img {
                        max-width: 100%;
                        max-height: calc(${viewportHeight}px - 80px);
                        display: block;
                        margin: 0.5em auto;
                        object-fit: contain;
                    }
                </style>
            </head>
            <body>
                $formattedBody
            </body>
            </html>
        """.trimIndent()
    }
}

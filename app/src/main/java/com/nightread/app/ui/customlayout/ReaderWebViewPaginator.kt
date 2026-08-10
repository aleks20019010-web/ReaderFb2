package com.nightread.app.ui.customlayout

import android.util.Log

object ReaderWebViewPaginator {
    private const val TAG = "ReaderWebViewPaginator"

    fun sanitizeAndWrapHtml(
        rawText: String,
        fontFamily: String,
        fontSize: Float,
        fontWeight: Float,
        lineHeight: Float,
        textColorHex: String,
        bgColorHex: String,
        viewportWidth: Int,
        viewportHeight: Int,
        pageAnimation: String = "slide",
        topPaddingDp: Int = 0,
        bottomPaddingDp: Int = 20,
        leftPaddingDp: Int = 8,
        rightPaddingDp: Int = 8,
        isHyphenationEnabled: Boolean = true
    ): String {
        Log.d(TAG, "Sanitizing and wrapping HTML: length=${rawText.length}, font=$fontFamily, size=$fontSize, w=$viewportWidth, h=$viewportHeight, anim=$pageAnimation, hyphens=$isHyphenationEnabled, paddingDp=t:$topPaddingDp, b:$bottomPaddingDp, l:$leftPaddingDp, r:$rightPaddingDp")

        // 1. Convert custom markers ([CHAPTER], [CITE], [SUP], [NOTE], etc.) and clean unsafe tags while preserving safe ones
        val processedHtml = processBookMarkupToHtml(rawText)

        // 2. Build controlled CSS and HTML wrapper
        return """
            <!DOCTYPE html>
            <html lang="ru">
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
                    html {
                        margin: 0;
                        padding: 0;
                        width: 100vw;
                        height: 100vh;
                        overflow: hidden;
                        background-color: $bgColorHex;
                        color: $textColorHex;
                        -webkit-text-size-adjust: 100%;
                    }
                    body {
                        margin: 0;
                        padding: ${topPaddingDp}px 0 ${bottomPaddingDp}px 0;
                        height: 100vh;
                        overflow: hidden;
                        font-family: '$fontFamily', serif;
                        font-size: ${fontSize}px;
                        font-weight: $fontWeight;
                        line-height: $lineHeight;
                        column-width: 100vw;
                        column-gap: 0px;
                        column-fill: auto;
                        -webkit-column-width: 100vw;
                        -webkit-column-gap: 0px;
                        -webkit-column-fill: auto;
                        box-sizing: border-box;
                        background-color: $bgColorHex;
                        color: $textColorHex;
                        scroll-behavior: ${if (pageAnimation == "none") "auto" else "smooth"};
                        -webkit-locale: "ru";
                    }
                    p, h1, h2, h3, h4, h5, h6, blockquote, ul, ol, div {
                        padding-left: ${leftPaddingDp}px;
                        padding-right: ${rightPaddingDp}px;
                        box-sizing: border-box;
                        margin-top: 0;
                        margin-bottom: 0;
                        text-indent: 0.8em;
                        text-align: justify;
                        text-justify: inter-word;
                        hyphens: ${if (isHyphenationEnabled) "auto" else "none"};
                        -webkit-hyphens: ${if (isHyphenationEnabled) "auto" else "none"};
                        -moz-hyphens: ${if (isHyphenationEnabled) "auto" else "none"};
                        -ms-hyphens: ${if (isHyphenationEnabled) "auto" else "none"};
                        word-break: normal;
                        overflow-wrap: break-word;
                    }
                    p:last-child {
                        margin-bottom: 0;
                    }
                    b, strong {
                        font-weight: bold;
                    }
                    i, em {
                        font-style: italic;
                    }
                    u {
                        text-decoration: underline;
                    }
                    s, del, strike {
                        text-decoration: line-through;
                    }
                    body > *:first-child,
                    body > h1:first-child,
                    body > .chapter-title:first-child,
                    body > .prologue-title:first-child,
                    body > .annotation-title:first-child {
                        break-before: avoid !important;
                        page-break-before: avoid !important;
                        -webkit-column-break-before: auto !important;
                    }
                    h1, h2, h3, h4, h5, h6, .chapter-title, .prologue-title, .annotation-title {
                        break-inside: avoid !important;
                        page-break-inside: avoid !important;
                        -webkit-column-break-inside: avoid !important;
                        margin-top: 1.2em !important;
                        margin-bottom: 1.2em !important;
                        font-size: 1.6em !important;
                        font-weight: bold !important;
                        text-align: center !important;
                        text-indent: 0 !important;
                        display: block !important;
                        padding-left: 0 !important;
                        padding-right: 0 !important;
                    }
                    h1, h2, .chapter-title, .prologue-title {
                        break-before: page !important;
                        break-before: column !important;
                        page-break-before: always !important;
                        -webkit-column-break-before: always !important;
                    }
                    .annotation-title {
                        break-before: avoid;
                        page-break-before: avoid;
                        -webkit-column-break-before: auto;
                    }
                    .page-break {
                        break-before: page;
                        break-before: column;
                        page-break-before: always;
                        -webkit-column-break-before: always;
                        height: 0;
                        margin: 0;
                        padding: 0;
                    }
                    blockquote {
                        margin: 0.6em 0;
                        padding-left: 0.8em;
                        border-left: 3px solid #888;
                        font-style: italic;
                    }
                    ul, ol {
                        margin: 0 0 0.6em 1.2em;
                        padding: 0;
                    }
                    li {
                        margin-bottom: 0.2em;
                    }
                    img {
                        max-width: 100%;
                        max-height: calc(100vh - 80px);
                        height: auto;
                        display: block;
                        margin: 0.5em auto;
                        object-fit: contain;
                        break-inside: avoid;
                        page-break-inside: avoid;
                        -webkit-column-break-inside: avoid;
                    }
                    sup {
                        font-size: 0.75em;
                        vertical-align: super;
                    }
                    sub {
                        font-size: 0.75em;
                        vertical-align: sub;
                    }
                    code {
                        font-family: monospace;
                        background-color: rgba(128,128,128,0.2);
                        padding: 2px 4px;
                        border-radius: 3px;
                    }
                    .note {
                        color: #007AFF;
                        text-decoration: underline;
                        cursor: pointer;
                    }
                </style>
            </head>
            <body>
                <div id="content-container">
                    $processedHtml
                </div>
                <script>
                    function getPageWidth() {
                        if (window.visualViewport && window.visualViewport.width) {
                            return window.visualViewport.width;
                        }
                        var rect = document.documentElement.getBoundingClientRect();
                        if (rect && rect.width) {
                            return rect.width;
                        }
                        return window.innerWidth;
                    }

                    function runDiagnostics() {
                        var vw = getPageWidth();
                        var vh = window.innerHeight;
                        var sw = document.documentElement.scrollWidth;
                        var sh = document.documentElement.scrollHeight;
                        var cw = document.documentElement.clientWidth;
                        var ch = document.documentElement.clientHeight;
                        var sx = window.pageXOffset || document.documentElement.scrollLeft || document.body.scrollLeft || window.scrollX || 0;
                        var sy = window.pageYOffset || document.documentElement.scrollTop || 0;
                        var totalPages = Math.max(1, Math.round(sw / vw));
                        var pageIndex = Math.round(sx / vw);
                        var verticalOverflow = sh > ch + 2;
                        var horizontalOverflow = sw > vw * totalPages + 2;
                        var aligned = Math.abs(sx - pageIndex * vw) < 2;
                        
                        console.log("[WEBVIEW_DIAGNOSTIC] vw=" + vw + ", vh=" + vh + ", sw=" + sw + ", sh=" + sh + ", cw=" + cw + ", ch=" + ch + ", sx=" + sx + ", sy=" + sy + ", totalPages=" + totalPages + ", pageIndex=" + pageIndex + ", verticalOverflow=" + verticalOverflow + ", horizontalOverflow=" + horizontalOverflow + ", aligned=" + aligned);
                    }

                    function reportCurrentPosition() {
                        try {
                            if (window.ReaderBridge) {
                                var sx = window.pageXOffset || document.documentElement.scrollLeft || document.body.scrollLeft || window.scrollX || 0;
                                var vw = getPageWidth();
                                var pageIndex = Math.round(sx / vw);
                                var totalWidth = Math.max(document.documentElement.scrollWidth, document.body.scrollWidth);
                                var totalPages = Math.max(1, Math.round(totalWidth / vw));
                                
                                var element = document.elementFromPoint(16, 50);
                                var offset = 0;
                                while (element && element !== document.body && element !== document.documentElement) {
                                    if (element.hasAttribute && element.hasAttribute('data-offset')) {
                                        offset = parseInt(element.getAttribute('data-offset')) || 0;
                                        break;
                                    }
                                    element = element.parentElement;
                                }
                                runDiagnostics();
                                window.ReaderBridge.reportPosition(offset, pageIndex, totalPages);
                            }
                        } catch (e) {
                            console.error("Error reporting position:", e);
                        }
                    }

                    function scrollToTarget(target) {
                        try {
                            window.scrollTo({
                                left: target,
                                top: 0,
                                behavior: '${if (pageAnimation == "none") "auto" else "smooth"}'
                            });
                        } catch (e) {}
                        document.body.scrollLeft = target;
                        document.documentElement.scrollLeft = target;
                        window.scroll(target, 0);
                    }

                    window.scrollToPage = function(pageIndex) {
                        var vw = getPageWidth();
                        var target = pageIndex * vw;
                        scrollToTarget(target);
                        reportCurrentPosition();
                    };

                    window.scrollToOffset = function(targetOffset) {
                        var allEls = document.querySelectorAll('[data-offset]');
                        var bestEl = null;
                        var minDiff = Infinity;
                        for (var i = 0; i < allEls.length; i++) {
                            var off = parseInt(allEls[i].getAttribute('data-offset')) || 0;
                            var diff = Math.abs(off - targetOffset);
                            if (diff < minDiff) {
                                minDiff = diff;
                                bestEl = allEls[i];
                            }
                        }
                        if (bestEl) {
                            var rect = bestEl.getBoundingClientRect();
                            var sx = window.pageXOffset || document.documentElement.scrollLeft || document.body.scrollLeft || window.scrollX || 0;
                            var vw = getPageWidth();
                            var absoluteLeft = sx + rect.left;
                            var targetPage = Math.floor(absoluteLeft / vw);
                            var target = targetPage * vw;
                            scrollToTarget(target);
                            reportCurrentPosition();
                        }
                    };

                    window.nextPage = function() {
                        var vw = getPageWidth();
                        var sx = window.pageXOffset || document.documentElement.scrollLeft || document.body.scrollLeft || window.scrollX || 0;
                        var totalWidth = Math.max(document.documentElement.scrollWidth, document.body.scrollWidth);
                        var totalPages = Math.max(1, Math.round(totalWidth / vw));
                        var currentPage = Math.round(sx / vw);
                        var targetPage = Math.min(totalPages - 1, currentPage + 1);
                        var target = targetPage * vw;
                        scrollToTarget(target);
                        setTimeout(reportCurrentPosition, 300);
                    };

                    window.prevPage = function() {
                        var vw = getPageWidth();
                        var sx = window.pageXOffset || document.documentElement.scrollLeft || document.body.scrollLeft || window.scrollX || 0;
                        var currentPage = Math.round(sx / vw);
                        var targetPage = Math.max(0, currentPage - 1);
                        var target = targetPage * vw;
                        scrollToTarget(target);
                        setTimeout(reportCurrentPosition, 300);
                    };

                    window.addEventListener('load', function() {
                        runDiagnostics();
                    });
                </script>
            </body>
            </html>
        """.trimIndent()
    }

    private fun processBookMarkupToHtml(raw: String): String {
        val sb = StringBuilder()
        var currentOffset = 0
        val lines = raw.split('\n')
        
        var inAnnotation = false
        var inPrologue = false

        for (line in lines) {
            val lineStartOffset = currentOffset
            currentOffset += line.length + 1 // +1 for newline

            if (line.isBlank()) {
                continue
            }

            var processedLine = line.trim()

            // Check for explicit form feed page break marker
            if (processedLine.contains("\u000C")) {
                processedLine = processedLine.replace("\u000C", "")
                if (sb.isNotEmpty() && !sb.endsWith("<div class=\"page-break\"></div>\n")) {
                    sb.append("<div class=\"page-break\"></div>\n")
                }
                if (processedLine.isBlank()) continue
            }

            // 1. Handle ANNOTATION tags
            if (processedLine.contains("[ANNOTATION]")) {
                inAnnotation = true
                processedLine = processedLine.replace("[ANNOTATION]", "")
                sb.append("<h1 class=\"annotation-title\" data-offset=\"$lineStartOffset\">Аннотация</h1>\n")
                if (processedLine.contains("[/ANNOTATION]")) {
                    processedLine = processedLine.replace("[/ANNOTATION]", "")
                    inAnnotation = false
                    if (processedLine.isNotBlank()) {
                        sb.append("<p data-offset=\"$lineStartOffset\">${escapeHtmlPreservingTags(processedLine)}</p>\n")
                    }
                    sb.append("<div class=\"page-break\"></div>\n")
                    continue
                } else {
                    if (processedLine.isNotBlank()) {
                        sb.append("<p data-offset=\"$lineStartOffset\">${escapeHtmlPreservingTags(processedLine)}</p>\n")
                    }
                    continue
                }
            } else if (processedLine.contains("[/ANNOTATION]")) {
                inAnnotation = false
                processedLine = processedLine.replace("[/ANNOTATION]", "")
                if (processedLine.isNotBlank()) {
                    sb.append("<p data-offset=\"$lineStartOffset\">${escapeHtmlPreservingTags(processedLine)}</p>\n")
                }
                sb.append("<div class=\"page-break\"></div>\n")
                continue
            } else if (inAnnotation) {
                sb.append("<p data-offset=\"$lineStartOffset\">${escapeHtmlPreservingTags(processedLine)}</p>\n")
                continue
            }

            // 2. Handle PROLOGUE tags
            if (processedLine.contains("[PROLOGUE]")) {
                inPrologue = true
                processedLine = processedLine.replace("[PROLOGUE]", "")
                sb.append("<h1 class=\"prologue-title\" data-offset=\"$lineStartOffset\">Пролог</h1>\n")
                if (processedLine.contains("[/PROLOGUE]")) {
                    processedLine = processedLine.replace("[/PROLOGUE]", "")
                    inPrologue = false
                    if (processedLine.isNotBlank()) {
                        sb.append("<p data-offset=\"$lineStartOffset\">${escapeHtmlPreservingTags(processedLine)}</p>\n")
                    }
                    sb.append("<div class=\"page-break\"></div>\n")
                    continue
                } else {
                    if (processedLine.isNotBlank()) {
                        sb.append("<p data-offset=\"$lineStartOffset\">${escapeHtmlPreservingTags(processedLine)}</p>\n")
                    }
                    continue
                }
            } else if (processedLine.contains("[/PROLOGUE]")) {
                inPrologue = false
                processedLine = processedLine.replace("[/PROLOGUE]", "")
                if (processedLine.isNotBlank()) {
                    sb.append("<p data-offset=\"$lineStartOffset\">${escapeHtmlPreservingTags(processedLine)}</p>\n")
                }
                sb.append("<div class=\"page-break\"></div>\n")
                continue
            } else if (inPrologue) {
                sb.append("<p data-offset=\"$lineStartOffset\">${escapeHtmlPreservingTags(processedLine)}</p>\n")
                continue
            }

            // 3. Handle CHAPTER tags / <CHAPTER> / <title> / <h1> / <h2>
            val containsChapterTag = processedLine.contains("[CHAPTER]") || processedLine.contains("<CHAPTER>") ||
                    processedLine.startsWith("<title", ignoreCase = true) || processedLine.startsWith("<h1", ignoreCase = true) ||
                    processedLine.startsWith("<h2", ignoreCase = true)

            if (containsChapterTag) {
                if (sb.isNotEmpty() && !sb.endsWith("<div class=\"page-break\"></div>\n")) {
                    sb.append("<div class=\"page-break\"></div>\n")
                }
                val cleanChapterContent = processedLine
                    .replace("[CHAPTER]", "")
                    .replace("[/CHAPTER]", "")
                    .replace("<CHAPTER>", "")
                    .replace("</CHAPTER>", "")
                    .replace(Regex("<[^>]*>"), "")
                    .trim()
                sb.append("<h1 class=\"chapter-title\" data-offset=\"$lineStartOffset\">${escapeHtmlPreservingTags(cleanChapterContent)}</h1>\n")
                continue
            }

            // 4. Standalone headings / line matches
            val lower = processedLine.lowercase()
            if (lower == "аннотация" || lower == "аннотация:" || lower == "annotation") {
                if (sb.isNotEmpty() && !sb.endsWith("<div class=\"page-break\"></div>\n")) {
                    sb.append("<div class=\"page-break\"></div>\n")
                }
                sb.append("<h1 class=\"annotation-title\" data-offset=\"$lineStartOffset\">${escapeHtmlPreservingTags(processedLine)}</h1>\n")
                inAnnotation = true
                continue
            }

            if (lower == "пролог" || lower == "пролог." || lower == "prologue") {
                if (sb.isNotEmpty() && !sb.endsWith("<div class=\"page-break\"></div>\n")) {
                    sb.append("<div class=\"page-break\"></div>\n")
                }
                sb.append("<h1 class=\"prologue-title\" data-offset=\"$lineStartOffset\">${escapeHtmlPreservingTags(processedLine)}</h1>\n")
                inPrologue = true
                continue
            }

            if (isChapterHeaderLine(processedLine)) {
                if (sb.isNotEmpty() && !sb.endsWith("<div class=\"page-break\"></div>\n")) {
                    sb.append("<div class=\"page-break\"></div>\n")
                }
                val cleanTitle = processedLine.replace(Regex("<[^>]*>"), "").trim()
                sb.append("<h1 class=\"chapter-title\" data-offset=\"$lineStartOffset\">${escapeHtmlPreservingTags(cleanTitle)}</h1>\n")
                continue
            }

            if (processedLine.contains("[CITE]")) {
                processedLine = processedLine
                    .replace("[CITE]", "<blockquote data-offset=\"$lineStartOffset\">")
                    .replace("[/CITE]", "</blockquote>")
            } else if (processedLine.contains("[SUP]")) {
                processedLine = processedLine
                    .replace("[SUP]", "<sup>")
                    .replace("[/SUP]", "</sup>")
            } else {
                // Check if it's already an HTML block or regular paragraph
                if (!processedLine.startsWith("<")) {
                    val escaped = escapeHtmlPreservingTags(processedLine)
                    processedLine = "<p data-offset=\"$lineStartOffset\">$escaped</p>"
                } else {
                    // Inject data-offset if tag doesn't have it
                    if (!processedLine.contains("data-offset=")) {
                        val firstSpace = processedLine.indexOf(' ')
                        if (firstSpace != -1) {
                            processedLine = processedLine.substring(0, firstSpace) + " data-offset=\"$lineStartOffset\"" + processedLine.substring(firstSpace)
                        } else {
                            val closeBracket = processedLine.indexOf('>')
                            if (closeBracket != -1) {
                                processedLine = processedLine.substring(0, closeBracket) + " data-offset=\"$lineStartOffset\"" + processedLine.substring(closeBracket)
                            }
                        }
                    }
                }
            }

            sb.append(processedLine).append("\n")
        }

        return sb.toString()
    }

    private fun isChapterHeaderLine(line: String): Boolean {
        val trimmed = line.replace(Regex("<[^>]*>"), "")
            .replace("[CHAPTER]", "")
            .replace("[/CHAPTER]", "")
            .trim()
        if (trimmed.isEmpty() || trimmed.length > 100) return false
        val regex = Regex("^(глава|chapter|часть|part|эпилог|epilogue|пролог|prologue|книга|book|разде?л)\\b.*", RegexOption.IGNORE_CASE)
        return regex.matches(trimmed)
    }

    private fun escapeHtmlPreservingTags(text: String): String {
        // We want to keep tags like <b>, <i>, <img>, etc. but escape raw < or > that are not part of tags if any.
        // For simplicity and robustness with book text containing emojis and unicode:
        return text
    }
}

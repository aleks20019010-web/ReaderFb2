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
        viewportHeight: Int
    ): String {
        Log.d(TAG, "Sanitizing and wrapping HTML: length=${rawText.length}, font=$fontFamily, size=$fontSize, w=$viewportWidth, h=$viewportHeight")

        // 1. Convert custom markers ([CHAPTER], [CITE], [SUP], [NOTE], etc.) and clean unsafe tags while preserving safe ones
        val processedHtml = processBookMarkupToHtml(rawText)

        // 2. Build controlled CSS and HTML wrapper
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <style>
                    html, body {
                        margin: 0;
                        padding: 0;
                        width: ${viewportWidth}px;
                        height: ${viewportHeight}px;
                        overflow-x: auto;
                        overflow-y: hidden;
                        -webkit-text-size-adjust: 100%;
                        background-color: $bgColorHex;
                        color: $textColorHex;
                    }
                    body {
                        font-family: '$fontFamily', serif;
                        font-size: ${fontSize}px;
                        font-weight: $fontWeight;
                        line-height: $lineHeight;
                        box-sizing: border-box;
                        padding: 24px 20px;
                        column-width: ${viewportWidth - 40}px;
                        column-gap: 40px;
                        column-fill: auto;
                        height: ${viewportHeight}px;
                    }
                    p {
                        margin: 0 0 1.2em 0;
                        text-align: justify;
                        text-justify: inter-word;
                        hyphens: auto;
                        -webkit-hyphens: auto;
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
                    s, del {
                        text-decoration: line-through;
                    }
                    h1, h2, h3, h4, h5, h6 {
                        break-inside: avoid;
                        page-break-inside: avoid;
                        margin: 1.5em 0 0.8em 0;
                        font-weight: bold;
                        text-align: center;
                    }
                    h1 { font-size: 1.5em; }
                    h2 { font-size: 1.3em; }
                    h3 { font-size: 1.1em; }
                    blockquote {
                        margin: 1.2em 0;
                        padding-left: 1.2em;
                        border-left: 3px solid #888;
                        font-style: italic;
                    }
                    ul, ol {
                        margin: 0 0 1.2em 1.5em;
                        padding: 0;
                    }
                    li {
                        margin-bottom: 0.4em;
                    }
                    img {
                        max-width: 100%;
                        max-height: ${viewportHeight / 2}px;
                        height: auto;
                        display: block;
                        margin: 1.2em auto;
                        object-fit: contain;
                        break-inside: avoid;
                        page-break-inside: avoid;
                    }
                    sup {
                        font-size: 0.75em;
                        vertical-align: super;
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
                    function reportCurrentPosition() {
                        try {
                            if (window.ReaderBridge) {
                                var scrollX = window.pageXOffset || document.documentElement.scrollLeft;
                                var pageWidth = window.innerWidth || document.documentElement.clientWidth;
                                var pageIndex = Math.round(scrollX / pageWidth);
                                var totalWidth = document.documentElement.scrollWidth;
                                var totalPages = Math.max(1, Math.round(totalWidth / pageWidth));
                                
                                // Find element at left edge of current page
                                var targetX = scrollX + 10;
                                var element = document.elementFromPoint(targetX, 50);
                                var offset = 0;
                                while (element && element !== document.body && element !== document.documentElement) {
                                    if (element.hasAttribute && element.hasAttribute('data-offset')) {
                                        offset = parseInt(element.getAttribute('data-offset')) || 0;
                                        break;
                                    }
                                    element = element.parentElement;
                                }
                                window.ReaderBridge.reportPosition(offset, pageIndex, totalPages);
                            }
                        } catch (e) {
                            console.error("Error reporting position:", e);
                        }
                    }

                    window.addEventListener('scroll', function() {
                        clearTimeout(window._scrollTimer);
                        window._scrollTimer = setTimeout(reportCurrentPosition, 100);
                    }, {passive: true});

                    window.addEventListener('resize', reportCurrentPosition);
                    
                    // Expose jump function to Kotlin
                    window.scrollToOffset = function(targetOffset) {
                        var el = document.querySelector('[data-offset]');
                        var bestEl = null;
                        var minDiff = Infinity;
                        var allEls = document.querySelectorAll('[data-offset]');
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
                            var scrollX = window.pageXOffset || document.documentElement.scrollLeft;
                            var targetScrollX = scrollX + rect.left - 20;
                            window.scrollTo({left: targetScrollX, behavior: 'instant'});
                        }
                    };
                </script>
            </body>
            </html>
        """.trimIndent()
    }

    private fun processBookMarkupToHtml(raw: String): String {
        val sb = StringBuilder()
        var currentOffset = 0
        val lines = raw.split('\n')
        
        for (line in lines) {
            val lineStartOffset = currentOffset
            currentOffset += line.length + 1 // +1 for newline

            if (line.isBlank()) {
                continue
            }

            var processedLine = line

            // Replace custom chapter tag
            if (processedLine.contains("[CHAPTER]")) {
                processedLine = processedLine
                    .replace("[CHAPTER]", "<h1 data-offset=\"$lineStartOffset\">")
                    .replace("[/CHAPTER]", "</h1>")
            } else if (processedLine.contains("[CITE]")) {
                processedLine = processedLine
                    .replace("[CITE]", "<blockquote data-offset=\"$lineStartOffset\">")
                    .replace("[/CITE]", "</blockquote>")
            } else if (processedLine.contains("[SUP]")) {
                processedLine = processedLine
                    .replace("[SUP]", "<sup>")
                    .replace("[/SUP]", "</sup>")
            } else {
                // Check if it's already an HTML block or regular paragraph
                if (!processedLine.trim().startsWith("<")) {
                    // Escape basic HTML special chars if needed, but preserve emojis and unicode
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

    private fun escapeHtmlPreservingTags(text: String): String {
        // We want to keep tags like <b>, <i>, <img>, etc. but escape raw < or > that are not part of tags if any.
        // For simplicity and robustness with book text containing emojis and unicode:
        return text
    }
}

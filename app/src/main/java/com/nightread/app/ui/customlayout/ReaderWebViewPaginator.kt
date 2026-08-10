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
                        padding: 20px 24px 32px 24px;
                        width: 100vw;
                        height: 100vh;
                        overflow: hidden;
                        font-family: '$fontFamily', serif;
                        font-size: ${fontSize}px;
                        font-weight: $fontWeight;
                        line-height: $lineHeight;
                        column-width: calc(100vw - 48px);
                        column-gap: 48px;
                        column-fill: auto;
                        -webkit-column-width: calc(100vw - 48px);
                        -webkit-column-gap: 48px;
                        -webkit-column-fill: auto;
                        background-color: $bgColorHex;
                        color: $textColorHex;
                    }
                    p {
                        margin-top: 0;
                        margin-bottom: 1.0em;
                        text-align: justify;
                        hyphens: auto;
                        -webkit-hyphens: auto;
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
                    h1, h2, h3, h4, h5, h6 {
                        break-inside: avoid;
                        page-break-inside: avoid;
                        -webkit-column-break-inside: avoid;
                        margin-top: 0.8em;
                        margin-bottom: 0.4em;
                        font-weight: bold;
                        text-align: center;
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
                    function runDiagnostics() {
                        var vw = window.innerWidth;
                        var vh = window.innerHeight;
                        var sw = document.documentElement.scrollWidth;
                        var sh = document.documentElement.scrollHeight;
                        var cw = document.documentElement.clientWidth;
                        var ch = document.documentElement.clientHeight;
                        var sx = window.pageXOffset || document.documentElement.scrollLeft;
                        var sy = window.pageYOffset || document.documentElement.scrollTop;
                        var totalPages = Math.max(1, Math.round(sw / vw));
                        var pageIndex = Math.round(sx / vw);
                        var verticalOverflow = sh > ch + 2;
                        var horizontalOverflow = sw > vw * totalPages + 2;
                        var aligned = Math.abs(sx % vw) < 2;
                        
                        console.log("[WEBVIEW_DIAGNOSTIC] vw=" + vw + ", vh=" + vh + ", sw=" + sw + ", sh=" + sh + ", cw=" + cw + ", ch=" + ch + ", sx=" + sx + ", sy=" + sy + ", totalPages=" + totalPages + ", pageIndex=" + pageIndex + ", verticalOverflow=" + verticalOverflow + ", horizontalOverflow=" + horizontalOverflow + ", aligned=" + aligned);
                    }

                    function reportCurrentPosition() {
                        try {
                            if (window.ReaderBridge) {
                                var sx = window.pageXOffset || document.documentElement.scrollLeft;
                                var vw = window.innerWidth;
                                var pageIndex = Math.round(sx / vw);
                                var totalWidth = document.documentElement.scrollWidth;
                                var totalPages = Math.max(1, Math.round(totalWidth / vw));
                                
                                var targetX = sx + 10;
                                var element = document.elementFromPoint(targetX, 50);
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

                    window.scrollToPage = function(pageIndex) {
                        var vw = window.innerWidth;
                        window.scrollTo({left: pageIndex * vw, behavior: 'instant'});
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
                            var sx = window.pageXOffset || document.documentElement.scrollLeft;
                            var vw = window.innerWidth;
                            var absoluteLeft = sx + rect.left;
                            var targetPage = Math.floor(absoluteLeft / vw);
                            window.scrollTo({left: targetPage * vw, behavior: 'instant'});
                            reportCurrentPosition();
                        }
                    };

                    window.nextPage = function() {
                        var vw = window.innerWidth;
                        var sx = window.pageXOffset || document.documentElement.scrollLeft;
                        var maxScroll = document.documentElement.scrollWidth - vw;
                        var target = Math.min(maxScroll, Math.round((sx + vw) / vw) * vw);
                        window.scrollTo({left: target, behavior: 'smooth'});
                        setTimeout(reportCurrentPosition, 350);
                    };

                    window.prevPage = function() {
                        var vw = window.innerWidth;
                        var sx = window.pageXOffset || document.documentElement.scrollLeft;
                        var target = Math.max(0, Math.round((sx - vw) / vw) * vw);
                        window.scrollTo({left: target, behavior: 'smooth'});
                        setTimeout(reportCurrentPosition, 350);
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

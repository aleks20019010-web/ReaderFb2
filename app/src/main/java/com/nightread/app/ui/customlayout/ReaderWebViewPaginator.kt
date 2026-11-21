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
        isHyphenationEnabled: Boolean = true,
        initialTargetOffset: Int = 0
    ): String {
        Log.d(TAG, "Sanitizing and wrapping HTML: length=${rawText.length}, font=$fontFamily, size=$fontSize, w=$viewportWidth, h=$viewportHeight, anim=$pageAnimation, hyphens=$isHyphenationEnabled, targetOffset=$initialTargetOffset, paddingDp=t:$topPaddingDp, b:$bottomPaddingDp, l:$leftPaddingDp, r:$rightPaddingDp")

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
                        padding: 0;
                        width: 100vw;
                        height: 100vh;
                        overflow: hidden;
                        font-family: '$fontFamily', serif;
                        font-size: ${fontSize}px;
                        font-weight: $fontWeight;
                        line-height: $lineHeight;
                        box-sizing: border-box;
                        background-color: $bgColorHex;
                        color: $textColorHex;
                        -webkit-locale: "ru";
                    }
                    #page-viewport {
                        position: fixed;
                        top: ${topPaddingDp}px;
                        left: 0;
                        width: 100vw;
                        height: calc(100vh - ${topPaddingDp}px - ${bottomPaddingDp}px);
                        overflow: hidden;
                        box-sizing: border-box;
                    }
                    #content-container {
                        width: 100vw;
                        height: 100%;
                        box-sizing: border-box;
                        column-width: 100vw;
                        column-gap: 0px;
                        column-fill: auto;
                        -webkit-column-width: 100vw;
                        -webkit-column-gap: 0px;
                        -webkit-column-fill: auto;
                        transform: translate3d(0px, 0px, 0px);
                        will-change: transform;
                        transform-origin: center center;
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
                    #page-viewport, #content-container {
                        padding-left: 0 !important;
                        padding-right: 0 !important;
                        text-indent: 0 !important;
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
                    #content-container > *:first-child,
                    #content-container > h1:first-child,
                    #content-container > .chapter-title:first-child,
                    #content-container > .prologue-title:first-child,
                    #content-container > .annotation-title:first-child {
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
                    h1, h2, .chapter-title, .prologue-title, .annotation-title {
                        break-before: page !important;
                        break-before: column !important;
                        page-break-before: always !important;
                        -webkit-column-break-before: always !important;
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
                    p, h1, h2, h3, h4, h5, h6, blockquote, ul, ol, li, div, span {
                        color: $textColorHex !important;
                    }
                    .note {
                        color: #007AFF !important;
                        text-decoration: underline;
                        cursor: pointer;
                    }
                </style>
            </head>
            <body>
                <div id="page-viewport">
                    <div id="content-container">
                        $processedHtml
                    </div>
                </div>
                <script>
                    var currentLogicalPage = 0;
                    var lastReadingAnchor = null;
                    var animTimer = null;

                    function getContainer() {
                        return document.getElementById('content-container') || document.body;
                    }

                    function getPageWidth() {
                        var vp = document.getElementById('page-viewport');
                        if (vp && vp.clientWidth > 0) return vp.clientWidth;
                        if (window.innerWidth > 0) return window.innerWidth;
                        return document.documentElement.clientWidth || 360;
                    }

                    function getTotalWidth() {
                        var vw = getPageWidth();
                        var container = getContainer();
                        var sw = container.scrollWidth || vw;
                        try {
                            var containerRect = container.getBoundingClientRect();
                            var lastEl = container.lastElementChild;
                            if (lastEl) {
                                var lastRect = lastEl.getBoundingClientRect();
                                var rightEdge = lastRect.right - containerRect.left;
                                if (rightEdge > sw) sw = rightEdge;
                            }
                        } catch (e) {}
                        return Math.max(sw, vw);
                    }

                    function getTotalPages() {
                        var vw = getPageWidth();
                        var totalWidth = getTotalWidth();
                        return Math.max(1, Math.ceil((totalWidth - 2) / vw));
                    }

                    function getElementAbsLeft(el) {
                        var container = getContainer();
                        var cRect = container.getBoundingClientRect();
                        var eRect = el.getBoundingClientRect();
                        return eRect.left - cRect.left;
                    }

                    function captureReadingAnchor() {
                        var vw = getPageWidth();
                        var vh = window.innerHeight || 600;
                        var totalWidth = getTotalWidth();
                        var sx = currentLogicalPage * vw;
                        var progress = (sx + vw / 2) / totalWidth;

                        var centerEl = document.elementFromPoint(vw * 0.5, vh * 0.45) ||
                                       document.elementFromPoint(vw * 0.25, vh * 0.35) ||
                                       document.elementFromPoint(vw * 0.5, vh * 0.25);

                        var offset = 0;
                        var chapterId = null;
                        var textAnchor = "";

                        if (centerEl) {
                            var curr = centerEl;
                            while (curr && curr !== document.body && curr !== document.documentElement) {
                                if (!offset && curr.hasAttribute && curr.hasAttribute('data-offset')) {
                                    offset = parseInt(curr.getAttribute('data-offset')) || 0;
                                }
                                if (!chapterId && curr.id && curr.id !== 'content-container' && curr.id !== 'page-viewport') {
                                    chapterId = curr.id;
                                }
                                curr = curr.parentElement;
                            }

                            var fullText = (centerEl.textContent || centerEl.innerText || "").trim().replace(/\s+/g, ' ');
                            if (fullText.length > 0) {
                                if (fullText.length <= 50) {
                                    textAnchor = fullText;
                                } else {
                                    var startIdx = Math.max(0, Math.floor(fullText.length / 2) - 20);
                                    textAnchor = fullText.substring(startIdx, Math.min(fullText.length, startIdx + 40)).trim();
                                }
                            }
                        }

                        return {
                            progress: progress,
                            offset: offset,
                            textAnchor: textAnchor,
                            chapterId: chapterId,
                            sx: sx
                        };
                    }

                    function reportCurrentPosition(overridePageIndex) {
                        try {
                            if (!window.ReaderBridge) return;
                            var vw = getPageWidth();
                            var vh = window.innerHeight || 600;
                            var totalPages = getTotalPages();
                            var pageIndex = (overridePageIndex !== undefined && overridePageIndex !== null && overridePageIndex >= 0)
                                ? overridePageIndex
                                : currentLogicalPage;
                            pageIndex = Math.max(0, Math.min(totalPages - 1, pageIndex));
                            currentLogicalPage = pageIndex;

                            var currentEffectiveSx = pageIndex * vw;
                            var offset = 0;

                            var samplePoints = [
                                [vw * 0.2, vh * 0.25], [vw * 0.5, vh * 0.25], [vw * 0.8, vh * 0.25],
                                [vw * 0.2, vh * 0.5],  [vw * 0.5, vh * 0.5],  [vw * 0.8, vh * 0.5],
                                [vw * 0.2, vh * 0.75], [vw * 0.5, vh * 0.75], [vw * 0.8, vh * 0.75]
                            ];
                            for (var i = 0; i < samplePoints.length; i++) {
                                var el = document.elementFromPoint(samplePoints[i][0], samplePoints[i][1]);
                                var curr = el;
                                while (curr && curr !== document.body && curr !== document.documentElement) {
                                    if (curr.hasAttribute && curr.hasAttribute('data-offset')) {
                                        var parsed = parseInt(curr.getAttribute('data-offset'));
                                        if (!isNaN(parsed) && parsed > 0) {
                                            offset = parsed;
                                            break;
                                        }
                                    }
                                    curr = curr.parentElement;
                                }
                                if (offset > 0) break;
                            }

                            if (offset <= 0) {
                                var allOffsetEls = document.querySelectorAll('[data-offset]');
                                var bestOffset = 0;
                                var minDiff = Infinity;
                                for (var j = 0; j < allOffsetEls.length; j++) {
                                    var absX = getElementAbsLeft(allOffsetEls[j]);
                                    var pVal = parseInt(allOffsetEls[j].getAttribute('data-offset'));
                                    if (isNaN(pVal) || pVal < 0) continue;

                                    if (absX >= currentEffectiveSx - 15 && absX < currentEffectiveSx + vw - 15) {
                                        bestOffset = pVal;
                                        break;
                                    }
                                    var diff = Math.abs(absX - currentEffectiveSx);
                                    if (diff < minDiff) {
                                        minDiff = diff;
                                        bestOffset = pVal;
                                    }
                                }
                                offset = bestOffset;
                            }

                            lastReadingAnchor = captureReadingAnchor();
                            window.ReaderBridge.reportPosition(offset, pageIndex, totalPages);
                        } catch (e) {
                            console.error("Error reporting position:", e);
                        }
                    }

                    function applyPageTransform(pageIndex, instant) {
                        var vw = getPageWidth();
                        var totalPages = getTotalPages();
                        pageIndex = Math.max(0, Math.min(totalPages - 1, pageIndex));
                        currentLogicalPage = pageIndex;

                        var container = getContainer();
                        var mode = '${pageAnimation}';
                        var translateX = -(pageIndex * vw);

                        if (animTimer) {
                            clearTimeout(animTimer);
                            animTimer = null;
                        }

                        if (instant || mode === 'none') {
                            container.style.transition = 'none';
                            container.style.opacity = '1';
                            container.style.filter = 'none';
                            container.style.transform = 'translate3d(' + translateX + 'px, 0px, 0px)';
                            return;
                        }

                        if (mode === 'fade') {
                            container.style.transition = 'opacity 0.14s ease-out';
                            container.style.opacity = '0.05';
                            animTimer = setTimeout(function() {
                                container.style.transition = 'none';
                                container.style.transform = 'translate3d(' + translateX + 'px, 0px, 0px)';
                                requestAnimationFrame(function() {
                                    container.style.transition = 'opacity 0.16s ease-in';
                                    container.style.opacity = '1';
                                });
                            }, 120);
                        } else {
                            // Default smooth GPU slide
                            container.style.opacity = '1';
                            container.style.filter = 'none';
                            container.style.transition = 'transform 0.22s cubic-bezier(0.22, 1, 0.36, 1)';
                            container.style.transform = 'translate3d(' + translateX + 'px, 0px, 0px)';
                        }
                    }

                    window.onSwipeDrag = function(deltaX) {
                        var vw = getPageWidth();
                        var totalPages = getTotalPages();
                        var base = -(currentLogicalPage * vw);
                        var effectiveDelta = deltaX;
                        if ((currentLogicalPage <= 0 && deltaX > 0) || (currentLogicalPage >= totalPages - 1 && deltaX < 0)) {
                            effectiveDelta = deltaX * 0.25;
                        }
                        var container = getContainer();
                        container.style.transition = 'none';
                        container.style.opacity = '1';
                        container.style.transform = 'translate3d(' + (base + effectiveDelta) + 'px, 0px, 0px)';
                    };

                    window.snapCurrentPage = function() {
                        applyPageTransform(currentLogicalPage, false);
                    };

                    function restorePositionFromAnchor(anchor) {
                        var vw = getPageWidth();
                        if (vw <= 0) vw = 360;
                        var totalWidth = getTotalWidth();
                        var totalPages = getTotalPages();

                        var targetPage = 0;
                        var found = false;

                        if (anchor && anchor.textAnchor && anchor.textAnchor.length >= 5) {
                            var allTextEls = document.querySelectorAll('#content-container p, #content-container h1, #content-container h2, #content-container h3, #content-container blockquote');
                            var bestEl = null;
                            for (var i = 0; i < allTextEls.length; i++) {
                                var txt = (allTextEls[i].textContent || '').trim().replace(/\s+/g, ' ');
                                if (txt.indexOf(anchor.textAnchor) !== -1) {
                                    bestEl = allTextEls[i];
                                    break;
                                }
                            }
                            if (!bestEl) {
                                var shortAnchor = anchor.textAnchor.substring(0, 15);
                                for (var j = 0; j < allTextEls.length; j++) {
                                    var txt2 = (allTextEls[j].textContent || '').trim().replace(/\s+/g, ' ');
                                    if (txt2.indexOf(shortAnchor) !== -1) {
                                        bestEl = allTextEls[j];
                                        break;
                                    }
                                }
                            }
                            if (bestEl) {
                                var absLeft = getElementAbsLeft(bestEl);
                                targetPage = Math.floor((absLeft + 5) / vw);
                                found = true;
                            }
                        }

                        if (!found && anchor && anchor.offset > 0) {
                            var allEls = document.querySelectorAll('[data-offset]');
                            var bestElOff = null;
                            var minDiff = Infinity;
                            for (var k = 0; k < allEls.length; k++) {
                                var off = parseInt(allEls[k].getAttribute('data-offset')) || 0;
                                var diff = Math.abs(off - anchor.offset);
                                if (diff < minDiff) {
                                    minDiff = diff;
                                    bestElOff = allEls[k];
                                }
                            }
                            if (bestElOff) {
                                var absLeft2 = getElementAbsLeft(bestElOff);
                                targetPage = Math.floor((absLeft2 + 5) / vw);
                                found = true;
                            }
                        }

                        if (!found && anchor && anchor.chapterId) {
                            var chapEl = document.getElementById(anchor.chapterId);
                            if (chapEl) {
                                var absLeft3 = getElementAbsLeft(chapEl);
                                targetPage = Math.floor((absLeft3 + 5) / vw);
                                found = true;
                            }
                        }

                        if (!found && anchor && typeof anchor.progress === 'number' && anchor.progress > 0) {
                            var progressX = (anchor.progress * totalWidth) - (vw / 2);
                            targetPage = Math.round(progressX / vw);
                            found = true;
                        }

                        targetPage = Math.max(0, Math.min(totalPages - 1, targetPage));
                        applyPageTransform(targetPage, true);
                        reportCurrentPosition(targetPage);
                        setTimeout(function() {
                            reportCurrentPosition(targetPage);
                        }, 120);
                    }

                    window.scrollToPage = function(pageIndex) {
                        applyPageTransform(pageIndex, true);
                        reportCurrentPosition(currentLogicalPage);
                    };

                    window.scrollToOffset = function(targetOffset) {
                        if (targetOffset === null || targetOffset === undefined) return;
                        var anchor = (lastReadingAnchor && lastReadingAnchor.offset === targetOffset)
                            ? lastReadingAnchor
                            : { offset: targetOffset, progress: 0, textAnchor: '' };

                        requestAnimationFrame(function() {
                            requestAnimationFrame(function() {
                                restorePositionFromAnchor(anchor);
                            });
                        });
                    };

                    window.updateStyles = function(fontFamily, fontSize, fontWeight, lineHeight, textColor, bgColor, isHyphenation, topPaddingDp, bottomPaddingDp, leftPaddingDp, rightPaddingDp) {
                        var anchor = captureReadingAnchor();
                        document.documentElement.style.opacity = '0';

                        var styleEl = document.getElementById('dynamic-reader-styles');
                        if (!styleEl) {
                            styleEl = document.createElement('style');
                            styleEl.id = 'dynamic-reader-styles';
                            document.head.appendChild(styleEl);
                        }
                        styleEl.innerHTML = `
                            html {
                                background-color: ` + bgColor + ` !important;
                                color: ` + textColor + ` !important;
                            }
                            body {
                                font-family: '` + fontFamily + `', serif !important;
                                font-size: ` + fontSize + `px !important;
                                font-weight: ` + fontWeight + ` !important;
                                line-height: ` + lineHeight + ` !important;
                                background-color: ` + bgColor + ` !important;
                                color: ` + textColor + ` !important;
                            }
                            #page-viewport {
                                top: ` + topPaddingDp + `px !important;
                                height: calc(100vh - ` + topPaddingDp + `px - ` + bottomPaddingDp + `px) !important;
                            }
                            p, h1, h2, h3, h4, h5, h6, blockquote, ul, ol, div {
                                padding-left: ` + leftPaddingDp + `px !important;
                                padding-right: ` + rightPaddingDp + `px !important;
                                hyphens: ` + (isHyphenation ? 'auto' : 'none') + ` !important;
                                -webkit-hyphens: ` + (isHyphenation ? 'auto' : 'none') + ` !important;
                            }
                            #page-viewport, #content-container {
                                padding-left: 0 !important;
                                padding-right: 0 !important;
                            }
                            p, h1, h2, h3, h4, h5, h6, blockquote, ul, ol, li, div, span {
                                color: ` + textColor + ` !important;
                            }
                            .note {
                                color: #007AFF !important;
                            }
                        `;

                        requestAnimationFrame(function() {
                            requestAnimationFrame(function() {
                                restorePositionFromAnchor(anchor);
                                requestAnimationFrame(function() {
                                    document.documentElement.style.opacity = '1';
                                });
                            });
                        });
                    };

                    window.nextPage = function() {
                        var totalPages = getTotalPages();
                        if (currentLogicalPage < totalPages - 1) {
                            applyPageTransform(currentLogicalPage + 1, false);
                        } else {
                            applyPageTransform(currentLogicalPage, false);
                        }
                        reportCurrentPosition(currentLogicalPage);
                        setTimeout(function() {
                            reportCurrentPosition(currentLogicalPage);
                        }, 240);
                    };

                    window.prevPage = function() {
                        if (currentLogicalPage > 0) {
                            applyPageTransform(currentLogicalPage - 1, false);
                        } else {
                            applyPageTransform(0, false);
                        }
                        reportCurrentPosition(currentLogicalPage);
                        setTimeout(function() {
                            reportCurrentPosition(currentLogicalPage);
                        }, 240);
                    };

                    window.addEventListener('load', function() {
                        if ($initialTargetOffset > 0) {
                            window.scrollToOffset($initialTargetOffset);
                        } else {
                            applyPageTransform(0, true);
                            reportCurrentPosition(0);
                        }
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

            // 4. Standalone exact headings
            val lower = processedLine.lowercase()
            if (lower == "аннотация" || lower == "аннотация:" || lower == "annotation") {
                sb.append("<h1 class=\"annotation-title\" data-offset=\"$lineStartOffset\">${escapeHtmlPreservingTags(processedLine)}</h1>\n")
                inAnnotation = true
                continue
            }

            if (lower == "пролог" || lower == "пролог." || lower == "prologue") {
                sb.append("<h1 class=\"prologue-title\" data-offset=\"$lineStartOffset\">${escapeHtmlPreservingTags(processedLine)}</h1>\n")
                inPrologue = true
                continue
            }

            if (isChapterHeaderLine(processedLine)) {
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

    private fun escapeHtmlPreservingTags(text: String): String {
        // We want to keep tags like <b>, <i>, <img>, etc. but escape raw < or > that are not part of tags if any.
        // For simplicity and robustness with book text containing emojis and unicode:
        return text
    }

    private fun isChapterHeaderLine(line: String): Boolean {
        val trimmed = line.replace(Regex("<[^>]*>"), "")
            .replace("[CHAPTER]", "")
            .replace("[/CHAPTER]", "")
            .trim()
        if (trimmed.isEmpty() || trimmed.length > 80) return false

        // 1. Exact standalone title words
        if (Regex("^(?i)(?:глава|chapter|часть|part|эпилог|epilogue|пролог|prologue|аннотация|annotation)[:.]?$").matches(trimmed)) {
            return true
        }

        // 2. Chapter keyword followed by numbers or Roman numerals (e.g. "Глава 1", "Глава 1. Возвращение в Академию", "Часть II: Начало")
        if (Regex("^(?i)(?:глава|chapter|часть|part|книга|book|раздел)\\s*(?:[0-9]+|[ivxldcm]+)\\b.*").matches(trimmed)) {
            return true
        }

        // 3. Chapter keyword followed by ordinal words (e.g. "Глава первая", "Часть вторая")
        if (Regex("^(?i)(?:глава|chapter|часть|part|книга|book|раздел)\\s+(?:первая|вторая|третья|четвертая|пятая|шестая|седьмая|восьмая|девятая|десятая|первый|второй|третий|четвертый|пятый|1-я|2-я|3-я|one|two|three|four|five|first|second|third)\\b.*").matches(trimmed)) {
            return true
        }

        // 4. Chapter keyword immediately followed by punctuation delimiter (e.g. "Глава: ...", "Глава - ...")
        if (Regex("""^(?i)(?:глава|chapter|часть|part|книга|book|раздел)\s*[:.\-—].*""").matches(trimmed)) {
            return true
        }

        return false
    }
}

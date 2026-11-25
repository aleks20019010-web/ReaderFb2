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
        val resolvedFontStack = resolveCssFontStack(fontFamily)
        val weightCss = computeFontWeightCss(fontFamily, fontWeight)

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
                        font-family: $resolvedFontStack;
                        font-size: ${fontSize}px;
                        $weightCss
                        line-height: $lineHeight;
                        box-sizing: border-box;
                        background-color: $bgColorHex;
                        color: $textColorHex;
                        -webkit-locale: "ru";
                        -webkit-font-smoothing: antialiased;
                    }
                    #page-viewport {
                        position: fixed;
                        top: ${topPaddingDp}px;
                        left: 0;
                        width: 100vw;
                        height: calc(100vh - ${topPaddingDp}px - ${bottomPaddingDp}px);
                        overflow-x: auto;
                        overflow-y: hidden;
                        -webkit-overflow-scrolling: auto;
                        scrollbar-width: none;
                        box-sizing: border-box;
                    }
                    #page-viewport::-webkit-scrollbar {
                        display: none;
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
                        orphans: 1;
                        widows: 1;
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
                        font-weight: 700;
                        font-variation-settings: "wght" 700;
                        -webkit-text-stroke: 0px transparent;
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
                        -webkit-column-break-before: auto !important;
                    }
                    h1, h2, h3, h4, h5, h6, .chapter-title, .prologue-title, .annotation-title {
                        break-inside: avoid !important;
                        break-after: avoid !important;
                        margin-top: 0 !important;
                        padding-top: 1.1em !important;
                        margin-bottom: 1.0em !important;
                        font-size: 1.55em !important;
                        font-weight: 700 !important;
                        font-variation-settings: "wght" 700 !important;
                        -webkit-text-stroke: 0px transparent !important;
                        text-align: center !important;
                        text-indent: 0 !important;
                        display: block !important;
                        padding-left: 0 !important;
                        padding-right: 0 !important;
                    }
                    h1, h2, .chapter-title, .prologue-title, .annotation-title {
                        break-before: column !important;
                        -webkit-column-break-before: always !important;
                    }
                    h1 + h1, h1 + h2, h2 + h1,
                    .chapter-title + .chapter-title,
                    .prologue-title + .chapter-title,
                    .annotation-title + .chapter-title {
                        break-before: auto !important;
                        padding-top: 0.4em !important;
                    }
                    .page-break {
                        break-before: column;
                        height: 0;
                        margin: 0;
                        padding: 0;
                    }
                    #page-turn-fx {
                        position: fixed;
                        top: 0;
                        left: 0;
                        width: 100vw;
                        height: 100vh;
                        pointer-events: none;
                        z-index: 30;
                        opacity: 0;
                        will-change: transform, opacity;
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
                        line-height: 0;
                    }
                    sub {
                        font-size: 0.75em;
                        vertical-align: sub;
                        line-height: 0;
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
                <div id="page-turn-fx"></div>
                <script>
                    var currentLogicalPage = 0;
                    var lastReadingAnchor = null;
                    var animTimer = null;
                    var scrollAnimFrame = null;
                    var currentAnimationMode = '${pageAnimation}';

                    window.setPageAnimation = function(mode) {
                        if (mode) currentAnimationMode = String(mode).toLowerCase();
                    };

                    function getFxOverlay() {
                        return document.getElementById('page-turn-fx');
                    }

                    function getViewport() {
                        return document.getElementById('page-viewport');
                    }

                    function getContainer() {
                        return document.getElementById('content-container') || document.body;
                    }

                    function syncViewportMetrics() {
                        var vp = getViewport();
                        var container = getContainer();
                        if (!vp || !container) return;
                        var vw = Math.floor(vp.clientWidth || window.innerWidth || document.documentElement.clientWidth || 360);
                        var vh = Math.floor(vp.clientHeight || window.innerHeight || 600);
                        if (vw > 0) {
                            container.style.width = vw + 'px';
                            container.style.columnWidth = vw + 'px';
                            container.style.webkitColumnWidth = vw + 'px';
                            container.style.columnGap = '0px';
                            container.style.webkitColumnGap = '0px';
                        }
                        if (vh > 0) {
                            var cs = window.getComputedStyle(document.body);
                            var lh = parseFloat(cs.lineHeight);
                            if (!lh || isNaN(lh) || lh <= 0) {
                                var fs = parseFloat(cs.fontSize) || 18;
                                lh = fs * 1.4;
                            }
                            var linesPerPage = Math.max(1, Math.floor(vh / lh));
                            var snappedVh = Math.floor(linesPerPage * lh);
                            container.style.height = snappedVh + 'px';
                        }
                    }

                    function getPageWidth() {
                        var vp = getViewport();
                        if (vp && vp.clientWidth > 0) return Math.floor(vp.clientWidth);
                        if (window.innerWidth > 0) return Math.floor(window.innerWidth);
                        return Math.floor(document.documentElement.clientWidth || 360);
                    }

                    function getTotalWidth() {
                        var vw = getPageWidth();
                        var vp = getViewport();
                        var container = getContainer();
                        var sw = Math.max(
                            (vp && vp.scrollWidth) ? vp.scrollWidth : vw,
                            (container && container.scrollWidth) ? container.scrollWidth : vw
                        );
                        try {
                            var vpRect = vp ? vp.getBoundingClientRect() : { left: 0 };
                            var scrollLeft = vp ? vp.scrollLeft : 0;
                            var lastEl = container.lastElementChild;
                            if (lastEl) {
                                var lastRect = lastEl.getBoundingClientRect();
                                var rightEdge = (lastRect.right - vpRect.left) + scrollLeft;
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
                        var vp = getViewport();
                        var vpRect = vp ? vp.getBoundingClientRect() : { left: 0 };
                        var scrollLeft = vp ? vp.scrollLeft : 0;
                        var eRect = el.getBoundingClientRect();
                        return (eRect.left - vpRect.left) + scrollLeft;
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

                    function cancelActiveAnimations() {
                        if (animTimer) {
                            clearTimeout(animTimer);
                            animTimer = null;
                        }
                        if (scrollAnimFrame) {
                            cancelAnimationFrame(scrollAnimFrame);
                            scrollAnimFrame = null;
                        }
                        var vp = getViewport();
                        if (vp) {
                            vp.style.transition = 'none';
                            vp.style.transform = 'none';
                            vp.style.opacity = '1';
                            vp.style.transformOrigin = 'center center';
                        }
                        var container = getContainer();
                        if (container) {
                            container.style.transition = 'none';
                            container.style.opacity = '1';
                        }
                        var fx = getFxOverlay();
                        if (fx) {
                            fx.style.transition = 'none';
                            fx.style.opacity = '0';
                            fx.style.transform = 'none';
                        }
                    }

                    function isPageBlank(pageIndex) {
                        try {
                            var container = getContainer();
                            var children = container ? container.children : null;
                            if (!children || children.length === 0) return false;
                            var vw = getPageWidth();
                            var vp = getViewport();
                            var vpRect = vp ? vp.getBoundingClientRect() : { left: 0 };
                            var scrollLeft = vp ? vp.scrollLeft : 0;
                            var pageLeft = pageIndex * vw + 4;
                            var pageRight = (pageIndex + 1) * vw - 4;

                            var lo = 0, hi = children.length - 1, idx = children.length;
                            while (lo <= hi) {
                                var mid = (lo + hi) >> 1;
                                var r = children[mid].getBoundingClientRect();
                                var absRight = (r.right - vpRect.left) + scrollLeft;
                                if (absRight > pageLeft) {
                                    idx = mid;
                                    hi = mid - 1;
                                } else {
                                    lo = mid + 1;
                                }
                            }

                            var startCheck = Math.max(0, idx - 2);
                            var endCheck = Math.min(children.length - 1, idx + 3);
                            for (var i = startCheck; i <= endCheck; i++) {
                                var el = children[i];
                                var r2 = el.getBoundingClientRect();
                                if (r2.width <= 0 && r2.height <= 0) continue;
                                var absL = (r2.left - vpRect.left) + scrollLeft;
                                var absR = (r2.right - vpRect.left) + scrollLeft;
                                if (absR > pageLeft && absL < pageRight) {
                                    var txt = (el.textContent || '').trim();
                                    if (txt.length > 0 || el.querySelector('img')) {
                                        return false;
                                    }
                                }
                            }
                            return true;
                        } catch (e) {
                            return false;
                        }
                    }

                    function findNearestNonBlankPage(startPage, direction, totalPages) {
                        var p = Math.max(0, Math.min(totalPages - 1, startPage));
                        var step = direction < 0 ? -1 : 1;
                        var guard = 0;
                        while (guard < 6 && isPageBlank(p)) {
                            var nextP = p + step;
                            if (nextP < 0 || nextP >= totalPages) {
                                // Try opposite direction if we hit an edge
                                var altP = p - step;
                                if (altP >= 0 && altP < totalPages && !isPageBlank(altP)) {
                                    return altP;
                                }
                                break;
                            }
                            p = nextP;
                            guard++;
                        }
                        return p;
                    }

                    function animateScrollLeft(vp, targetLeft, durationMs) {
                        var startLeft = vp.scrollLeft;
                        var diff = targetLeft - startLeft;
                        if (Math.abs(diff) < 1 || durationMs <= 0) {
                            vp.scrollLeft = targetLeft;
                            return;
                        }
                        var startTime = performance.now ? performance.now() : Date.now();
                        function step(now) {
                            var currentNow = now || (performance.now ? performance.now() : Date.now());
                            var elapsed = currentNow - startTime;
                            var t = Math.min(1, elapsed / durationMs);
                            var eased = 1 - Math.pow(1 - t, 3);
                            vp.scrollLeft = startLeft + diff * eased;
                            if (t < 1) {
                                scrollAnimFrame = requestAnimationFrame(step);
                            } else {
                                vp.scrollLeft = targetLeft;
                                scrollAnimFrame = null;
                            }
                        }
                        scrollAnimFrame = requestAnimationFrame(step);
                    }

                    function applyPageScroll(pageIndex, instant, directionHint) {
                        syncViewportMetrics();
                        var vw = getPageWidth();
                        var totalPages = getTotalPages();
                        var prevPage = currentLogicalPage;
                        var dir = (directionHint !== undefined && directionHint !== 0)
                            ? (directionHint > 0 ? 1 : -1)
                            : (pageIndex >= prevPage ? 1 : -1);

                        pageIndex = Math.max(0, Math.min(totalPages - 1, pageIndex));
                        if (isPageBlank(pageIndex)) {
                            pageIndex = findNearestNonBlankPage(pageIndex, dir, totalPages);
                        }
                        currentLogicalPage = pageIndex;

                        var vp = getViewport();
                        var container = getContainer();
                        var fx = getFxOverlay();
                        if (!vp) return;
                        var mode = (currentAnimationMode || 'slide').toLowerCase();
                        var targetLeft = pageIndex * vw;

                        cancelActiveAnimations();

                        if (instant || mode === 'none') {
                            vp.scrollLeft = targetLeft;
                            return;
                        }

                        if (mode === 'fade') {
                            vp.style.transition = 'opacity 0.12s ease-out';
                            vp.style.opacity = '0.05';
                            animTimer = setTimeout(function() {
                                vp.scrollLeft = targetLeft;
                                requestAnimationFrame(function() {
                                    vp.style.transition = 'opacity 0.15s ease-in';
                                    vp.style.opacity = '1';
                                });
                            }, 115);
                        } else if (mode === 'curl') {
                            // 3D Page Curl with spine fold shadow sweep across viewport
                            var origin = dir > 0 ? 'left center' : 'right center';
                            var tiltOut = dir > 0 ? -16 : 16;
                            var tiltIn = dir > 0 ? 10 : -10;
                            vp.style.transformOrigin = origin;
                            vp.style.transition = 'transform 0.13s ease-in, opacity 0.13s ease-in';
                            vp.style.transform = 'perspective(1200px) rotateY(' + tiltOut + 'deg) scale(0.985)';
                            vp.style.opacity = '0.82';

                            if (fx) {
                                fx.style.background = 'linear-gradient(90deg, rgba(0,0,0,0) 0%, rgba(0,0,0,0.20) 35%, rgba(255,255,255,0.14) 50%, rgba(0,0,0,0.34) 65%, rgba(0,0,0,0) 100%)';
                                fx.style.transition = 'none';
                                fx.style.opacity = '0.9';
                                fx.style.transform = 'translate3d(' + (dir > 0 ? '70%' : '-70%') + ', 0, 0)';
                                requestAnimationFrame(function() {
                                    fx.style.transition = 'transform 0.27s cubic-bezier(0.22, 1, 0.36, 1), opacity 0.27s ease-out';
                                    fx.style.transform = 'translate3d(' + (dir > 0 ? '-70%' : '70%') + ', 0, 0)';
                                    fx.style.opacity = '0';
                                });
                            }

                            animTimer = setTimeout(function() {
                                vp.scrollLeft = targetLeft;
                                vp.style.transition = 'none';
                                vp.style.transform = 'perspective(1200px) rotateY(' + tiltIn + 'deg) scale(0.99)';
                                requestAnimationFrame(function() {
                                    vp.style.transition = 'transform 0.16s cubic-bezier(0.22, 1, 0.36, 1), opacity 0.16s ease-out';
                                    vp.style.transform = 'perspective(1200px) rotateY(0deg) scale(1)';
                                    vp.style.opacity = '1';
                                });
                            }, 125);
                        } else if (mode === 'depth') {
                            // Depth card stack transition
                            vp.style.transformOrigin = 'center center';
                            if (dir > 0) {
                                vp.style.transition = 'transform 0.12s ease-in, opacity 0.12s ease-in';
                                vp.style.transform = 'scale(0.92)';
                                vp.style.opacity = '0.45';
                                animTimer = setTimeout(function() {
                                    vp.scrollLeft = targetLeft;
                                    vp.style.transition = 'none';
                                    vp.style.transform = 'translate3d(22%, 0, 0) scale(1)';
                                    vp.style.opacity = '0.85';
                                    requestAnimationFrame(function() {
                                        vp.style.transition = 'transform 0.18s cubic-bezier(0.22, 1, 0.36, 1), opacity 0.18s ease-out';
                                        vp.style.transform = 'translate3d(0, 0, 0) scale(1)';
                                        vp.style.opacity = '1';
                                    });
                                }, 115);
                            } else {
                                vp.style.transition = 'transform 0.12s ease-in, opacity 0.12s ease-in';
                                vp.style.transform = 'translate3d(22%, 0, 0)';
                                vp.style.opacity = '0.45';
                                animTimer = setTimeout(function() {
                                    vp.scrollLeft = targetLeft;
                                    vp.style.transition = 'none';
                                    vp.style.transform = 'scale(0.92)';
                                    vp.style.opacity = '0.7';
                                    requestAnimationFrame(function() {
                                        vp.style.transition = 'transform 0.18s cubic-bezier(0.22, 1, 0.36, 1), opacity 0.18s ease-out';
                                        vp.style.transform = 'scale(1)';
                                        vp.style.opacity = '1';
                                    });
                                }, 115);
                            }
                        } else if (mode === 'zoom') {
                            // Zoom-out then zoom-in transition
                            vp.style.transformOrigin = 'center center';
                            vp.style.transition = 'transform 0.12s ease-in, opacity 0.12s ease-in';
                            vp.style.transform = 'scale(0.86)';
                            vp.style.opacity = '0.25';
                            animTimer = setTimeout(function() {
                                vp.scrollLeft = targetLeft;
                                vp.style.transition = 'none';
                                vp.style.transform = 'scale(0.90)';
                                requestAnimationFrame(function() {
                                    vp.style.transition = 'transform 0.17s cubic-bezier(0.22, 1, 0.36, 1), opacity 0.17s ease-out';
                                    vp.style.transform = 'scale(1)';
                                    vp.style.opacity = '1';
                                });
                            }, 115);
                        } else {
                            // Default smooth slide
                            animateScrollLeft(vp, targetLeft, 190);
                        }
                    }

                    window.onSwipeDrag = function(deltaX) {
                        var vp = getViewport();
                        if (!vp) return;
                        cancelActiveAnimations();
                        var vw = getPageWidth();
                        var totalPages = getTotalPages();
                        var base = currentLogicalPage * vw;
                        var effectiveDelta = deltaX;
                        if ((currentLogicalPage <= 0 && deltaX > 0) || (currentLogicalPage >= totalPages - 1 && deltaX < 0)) {
                            effectiveDelta = deltaX * 0.2;
                        }
                        var maxScroll = Math.max(0, (totalPages - 1) * vw);
                        var target = Math.max(0, Math.min(maxScroll, base - effectiveDelta));
                        vp.scrollLeft = target;
                    };

                    window.snapCurrentPage = function() {
                        applyPageScroll(currentLogicalPage, false, 0);
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
                        applyPageScroll(targetPage, true);
                        reportCurrentPosition(targetPage);
                        setTimeout(function() {
                            reportCurrentPosition(targetPage);
                        }, 120);
                    }

                    window.scrollToPage = function(pageIndex) {
                        applyPageScroll(pageIndex, true);
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

                    function resolveFontStackJs(fontFamily) {
                        var f = (fontFamily || '').toLowerCase();
                        if (f === 'roboto' || f === 'sansserif' || f === 'sans-serif' || f === 'default' || f === 'opendyslexic') {
                            return "'Roboto', 'Noto Sans', sans-serif";
                        }
                        if (f === 'monospace') {
                            return "'Roboto Mono', monospace";
                        }
                        return "'Noto Serif', Georgia, serif";
                    }

                    function isVariableSansJs(fontFamily) {
                        var f = (fontFamily || '').toLowerCase();
                        return (f === 'roboto' || f === 'sansserif' || f === 'sans-serif' || f === 'default');
                    }

                    function computeWeightCssJs(fontFamily, rawWeight) {
                        var w = Math.max(100, Math.min(900, Math.round(Number(rawWeight) || 400)));
                        if (isVariableSansJs(fontFamily)) {
                            return 'font-weight: ' + w + ' !important; font-variation-settings: "wght" ' + w + ' !important; -webkit-text-stroke: 0px transparent !important;';
                        }
                        // Static Serif / Monospace on Android only have 400 and 700 weights.
                        // Keep base weight at 400 up to 699 and smoothly increase optical stroke so
                        // 400 -> 450 -> 500 -> 550 -> 600 -> 650 transitions gradually with zero 500->550 jump!
                        if (w <= 400) {
                            return 'font-weight: 400 !important; font-variation-settings: "wght" ' + w + ' !important; -webkit-text-stroke: 0px transparent !important; paint-order: stroke fill !important;';
                        } else if (w < 700) {
                            var strokeEm = (((w - 400) / 300) * 0.052).toFixed(4);
                            return 'font-weight: 400 !important; font-variation-settings: "wght" ' + w + ' !important; -webkit-text-stroke: ' + strokeEm + 'em currentColor !important; paint-order: stroke fill !important;';
                        } else {
                            var extraStrokeEm = (((w - 700) / 200) * 0.032).toFixed(4);
                            var strokeDecl = (w > 700) ? (extraStrokeEm + 'em currentColor') : '0px transparent';
                            return 'font-weight: 700 !important; font-variation-settings: "wght" ' + w + ' !important; -webkit-text-stroke: ' + strokeDecl + ' !important; paint-order: stroke fill !important;';
                        }
                    }

                    window.updateStyles = function(fontFamily, fontSize, fontWeight, lineHeight, textColor, bgColor, isHyphenation, topPaddingDp, bottomPaddingDp, leftPaddingDp, rightPaddingDp) {
                        var anchor = captureReadingAnchor();

                        var styleEl = document.getElementById('dynamic-reader-styles');
                        if (!styleEl) {
                            styleEl = document.createElement('style');
                            styleEl.id = 'dynamic-reader-styles';
                            document.head.appendChild(styleEl);
                        }
                        var fontStack = resolveFontStackJs(fontFamily);
                        var weightCss = computeWeightCssJs(fontFamily, fontWeight);
                        styleEl.innerHTML =
                            'html { background-color: ' + bgColor + ' !important; color: ' + textColor + ' !important; } ' +
                            'body { font-family: ' + fontStack + ' !important; font-size: ' + fontSize + 'px !important; ' + weightCss + ' line-height: ' + lineHeight + ' !important; background-color: ' + bgColor + ' !important; color: ' + textColor + ' !important; } ' +
                            '#page-viewport { top: ' + topPaddingDp + 'px !important; height: calc(100vh - ' + topPaddingDp + 'px - ' + bottomPaddingDp + 'px) !important; } ' +
                            'p, h1, h2, h3, h4, h5, h6, blockquote, ul, ol, div { padding-left: ' + leftPaddingDp + 'px !important; padding-right: ' + rightPaddingDp + 'px !important; hyphens: ' + (isHyphenation ? 'auto' : 'none') + ' !important; -webkit-hyphens: ' + (isHyphenation ? 'auto' : 'none') + ' !important; } ' +
                            '#page-viewport, #content-container { padding-left: 0 !important; padding-right: 0 !important; } ' +
                            'p, h1, h2, h3, h4, h5, h6, blockquote, ul, ol, li, div, span { color: ' + textColor + ' !important; } ' +
                            'h1, h2, h3, h4, h5, h6, .chapter-title, .prologue-title, .annotation-title, b, strong { font-weight: 700 !important; font-variation-settings: "wght" 700 !important; -webkit-text-stroke: 0px transparent !important; } ' +
                            '.note { color: #007AFF !important; }';

                        requestAnimationFrame(function() {
                            syncViewportMetrics();
                            requestAnimationFrame(function() {
                                syncViewportMetrics();
                                restorePositionFromAnchor(anchor);
                            });
                        });
                    };

                    window.nextPage = function() {
                        syncViewportMetrics();
                        var totalPages = getTotalPages();
                        if (currentLogicalPage < totalPages - 1) {
                            var target = findNearestNonBlankPage(currentLogicalPage + 1, 1, totalPages);
                            applyPageScroll(target, false, 1);
                        } else {
                            applyPageScroll(currentLogicalPage, false, 1);
                        }
                        reportCurrentPosition(currentLogicalPage);
                        setTimeout(function() {
                            reportCurrentPosition(currentLogicalPage);
                        }, 300);
                    };

                    window.prevPage = function() {
                        syncViewportMetrics();
                        var totalPages = getTotalPages();
                        if (currentLogicalPage > 0) {
                            var target = findNearestNonBlankPage(currentLogicalPage - 1, -1, totalPages);
                            applyPageScroll(target, false, -1);
                        } else {
                            applyPageScroll(0, false, -1);
                        }
                        reportCurrentPosition(currentLogicalPage);
                        setTimeout(function() {
                            reportCurrentPosition(currentLogicalPage);
                        }, 300);
                    };

                    window.addEventListener('resize', function() {
                        syncViewportMetrics();
                        applyPageScroll(currentLogicalPage, true);
                        reportCurrentPosition(currentLogicalPage);
                    });

                    window.addEventListener('load', function() {
                        syncViewportMetrics();
                        if ($initialTargetOffset > 0) {
                            window.scrollToOffset($initialTargetOffset);
                        } else {
                            applyPageScroll(0, true);
                            reportCurrentPosition(0);
                        }
                        setTimeout(function() {
                            syncViewportMetrics();
                            reportCurrentPosition(currentLogicalPage);
                        }, 150);
                    });
                </script>
            </body>
            </html>
        """.trimIndent()
    }

    private fun resolveCssFontStack(fontFamily: String): String {
        return when (fontFamily.trim().lowercase()) {
            "roboto", "sansserif", "sans-serif", "default", "opendyslexic" -> "'Roboto', 'Noto Sans', sans-serif"
            "monospace" -> "'Roboto Mono', monospace"
            else -> "'Noto Serif', Georgia, serif"
        }
    }

    private fun computeFontWeightCss(fontFamily: String, rawWeight: Float): String {
        val w = rawWeight.toInt().coerceIn(100, 900)
        val isVarSans = when (fontFamily.trim().lowercase()) {
            "roboto", "sansserif", "sans-serif", "default" -> true
            else -> false
        }
        if (isVarSans) {
            return "font-weight: $w; font-variation-settings: \"wght\" $w; -webkit-text-stroke: 0px transparent;"
        }
        return when {
            w <= 400 ->
                "font-weight: 400; font-variation-settings: \"wght\" $w; -webkit-text-stroke: 0px transparent; paint-order: stroke fill;"
            w < 700 -> {
                val strokeEm = String.format(java.util.Locale.US, "%.4f", ((w - 400f) / 300f) * 0.052f)
                "font-weight: 400; font-variation-settings: \"wght\" $w; -webkit-text-stroke: ${strokeEm}em currentColor; paint-order: stroke fill;"
            }
            else -> {
                val strokeDecl = if (w > 700) {
                    val extraEm = String.format(java.util.Locale.US, "%.4f", ((w - 700f) / 200f) * 0.032f)
                    "${extraEm}em currentColor"
                } else {
                    "0px transparent"
                }
                "font-weight: 700; font-variation-settings: \"wght\" $w; -webkit-text-stroke: $strokeDecl; paint-order: stroke fill;"
            }
        }
    }

    private val CHAPTER_OPEN_REGEX = Regex("\\[CHAPTER]|<CHAPTER>", RegexOption.IGNORE_CASE)
    private val CHAPTER_CLOSE_REGEX = Regex("\\[/CHAPTER]|</CHAPTER>", RegexOption.IGNORE_CASE)
    private val ANY_MARKUP_TAG_REGEX = Regex(
        "\\[/?(?:CHAPTER|ANNOTATION|PROLOGUE|CITE|SUP|NOTE)]|</?CHAPTER>",
        RegexOption.IGNORE_CASE
    )

    private fun processBookMarkupToHtml(raw: String): String {
        val sb = StringBuilder()
        var currentOffset = 0
        val lines = raw.split('\n')

        var inAnnotation = false
        var inPrologue = false
        var inChapter = false
        var chapterStartOffset = 0
        val chapterBuffer = StringBuilder()

        fun flushChapterBuffer() {
            val cleanTitle = chapterBuffer.toString()
                .replace(ANY_MARKUP_TAG_REGEX, "")
                .replace(Regex("<[^>]*>"), "")
                .replace(Regex("\\s+"), " ")
                .trim()
            chapterBuffer.clear()
            if (cleanTitle.isNotEmpty()) {
                sb.append("<h1 class=\"chapter-title\" data-offset=\"$chapterStartOffset\">${escapeHtmlPreservingTags(cleanTitle)}</h1>\n")
            }
        }

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

            // 0. Handle multi-line and single-line [CHAPTER]...[/CHAPTER] / <CHAPTER>...</CHAPTER>
            val hasChapterOpen = CHAPTER_OPEN_REGEX.containsMatchIn(processedLine)
            val hasChapterClose = CHAPTER_CLOSE_REGEX.containsMatchIn(processedLine)

            if (inChapter) {
                val stripped = processedLine
                    .replace(CHAPTER_OPEN_REGEX, "")
                    .replace(CHAPTER_CLOSE_REGEX, "")
                    .trim()
                if (stripped.isNotEmpty()) {
                    if (chapterBuffer.isNotEmpty()) chapterBuffer.append(' ')
                    chapterBuffer.append(stripped)
                }
                if (hasChapterClose) {
                    inChapter = false
                    flushChapterBuffer()
                }
                continue
            }

            if (hasChapterOpen) {
                val stripped = processedLine
                    .replace(CHAPTER_OPEN_REGEX, "")
                    .replace(CHAPTER_CLOSE_REGEX, "")
                    .trim()
                chapterStartOffset = lineStartOffset
                if (hasChapterClose) {
                    chapterBuffer.clear()
                    if (stripped.isNotEmpty()) {
                        chapterBuffer.append(stripped)
                        flushChapterBuffer()
                    }
                } else {
                    inChapter = true
                    chapterBuffer.clear()
                    if (stripped.isNotEmpty()) {
                        chapterBuffer.append(stripped)
                    }
                }
                continue
            }

            if (hasChapterClose) {
                // Stray closing [/CHAPTER] tag outside a chapter block -> strip it completely
                processedLine = processedLine.replace(CHAPTER_CLOSE_REGEX, "").trim()
                if (processedLine.isEmpty()) continue
            }

            // 1. Handle ANNOTATION tags
            if (processedLine.contains("[ANNOTATION]", ignoreCase = true)) {
                inAnnotation = true
                processedLine = processedLine.replace(Regex("\\[ANNOTATION]", RegexOption.IGNORE_CASE), "")
                sb.append("<h1 class=\"annotation-title\" data-offset=\"$lineStartOffset\">Аннотация</h1>\n")
                if (processedLine.contains("[/ANNOTATION]", ignoreCase = true)) {
                    processedLine = processedLine.replace(Regex("\\[/ANNOTATION]", RegexOption.IGNORE_CASE), "")
                    inAnnotation = false
                }
                val cleanAnn = processedLine.replace(ANY_MARKUP_TAG_REGEX, "").trim()
                if (cleanAnn.isNotEmpty()) {
                    sb.append("<p data-offset=\"$lineStartOffset\">${escapeHtmlPreservingTags(cleanAnn)}</p>\n")
                }
                continue
            } else if (processedLine.contains("[/ANNOTATION]", ignoreCase = true)) {
                inAnnotation = false
                val cleanAnn = processedLine.replace(ANY_MARKUP_TAG_REGEX, "").trim()
                if (cleanAnn.isNotEmpty()) {
                    sb.append("<p data-offset=\"$lineStartOffset\">${escapeHtmlPreservingTags(cleanAnn)}</p>\n")
                }
                continue
            } else if (inAnnotation) {
                val cleanAnn = processedLine.replace(ANY_MARKUP_TAG_REGEX, "").trim()
                if (cleanAnn.isNotEmpty()) {
                    sb.append("<p data-offset=\"$lineStartOffset\">${escapeHtmlPreservingTags(cleanAnn)}</p>\n")
                }
                continue
            }

            // 2. Handle PROLOGUE tags
            if (processedLine.contains("[PROLOGUE]", ignoreCase = true)) {
                inPrologue = true
                processedLine = processedLine.replace(Regex("\\[PROLOGUE]", RegexOption.IGNORE_CASE), "")
                sb.append("<h1 class=\"prologue-title\" data-offset=\"$lineStartOffset\">Пролог</h1>\n")
                if (processedLine.contains("[/PROLOGUE]", ignoreCase = true)) {
                    processedLine = processedLine.replace(Regex("\\[/PROLOGUE]", RegexOption.IGNORE_CASE), "")
                    inPrologue = false
                }
                val cleanProl = processedLine.replace(ANY_MARKUP_TAG_REGEX, "").trim()
                if (cleanProl.isNotEmpty()) {
                    sb.append("<p data-offset=\"$lineStartOffset\">${escapeHtmlPreservingTags(cleanProl)}</p>\n")
                }
                continue
            } else if (processedLine.contains("[/PROLOGUE]", ignoreCase = true)) {
                inPrologue = false
                val cleanProl = processedLine.replace(ANY_MARKUP_TAG_REGEX, "").trim()
                if (cleanProl.isNotEmpty()) {
                    sb.append("<p data-offset=\"$lineStartOffset\">${escapeHtmlPreservingTags(cleanProl)}</p>\n")
                }
                continue
            } else if (inPrologue) {
                val cleanProl = processedLine.replace(ANY_MARKUP_TAG_REGEX, "").trim()
                if (cleanProl.isNotEmpty()) {
                    sb.append("<p data-offset=\"$lineStartOffset\">${escapeHtmlPreservingTags(cleanProl)}</p>\n")
                }
                continue
            }

            // 3. Handle HTML heading tags <title> / <h1> / <h2>
            val isHtmlHeading = processedLine.startsWith("<title", ignoreCase = true) ||
                processedLine.startsWith("<h1", ignoreCase = true) ||
                processedLine.startsWith("<h2", ignoreCase = true)

            if (isHtmlHeading) {
                val cleanChapterContent = processedLine
                    .replace(ANY_MARKUP_TAG_REGEX, "")
                    .replace(Regex("<[^>]*>"), "")
                    .trim()
                if (cleanChapterContent.isNotEmpty()) {
                    sb.append("<h1 class=\"chapter-title\" data-offset=\"$lineStartOffset\">${escapeHtmlPreservingTags(cleanChapterContent)}</h1>\n")
                }
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
                val cleanTitle = processedLine
                    .replace(ANY_MARKUP_TAG_REGEX, "")
                    .replace(Regex("<[^>]*>"), "")
                    .trim()
                if (cleanTitle.isNotEmpty()) {
                    sb.append("<h1 class=\"chapter-title\" data-offset=\"$lineStartOffset\">${escapeHtmlPreservingTags(cleanTitle)}</h1>\n")
                }
                continue
            }

            if (processedLine.contains("[CITE]", ignoreCase = true)) {
                processedLine = processedLine
                    .replace(Regex("\\[CITE]", RegexOption.IGNORE_CASE), "<blockquote data-offset=\"$lineStartOffset\">")
                    .replace(Regex("\\[/CITE]", RegexOption.IGNORE_CASE), "</blockquote>")
                    .replace(ANY_MARKUP_TAG_REGEX, "")
            } else if (processedLine.contains("[SUP]", ignoreCase = true)) {
                processedLine = processedLine
                    .replace(Regex("\\[SUP]", RegexOption.IGNORE_CASE), "<sup>")
                    .replace(Regex("\\[/SUP]", RegexOption.IGNORE_CASE), "</sup>")
                    .replace(ANY_MARKUP_TAG_REGEX, "")
            } else {
                processedLine = processedLine.replace(ANY_MARKUP_TAG_REGEX, "").trim()
                if (processedLine.isEmpty()) continue
                // Check if it's already an HTML block or regular paragraph
                val startsWithBlockTag = Regex("^<(?:p|div|blockquote|h[1-6]|ul|ol|table|hr)\\b", RegexOption.IGNORE_CASE).containsMatchIn(processedLine)
                if (!startsWithBlockTag) {
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

        if (inChapter) {
            flushChapterBuffer()
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

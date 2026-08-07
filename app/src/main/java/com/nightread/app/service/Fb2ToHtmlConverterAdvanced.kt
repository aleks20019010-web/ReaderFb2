package com.nightread.app.service

import android.util.Log
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayInputStream
import java.util.ArrayDeque
import javax.xml.parsers.SAXParserFactory

object Fb2ToHtmlConverterAdvanced {

    private const val TAG = "Fb2Converter"
    private const val MAX_BINARY_SIZE_CHARS = 5_000_000 // Limit ~3.7MB binary (~5MB base64) to prevent OOM

    fun convert(
        fb2Xml: String,
        theme: String,
        fontSize: Float,
        lineSpacing: Float,
        fontFamily: String,
        fontWeight: Int,
        fontAlignment: String,
        pageMargins: Boolean,
        paddingTop: Int,
        paddingBottom: Int,
        paddingLeft: Int,
        paddingRight: Int,
        paragraphIndent: Int = 12
    ): String {
        try {
            val factory = SAXParserFactory.newInstance()
            val saxParser = factory.newSAXParser()
            val handler = Fb2SaxHandler()
            
            val inputStream = ByteArrayInputStream(fb2Xml.toByteArray(Charsets.UTF_8))
            saxParser.parse(inputStream, handler)

            // Dynamic Font Style mapping
            val cssFontFamily = when (fontFamily) {
                "EB Garamond" -> "'EB Garamond', serif"
                "Literata" -> "'Literata', serif"
                "Lora" -> "'Lora', serif"
                "Roboto", "Sans Serif" -> "'Roboto', sans-serif"
                "Serif", "Times New Roman" -> "serif"
                "Monospace" -> "monospace"
                else -> "sans-serif"
            }

            // Theme colors mapping
            val (bgColor, textColor) = when (theme.lowercase()) {
                "light", "beige" -> "#FFFBF0" to "#1A1A1A"
                "sepia", "sepia_contrast" -> "#F4ECD8" to "#5C4033"
                "dark", "contrast" -> "#121212" to "#E0E0E0"
                "amoled" -> "#000000" to "#FFFFFF"
                else -> "#FFFBF0" to "#1A1A1A"
            }

            // Margin/padding setup
            val sideMarginPx = if (pageMargins) paddingLeft else 0
            val sideMargin = "${sideMarginPx}px"
            val columnWidthCss = "calc(100vw - ${sideMarginPx * 2}px)"
            val columnGapCss = "${sideMarginPx * 2}px"
            val topMargin = "${paddingTop}px"
            val bottomMargin = "${paddingBottom}px"
            val paragraphIndentCss = "${paragraphIndent}px"
            val fontWeightCss = fontWeight.toString()

            val htmlContent = handler.getHtml()

            return """
                <!DOCTYPE html>
                <html lang="ru">
                <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
                    <style>
                        :root {
                            --bg-color: $bgColor;
                            --text-color: $textColor;
                            --top-margin: $topMargin;
                            --bottom-margin: $bottomMargin;
                            --side-margin: $sideMargin;
                            --column-width: $columnWidthCss;
                            --column-gap: $columnGapCss;
                            --font-family: $cssFontFamily;
                            --font-size: ${fontSize}px;
                            --font-weight: $fontWeightCss;
                            --line-spacing: $lineSpacing;
                            --text-align: ${fontAlignment.lowercase()};
                            --paragraph-indent: $paragraphIndentCss;
                        }
                        html {
                            margin: 0;
                            padding: 0;
                            width: 100%;
                            height: 100%;
                            background-color: var(--bg-color);
                            overflow: hidden;
                        }
                        body {
                            margin: 0;
                            padding: 0;
                            width: 100vw;
                            height: 100vh;
                            background-color: var(--bg-color);
                            overflow: hidden;
                            -webkit-user-select: none;
                            user-select: none;
                            -webkit-touch-callout: none;
                        }
                        #column-container {
                            margin: 0;
                            padding-top: var(--top-margin);
                            padding-bottom: var(--bottom-margin);
                            padding-left: var(--side-margin);
                            padding-right: var(--side-margin);
                            height: 100vh;
                            box-sizing: border-box;
                            -webkit-column-width: var(--column-width);
                            -webkit-column-gap: var(--column-gap);
                            column-width: var(--column-width);
                            column-gap: var(--column-gap);
                            -webkit-column-fill: auto;
                            column-fill: auto;
                            color: var(--text-color);
                            font-family: var(--font-family);
                            font-size: var(--font-size);
                            font-weight: var(--font-weight);
                            line-height: var(--line-spacing);
                            text-align: var(--text-align);
                            -webkit-hyphens: auto;
                            -ms-hyphens: auto;
                            hyphens: auto;
                            orphans: 1;
                            widows: 1;
                        }
                        #column-container * {
                            max-width: 100% !important;
                            box-sizing: border-box !important;
                            word-wrap: break-word !important;
                            overflow-wrap: break-word !important;
                        }
                        #column-container p,
                        #column-container div,
                        #column-container section,
                        #column-container article,
                        #column-container blockquote,
                        #column-container h1,
                        #column-container h2,
                        #column-container h3,
                        #column-container h4,
                        #column-container h5,
                        #column-container h6 {
                            margin-left: 0 !important;
                            margin-right: 0 !important;
                            padding-left: 0 !important;
                            padding-right: 0 !important;
                            max-width: 100% !important;
                            width: auto !important;
                        }
                        #column-container img,
                        #column-container svg,
                        #column-container image,
                        #column-container table,
                        #column-container iframe {
                            max-width: 100% !important;
                            height: auto !important;
                            margin-left: auto !important;
                            margin-right: auto !important;
                            display: block !important;
                        }
                        p {
                            margin-top: 0;
                            margin-bottom: 0.2em;
                            text-indent: var(--paragraph-indent);
                            text-align: justify;
                            max-width: 100%;
                            box-sizing: border-box;
                        }
                        h1, h2, h3, h4, h5, h6 {
                            margin-top: 1em;
                            margin-bottom: 0.5em;
                            font-weight: bold;
                            text-align: center;
                            page-break-after: avoid;
                            break-after: avoid;
                            text-indent: 0 !important;
                        }
                        h1 { 
                            font-size: 1.6em; 
                            margin-top: 0.5em;
                            margin-bottom: 1.2em;
                        }
                        h2 { font-size: 1.3em; }
                        h3 { font-size: 1.2em; }
                        
                        /* FB2 Structural & Chapter Breaks */
                        .fb2-section { 
                            margin-bottom: 1.5em; 
                        }
                        .chapter-section {
                            break-before: column;
                            -webkit-column-break-before: always;
                        }
                        
                        /* Poetry */
                        .poem { 
                            margin: 1em 0 1em 1.5em;
                            font-style: italic;
                        }
                        .stanza { 
                            margin-bottom: 0.8em; 
                        }
                        .verse { 
                            padding-left: 0.5em;
                            text-indent: 0 !important;
                        }
                        .poem-title {
                            font-weight: bold;
                            text-align: center;
                            margin: 0.5em 0;
                            text-indent: 0 !important;
                        }
                        
                        /* Epigraphs & Citations */
                        .epigraph {
                            margin: 1.5em 1em 1.5em 2em;
                            font-style: italic;
                            text-align: right;
                        }
                        .epigraph-title {
                            font-weight: bold;
                            text-align: right;
                            margin-bottom: 0.5em;
                            text-indent: 0 !important;
                        }
                        .cite {
                            margin: 1em 1.5em;
                            padding-left: 1em;
                            border-left: 3px solid var(--text-color);
                            opacity: 0.85;
                        }
                        .annotation {
                            margin: 1em 1.5em;
                            font-style: italic;
                            opacity: 0.9;
                        }
                        
                        /* Footnotes */
                        .footnote-ref {
                            font-size: 0.8em;
                            vertical-align: super;
                            text-decoration: none;
                            color: var(--text-color);
                            font-weight: bold;
                            padding: 0 2px;
                        }
                        .footnote {
                            font-size: 0.85em;
                            margin-top: 0.5em;
                            padding: 0.5em 1em;
                            border-top: 1px solid var(--text-color);
                            opacity: 0.75;
                        }
                        
                        /* Tables */
                        .fb2-table {
                            width: 100%;
                            border-collapse: collapse;
                            margin: 1em 0;
                        }
                        .fb2-table td, .fb2-table th {
                            border: 1px solid var(--text-color);
                            padding: 4px 8px;
                            font-size: 0.9em;
                        }
                        
                        strong { font-weight: bold; }
                        em { font-style: italic; }
                        img {
                            max-width: 100%;
                            height: auto;
                            display: block;
                            margin: 12px auto;
                            box-shadow: 0 2px 5px rgba(0,0,0,0.15);
                        }
                        ::-webkit-scrollbar {
                            display: none;
                        }
                        body.antiglare-active {
                            font-weight: 800 !important;
                            text-shadow: 0.5px 0 0 currentColor, -0.5px 0 0 currentColor !important;
                        }
                    </style>
                    <script type="text/javascript">
                        function applyAntiGlare(active, normalTextColor) {
                            if (active) {
                                document.body.classList.add('antiglare-active');
                                var isDark = document.body.style.backgroundColor === 'rgb(0, 0, 0)' || document.body.style.backgroundColor === 'rgb(26, 26, 26)' || document.body.style.backgroundColor === '#000000' || document.body.style.backgroundColor === '#1A1A1A';
                                document.body.style.color = isDark ? '#FFFFFF' : '#000000';
                            } else {
                                document.body.classList.remove('antiglare-active');
                                document.body.style.color = normalTextColor;
                            }
                        }

                        function applyThemeChange(newBg, newText, duration) {
                            var start = performance.now();
                            var oldBg = getComputedStyle(document.body).backgroundColor || '#FFFBF0';
                            var oldText = getComputedStyle(document.body).color || '#1A1A1A';
                            
                            document.body.style.transition = 'color ' + duration + 'ms ease-in-out';
                            document.body.style.color = newText;
                            
                            var textElements = document.querySelectorAll('p, h1, h2, h3, h4, h5, h6, span');
                            for (var i = 0; i < textElements.length; i++) {
                                textElements[i].style.transition = 'color ' + duration + 'ms ease-in-out';
                                textElements[i].style.color = newText;
                            }
                            
                            function step(now) {
                                var elapsed = now - start;
                                var progress = Math.min(elapsed / duration, 1.0);
                                var easeProgress = Math.sin(progress * Math.PI / 2); // sine ease-out
                                var radiusPercent = easeProgress * 150;
                                
                                var grad = 'radial-gradient(circle at center, ' + newBg + ' ' + radiusPercent + '%, ' + oldBg + ' ' + radiusPercent + '%)';
                                document.body.style.background = grad;
                                document.documentElement.style.background = grad;
                                
                                if (progress < 1.0) {
                                    requestAnimationFrame(step);
                                } else {
                                    document.body.style.background = newBg;
                                    document.body.style.backgroundColor = newBg;
                                    document.documentElement.style.background = newBg;
                                    document.documentElement.style.backgroundColor = newBg;
                                    
                                    document.body.style.transition = '';
                                    for (var i = 0; i < textElements.length; i++) {
                                        textElements[i].style.transition = '';
                                    }
                                }
                            }
                            requestAnimationFrame(step);
                        }

                        function applyFontChange(newFamily, newSize, newLineSpacing, newAlign, newWeight) {
                            var elements = document.querySelectorAll('p, h1, h2, h3, h4, h5, h6');
                            var pageWidth = window.innerWidth || document.documentElement.clientWidth;
                            var targetElement = null;
                            for (var i = 0; i < elements.length; i++) {
                                var rect = elements[i].getBoundingClientRect();
                                if (rect.right > 5 && rect.left < pageWidth) {
                                    targetElement = elements[i];
                                    break;
                                }
                            }

                            document.body.style.transition = 'font-size 0.5s ease-in-out, line-height 0.5s ease-in-out';
                            
                            var cssFontFamily = newFamily;
                            if (newFamily === "EB Garamond") cssFontFamily = "'EB Garamond', serif";
                            else if (newFamily === "Literata") cssFontFamily = "'Literata', serif";
                            else if (newFamily === "Lora") cssFontFamily = "'Lora', serif";
                            else if (newFamily === "Roboto" || newFamily === "Sans Serif") cssFontFamily = "'Roboto', sans-serif";
                            else if (newFamily === "Serif" || newFamily === "Times New Roman") cssFontFamily = "serif";
                            else if (newFamily === "Monospace") cssFontFamily = "monospace";
                            
                            document.body.style.fontFamily = cssFontFamily;
                            document.body.style.fontSize = newSize + 'px';
                            document.body.style.lineHeight = newLineSpacing;
                            document.body.style.textAlign = newAlign.toLowerCase();
                            document.body.style.fontWeight = newWeight > 0 ? 'bold' : 'normal';

                            if (targetElement) {
                                var startTime = performance.now();
                                var duration = 500;
                                
                                function lockScroll(now) {
                                    var elapsed = now - startTime;
                                    var rect = targetElement.getBoundingClientRect();
                                    var scrollLeft = window.pageXOffset || document.documentElement.scrollLeft || document.body.scrollLeft;
                                    var targetX = scrollLeft + rect.left;
                                    var pw = window.innerWidth || document.documentElement.clientWidth;
                                    if (pw > 0) {
                                        var pageIndex = Math.floor(targetX / pw);
                                        window.scrollTo(pageIndex * pw, 0);
                                    }
                                    
                                    if (elapsed < duration) {
                                        requestAnimationFrame(lockScroll);
                                    } else {
                                        calculatePages();
                                        reportCurrentParagraph();
                                        document.body.style.transition = '';
                                    }
                                }
                                requestAnimationFrame(lockScroll);
                            } else {
                                setTimeout(function() {
                                    calculatePages();
                                    reportCurrentParagraph();
                                }, 500);
                            }
                        }

                        function calculatePages() {
                            var totalWidth = Math.max(
                                document.body.scrollWidth || 0,
                                document.documentElement.scrollWidth || 0,
                                document.body.offsetWidth || 0
                            );
                            var pageWidth = window.innerWidth || document.documentElement.clientWidth;
                            if (pageWidth > 0) {
                                var pages = Math.max(1, Math.round(totalWidth / pageWidth));
                                if (typeof AndroidInterface !== 'undefined' && AndroidInterface.onPagesCalculated) {
                                    AndroidInterface.onPagesCalculated(pages);
                                }
                            }
                        }

                        function reportCurrentParagraph() {
                            var elements = document.querySelectorAll('p, h1, h2, h3, h4, h5, h6');
                            var scrollLeft = window.pageXOffset || document.documentElement.scrollLeft || document.body.scrollLeft;
                            var pageWidth = window.innerWidth || document.documentElement.clientWidth;
                            
                            for (var i = 0; i < elements.length; i++) {
                                var rect = elements[i].getBoundingClientRect();
                                if (rect.right > 5 && rect.left < pageWidth) {
                                    if (typeof AndroidInterface !== 'undefined' && AndroidInterface.onParagraphVisible) {
                                        AndroidInterface.onParagraphVisible(elements[i].id);
                                    }
                                    break;
                                }
                            }
                        }

                        function scrollToParagraph(pId) {
                            var element = document.getElementById(pId);
                            if (element) {
                                var rect = element.getBoundingClientRect();
                                var scrollLeft = window.pageXOffset || document.documentElement.scrollLeft || document.body.scrollLeft;
                                var targetX = scrollLeft + rect.left;
                                var pageWidth = window.innerWidth || document.documentElement.clientWidth;
                                if (pageWidth > 0) {
                                    var pageIndex = Math.floor(targetX / pageWidth);
                                    scrollToPage(pageIndex);
                                    if (typeof AndroidInterface !== 'undefined' && AndroidInterface.onPageRestored) {
                                        AndroidInterface.onPageRestored(pageIndex);
                                    }
                                    return true;
                                }
                            }
                            return false;
                        }

                        function scrollToPage(pageIndex) {
                            var pageWidth = window.innerWidth || document.documentElement.clientWidth;
                            if (pageWidth <= 0) return;
                            var x = Math.round(pageIndex * pageWidth);
                            window.scrollTo(x, 0);
                            document.body.scrollLeft = x;
                            document.documentElement.scrollLeft = x;
                        }

                        window.onscroll = function() {
                            reportCurrentParagraph();
                        };
                        
                        window.onload = function() {
                            setTimeout(calculatePages, 200);
                            setTimeout(reportCurrentParagraph, 300);
                        };

                        window.onresize = function() {
                            setTimeout(calculatePages, 200);
                            setTimeout(reportCurrentParagraph, 300);
                        };
                    </script>
                </head>
                <body>
                    <div id="column-container">
                        $htmlContent
                    </div>
                </body>
                </html>
            """.trimIndent()
        } catch (e: Exception) {
            Log.e(TAG, "Error rendering FB2", e)
            return "<html><body>Error rendering FB2: ${e.message}</body></html>"
        }
    }

    private class Fb2SaxHandler : DefaultHandler() {
        private val html = StringBuilder(1024 * 1024)
        private val binaryMap = mutableMapOf<String, String>()
        private val currentText = StringBuilder()
        private var paragraphCounter = 0
        private var noteCounter = 0
        private var sectionDepth = 0
        
        private var insideBody = false
        private var insideBinary = false
        private var insidePoem = false
        private var insideEpigraph = false
        private var currentBinaryId = ""
        private val currentBinaryContent = StringBuilder()
        private val tagStack = ArrayDeque<String>()

        private fun escapeAndAppendText(text: String) {
            for (i in 0 until text.length) {
                when (val c = text[i]) {
                    '<' -> html.append("&lt;")
                    '>' -> html.append("&gt;")
                    '&' -> html.append("&amp;")
                    '"' -> html.append("&quot;")
                    '\'' -> html.append("&apos;")
                    '\u00A0' -> html.append("&nbsp;")
                    else -> {
                        if (c.code < 32 && c != '\n' && c != '\r' && c != '\t') {
                            continue
                        }
                        html.append(c)
                    }
                }
            }
        }

        private fun flushText() {
            if (currentText.isEmpty()) return
            val text = currentText.toString()
            currentText.setLength(0)
            escapeAndAppendText(text)
        }

        private fun processAndStoreBinary() {
            if (currentBinaryId.isEmpty()) return
            val content = currentBinaryContent.toString().trim()
            currentBinaryContent.setLength(0)

            if (content.length > MAX_BINARY_SIZE_CHARS) {
                Log.w(TAG, "Binary image '$currentBinaryId' too large (${content.length} chars), skipping")
                return
            }

            val cleanBase64 = content.replace("\n", "").replace("\r", "").replace(" ", "")
            if (cleanBase64.isNotEmpty()) {
                binaryMap[currentBinaryId] = cleanBase64
            }
        }

        fun getHtml(): String {
            flushText()
            var rawHtml = html.toString()
            // Replace image placeholders with actual base64 data
            for ((id, base64) in binaryMap) {
                rawHtml = rawHtml.replace("IMAGE_ID:$id", "data:image/jpeg;base64,$base64")
            }
            return rawHtml
        }

        override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
            val element = qName?.lowercase() ?: ""
            
            if (element == "body") {
                insideBody = true
                return
            }
            if (element == "binary") {
                insideBinary = true
                currentBinaryId = attributes?.getValue("id") ?: ""
                currentBinaryContent.setLength(0)
                return
            }
            
            if (!insideBody) return
            
            flushText()
            
            when (element) {
                "section" -> {
                    sectionDepth++
                    html.append("<section class='fb2-section chapter-section'>")
                    tagStack.push("section")
                }
                "p" -> {
                    html.append("<p id=\"p_$paragraphCounter\">")
                    paragraphCounter++
                    tagStack.push("p")
                }
                "title" -> {
                    val hTag = when {
                        insidePoem -> "div class='poem-title'"
                        insideEpigraph -> "div class='epigraph-title'"
                        sectionDepth > 1 -> "h2 id=\"p_$paragraphCounter\""
                        else -> "h1 id=\"p_$paragraphCounter\""
                    }
                    if (hTag.startsWith("h") || hTag.contains("id=")) paragraphCounter++
                    html.append("<$hTag>")
                    tagStack.push("title")
                }
                "subtitle" -> {
                    html.append("<h3 id=\"p_$paragraphCounter\">")
                    paragraphCounter++
                    tagStack.push("subtitle")
                }
                "poem" -> {
                    insidePoem = true
                    html.append("<div class='poem'>")
                    tagStack.push("poem")
                }
                "stanza" -> {
                    html.append("<div class='stanza'>")
                    tagStack.push("stanza")
                }
                "v" -> {
                    html.append("<div class='verse'>")
                    tagStack.push("v")
                }
                "epigraph" -> {
                    insideEpigraph = true
                    html.append("<div class='epigraph'>")
                    tagStack.push("epigraph")
                }
                "cite" -> {
                    html.append("<blockquote class='cite'>")
                    tagStack.push("cite")
                }
                "annotation" -> {
                    html.append("<div class='annotation'>")
                    tagStack.push("annotation")
                }
                "strong", "b" -> {
                    html.append("<strong>")
                    tagStack.push("strong")
                }
                "emphasis", "i" -> {
                    html.append("<em>")
                    tagStack.push("em")
                }
                "style" -> {
                    html.append("<span class='style'>")
                    tagStack.push("style")
                }
                "sub" -> {
                    html.append("<sub>")
                    tagStack.push("sub")
                }
                "sup" -> {
                    html.append("<sup>")
                    tagStack.push("sup")
                }
                "code" -> {
                    html.append("<code>")
                    tagStack.push("code")
                }
                "strikethrough" -> {
                    html.append("<del>")
                    tagStack.push("del")
                }
                "empty-line" -> {
                    html.append("<br/>")
                }
                "a" -> {
                    var href = attributes?.getValue("l:href") ?: attributes?.getValue("href") ?: ""
                    val type = attributes?.getValue("type") ?: ""
                    if (href.startsWith("#")) href = href.substring(1)
                    if (type == "note" || href.isNotEmpty()) {
                        html.append("<a href=\"#$href\" class=\"footnote-ref\">")
                        tagStack.push("a")
                    }
                }
                "note" -> {
                    noteCounter++
                    html.append("<div class='footnote' id='note_$noteCounter'>")
                    tagStack.push("note")
                }
                "image", "img" -> {
                    var href = attributes?.getValue("l:href") ?: attributes?.getValue("href") ?: ""
                    if (href.startsWith("#")) {
                        href = href.substring(1)
                    }
                    if (href.isNotEmpty()) {
                        html.append("<img src=\"IMAGE_ID:$href\" alt=\"image\" />")
                    }
                }
                "table" -> {
                    html.append("<table class='fb2-table'>")
                    tagStack.push("table")
                }
                "tr" -> {
                    html.append("<tr>")
                    tagStack.push("tr")
                }
                "td" -> {
                    html.append("<td>")
                    tagStack.push("td")
                }
                "th" -> {
                    html.append("<th>")
                    tagStack.push("th")
                }
            }
        }

        override fun endElement(uri: String?, localName: String?, qName: String?) {
            val element = qName?.lowercase() ?: ""
            
            if (element == "body") {
                flushText()
                insideBody = false
                return
            }
            if (element == "binary") {
                insideBinary = false
                processAndStoreBinary()
                return
            }
            
            if (!insideBody) return
            
            flushText()
            
            when (element) {
                "section" -> {
                    if (sectionDepth > 0) sectionDepth--
                    html.append("</section>")
                    popTag("section")
                }
                "p" -> {
                    html.append("</p>")
                    popTag("p")
                }
                "title" -> {
                    val hTag = when {
                        insidePoem -> "div"
                        insideEpigraph -> "div"
                        sectionDepth > 1 -> "h2"
                        else -> "h1"
                    }
                    html.append("</$hTag>")
                    popTag("title")
                }
                "subtitle" -> {
                    html.append("</h3>")
                    popTag("subtitle")
                }
                "poem" -> {
                    insidePoem = false
                    html.append("</div>")
                    popTag("poem")
                }
                "stanza" -> {
                    html.append("</div>")
                    popTag("stanza")
                }
                "v" -> {
                    html.append("</div>")
                    popTag("v")
                }
                "epigraph" -> {
                    insideEpigraph = false
                    html.append("</div>")
                    popTag("epigraph")
                }
                "cite" -> {
                    html.append("</blockquote>")
                    popTag("cite")
                }
                "annotation" -> {
                    html.append("</div>")
                    popTag("annotation")
                }
                "strong", "b" -> {
                    html.append("</strong>")
                    popTag("strong")
                }
                "emphasis", "i" -> {
                    html.append("</em>")
                    popTag("em")
                }
                "style" -> {
                    html.append("</span>")
                    popTag("style")
                }
                "sub" -> {
                    html.append("</sub>")
                    popTag("sub")
                }
                "sup" -> {
                    html.append("<sup>")
                    popTag("sup")
                }
                "code" -> {
                    html.append("</code>")
                    popTag("code")
                }
                "strikethrough" -> {
                    html.append("</del>")
                    popTag("del")
                }
                "a" -> {
                    html.append("</a>")
                    popTag("a")
                }
                "note" -> {
                    html.append("</div>")
                    popTag("note")
                }
                "table" -> {
                    html.append("</table>")
                    popTag("table")
                }
                "tr" -> {
                    html.append("</tr>")
                    popTag("tr")
                }
                "td" -> {
                    html.append("</td>")
                    popTag("td")
                }
                "th" -> {
                    html.append("</th>")
                    popTag("th")
                }
            }
        }

        private fun popTag(expectedTag: String) {
            if (tagStack.isNotEmpty()) {
                tagStack.pop()
            }
        }

        override fun characters(ch: CharArray?, start: Int, length: Int) {
            if (ch == null || length <= 0) return
            if (insideBinary) {
                currentBinaryContent.append(ch, start, length)
            } else if (insideBody) {
                currentText.append(ch, start, length)
            }
        }
    }
}


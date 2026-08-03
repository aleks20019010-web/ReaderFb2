package com.nightread.app.service

import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayInputStream
import javax.xml.parsers.SAXParserFactory

object Fb2ToHtmlConverterAdvanced {

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
        paddingRight: Int
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
            val sideMarginPx = paddingLeft
            val sideMargin = "${sideMarginPx}px"
            val columnWidthCss = "calc(100vw - ${sideMarginPx * 2}px)"
            val columnGapCss = "${sideMarginPx * 2}px"
            val topMargin = "${paddingTop}px"
            val bottomMargin = "${paddingBottom}px"
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
                            margin-bottom: 0em;
                            text-indent: 1.5em;
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
                        }
                        h1 { 
                            font-size: 1.6em; 
                            margin-top: 0.5em;
                            margin-bottom: 1.2em;
                        }
                        h2 { font-size: 1.3em; }
                        h3 { font-size: 1.2em; }
                        .chapter-section {
                            break-before: column;
                            -webkit-column-break-before: always;
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
            e.printStackTrace()
            return "<html><body>Error rendering FB2: ${e.message}</body></html>"
        }
    }

    private class Fb2SaxHandler : DefaultHandler() {
        private val html = StringBuilder()
        private val binaryMap = HashMap<String, String>()
        private val currentText = StringBuilder()
        private var paragraphCounter = 0
        
        private var insideBody = false
        private var insideBinary = false
        private var currentBinaryId = ""
        private val currentBinaryContent = StringBuilder()

        private fun flushText() {
            if (currentText.isEmpty()) return
            val text = currentText.toString()
            currentText.setLength(0)
            
            for (i in 0 until text.length) {
                when (val c = text[i]) {
                    '<' -> html.append("&lt;")
                    '>' -> html.append("&gt;")
                    '&' -> html.append("&amp;")
                    else -> html.append(c)
                }
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
                "p" -> {
                    html.append("<p id=\"p_$paragraphCounter\">")
                    paragraphCounter++
                }
                "title" -> {
                    html.append("<h1 id=\"p_$paragraphCounter\">")
                    paragraphCounter++
                }
                "section" -> html.append("<div class='chapter-section'>")
                "subtitle" -> {
                    html.append("<h3 id=\"p_$paragraphCounter\">")
                    paragraphCounter++
                }
                "strong", "b" -> html.append("<strong>")
                "emphasis", "i" -> html.append("<em>")
                "empty-line" -> html.append("<br/>")
                "image", "img" -> {
                    var href = attributes?.getValue("l:href") ?: attributes?.getValue("href") ?: ""
                    if (href.startsWith("#")) {
                        href = href.substring(1)
                    }
                    if (href.isNotEmpty()) {
                        html.append("<img src=\"IMAGE_ID:$href\" />")
                    }
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
                if (currentBinaryId.isNotEmpty()) {
                    binaryMap[currentBinaryId] = currentBinaryContent.toString().trim()
                }
                return
            }
            
            if (!insideBody) return
            
            flushText()
            
            when (element) {
                "p" -> html.append("</p>")
                "title" -> html.append("</h1>")
                "section" -> html.append("</div>")
                "subtitle" -> html.append("</h3>")
                "strong", "b" -> html.append("</strong>")
                "emphasis", "i" -> html.append("</em>")
            }
        }

        override fun characters(ch: CharArray?, start: Int, length: Int) {
            if (ch == null) return
            if (insideBinary) {
                currentBinaryContent.append(ch, start, length)
            } else if (insideBody) {
                currentText.append(ch, start, length)
            }
        }
    }
}

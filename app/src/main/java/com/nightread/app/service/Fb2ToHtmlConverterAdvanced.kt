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
        paddingRight: Int,
        hyphenationEnabled: Boolean,
        context: android.content.Context
    ): String {
        try {
            val factory = SAXParserFactory.newInstance()
            val saxParser = factory.newSAXParser()
            val handler = Fb2SaxHandler(hyphenationEnabled, context)
            
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
            val fontWeightCss = if (fontWeight > 0) "bold" else "normal"

            val htmlContent = handler.getHtml()

            return """
                <!DOCTYPE html>
                <html lang="ru">
                <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
                    <style>
                        html {
                            margin: 0;
                            padding: 0;
                            height: 100%;
                            background-color: $bgColor;
                            overflow: hidden;
                        }
                        body {
                            margin: 0;
                            padding-top: $topMargin;
                            padding-bottom: $bottomMargin;
                            padding-left: $sideMargin;
                            padding-right: $sideMargin;
                            width: 100vw;
                            height: 100vh;
                            box-sizing: border-box;
                            overflow-x: scroll;
                            overflow-y: hidden;
                            -webkit-column-width: $columnWidthCss;
                            -webkit-column-gap: $columnGapCss;
                            column-width: $columnWidthCss;
                            column-gap: $columnGapCss;
                            background-color: $bgColor;
                            color: $textColor;
                            font-family: $cssFontFamily;
                            font-size: ${fontSize}px;
                            font-weight: $fontWeightCss;
                            line-height: $lineSpacing;
                            text-align: ${fontAlignment.lowercase()};
                            -webkit-hyphens: auto;
                            -ms-hyphens: auto;
                            hyphens: auto;
                            -webkit-user-select: none;
                            user-select: none;
                        }
                        p {
                            margin-top: 0;
                            margin-bottom: 0.8em;
                            text-indent: 1.5em;
                            text-align: justify;
                        }
                        h1, h2, h3, h4, h5, h6 {
                            margin-top: 1em;
                            margin-bottom: 0.5em;
                            font-weight: bold;
                            text-align: center;
                            page-break-after: avoid;
                        }
                        h1 { font-size: 1.4em; }
                        h2 { font-size: 1.3em; }
                        h3 { font-size: 1.2em; }
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
                    </style>
                    <script type="text/javascript">
                        function calculatePages() {
                            var totalWidth = document.body.scrollWidth;
                            var pageWidth = window.innerWidth || document.documentElement.clientWidth;
                            if (pageWidth > 0) {
                                var pages = Math.round(totalWidth / pageWidth);
                                if (typeof AndroidInterface !== 'undefined' && AndroidInterface.onPagesCalculated) {
                                    AndroidInterface.onPagesCalculated(pages);
                                }
                            }
                        }
                        
                        window.onload = function() {
                            setTimeout(calculatePages, 200);
                        };

                        window.onresize = function() {
                            setTimeout(calculatePages, 200);
                        };
                    </script>
                </head>
                <body>
                    $htmlContent
                </body>
                </html>
            """.trimIndent()
        } catch (e: Exception) {
            e.printStackTrace()
            return "<html><body>Error rendering FB2: ${e.message}</body></html>"
        }
    }

    private class Fb2SaxHandler(
        private val hyphenationEnabled: Boolean,
        private val context: android.content.Context
    ) : DefaultHandler() {
        private val html = StringBuilder()
        private val binaryMap = HashMap<String, String>()
        private val currentText = StringBuilder()
        
        private var insideBody = false
        private var insideBinary = false
        private var currentBinaryId = ""
        private val currentBinaryContent = StringBuilder()

        private fun flushText() {
            if (currentText.isEmpty()) return
            var text = currentText.toString()
            currentText.setLength(0)
            
            if (hyphenationEnabled) {
                text = com.nightread.app.ui.HyphenatorHelper.hyphenate(text, context, null)
            }
            
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
                "p" -> html.append("<p>")
                "title", "section" -> html.append("<div>")
                "subtitle" -> html.append("<h3>")
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
                "title", "section" -> html.append("</div>")
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

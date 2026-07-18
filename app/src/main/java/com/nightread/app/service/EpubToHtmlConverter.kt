package com.nightread.app.service

object EpubToHtmlConverter {

    fun convert(
        xhtmlContent: String,
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

        return """
            <!DOCTYPE html>
            <html lang="ru">
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
                <style>
                    html { margin: 0; padding: 0; height: 100%; background-color: $bgColor; overflow: hidden; }
                    body {
                        margin: 0;
                        padding-top: $topMargin; padding-bottom: $bottomMargin;
                        padding-left: $sideMargin; padding-right: $sideMargin;
                        width: 100vw; height: 100vh; box-sizing: border-box;
                        overflow-x: scroll; overflow-y: hidden;
                        -webkit-column-width: $columnWidthCss; -webkit-column-gap: $columnGapCss;
                        column-width: $columnWidthCss; column-gap: $columnGapCss;
                        background-color: $bgColor; color: $textColor;
                        font-family: $cssFontFamily; font-size: ${fontSize}px;
                        font-weight: $fontWeightCss; line-height: $lineSpacing;
                        text-align: ${fontAlignment.lowercase()};
                        -webkit-hyphens: auto; -ms-hyphens: auto; hyphens: auto;
                    }
                    p { margin-top: 0; margin-bottom: 0em; text-indent: 1.5em; text-align: justify; }
                    h1, h2, h3, h4, h5, h6 { margin-top: 1em; margin-bottom: 0.5em; font-weight: bold; text-align: center; }
                    img { max-width: 100%; height: auto; display: block; margin: 12px auto; }
                </style>
                <script type="text/javascript">
                    // (Reuse JS from Fb2ToHtmlConverterAdvanced if possible, 
                    // or just copy the essential ones for pagination/scrolling)
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
                    window.onload = function() { setTimeout(calculatePages, 200); };
                    window.onresize = function() { setTimeout(calculatePages, 200); };


                </script>
            </head>
            <body>
                $xhtmlContent
            </body>
            </html>
        """.trimIndent()
    }
}

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

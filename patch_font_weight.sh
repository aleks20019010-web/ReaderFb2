sed -i 's/val fontWeightCss = if (fontWeight > 0) "bold" else "normal"/val fontWeightCss = fontWeight.toString()/' app/src/main/java/com/nightread/app/ui/BookReaderFragment.kt
sed -i 's/val fontWeightCss = if (fontWeight > 0) "bold" else "normal"/val fontWeightCss = fontWeight.toString()/' app/src/main/java/com/nightread/app/service/EpubToHtmlConverter.kt
sed -i 's/val fontWeightCss = if (fontWeight > 0) "bold" else "normal"/val fontWeightCss = fontWeight.toString()/' app/src/main/java/com/nightread/app/service/Fb2ToHtmlConverterAdvanced.kt

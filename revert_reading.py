import sys

file = 'app/src/main/java/com/nightread/app/ui/ReadingActivity.kt'
content = open(file).read()

old_code = """        var rawTextToSplit = if (hyphenationEnabled) {
            com.nightread.app.ui.HyphenationPatterns.load("ru")
            com.nightread.app.ui.HyphenatorHelper.hyphenate(bookContent)
        } else {
            bookContent
        }
        
        val spannedText = android.text.SpannableStringBuilder(rawTextToSplit)
        val pattern = java.util.regex.Pattern.compile("\\\\[CHAPTER\\\\](.*?)\\\\[/CHAPTER\\\\]", java.util.regex.Pattern.DOTALL)
        val matcher = pattern.matcher(spannedText)
        var deletedCount = 0
        while (matcher.find()) {
            val start = matcher.start() - deletedCount
            val end = matcher.end() - deletedCount
            
            val title = matcher.group(1) ?: ""
            spannedText.replace(start, end, title)
            
            spannedText.setSpan(
                android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                start, start + title.length,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannedText.setSpan(
                android.text.style.RelativeSizeSpan(1.5f),
                start, start + title.length,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannedText.setSpan(
                android.text.style.AlignmentSpan.Standard(android.text.Layout.Alignment.ALIGN_CENTER),
                start, start + title.length,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            
            deletedCount += (end - start) - title.length
        }
        val textToSplit = spannedText"""

new_code = """        val textToSplit = if (hyphenationEnabled) {
            com.nightread.app.ui.HyphenationPatterns.load("ru")
            com.nightread.app.ui.HyphenatorHelper.hyphenate(bookContent)
        } else {
            bookContent
        }"""

content = content.replace(old_code, new_code)
open(file, 'w').write(content)

import sys

# 1. PageSplitter.kt
file1 = 'app/src/main/java/com/nightread/app/ui/PageSplitter.kt'
content1 = open(file1).read()
old_builder = """.setHyphenationFrequency(android.text.Layout.HYPHENATION_FREQUENCY_FULL)
                    .build()"""
new_builder = """.setHyphenationFrequency(android.text.Layout.HYPHENATION_FREQUENCY_FULL)
                    .apply { if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) { setJustificationMode(android.text.Layout.JUSTIFICATION_MODE_INTER_WORD) } }
                    .build()"""
content1 = content1.replace(old_builder, new_builder)
open(file1, 'w').write(content1)

# 2. PageFragment.kt
file3 = 'app/src/main/java/com/nightread/app/ui/PageFragment.kt'
content3 = open(file3).read()
content3 = content3.replace('textView.justificationMode = android.text.Layout.JUSTIFICATION_MODE_NONE', 'textView.justificationMode = android.text.Layout.JUSTIFICATION_MODE_INTER_WORD')
open(file3, 'w').write(content3)

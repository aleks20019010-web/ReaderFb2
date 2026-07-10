
import sys

content = open('app/src/main/java/com/nightread/app/ui/PageSplitter.kt').read()

# Add setJustificationMode for API 26+
# Look for: .setHyphenationFrequency(android.text.Layout.HYPHENATION_FREQUENCY_FULL)
# Replace with: .setHyphenationFrequency(android.text.Layout.HYPHENATION_FREQUENCY_FULL).apply { if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) { setJustificationMode(android.text.Layout.JUSTIFICATION_MODE_INTER_WORD) } }
# This needs to be applied in both `getPageForOffset` and `splitTextProgressive`.

old_builder_settings = """.setHyphenationFrequency(android.text.Layout.HYPHENATION_FREQUENCY_FULL)
            .build()"""

new_builder_settings = """.setHyphenationFrequency(android.text.Layout.HYPHENATION_FREQUENCY_FULL)
            .apply { if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) { setJustificationMode(android.text.Layout.JUSTIFICATION_MODE_INTER_WORD) } }
            .build()"""

content = content.replace(old_builder_settings, new_builder_settings)

open('app/src/main/java/com/nightread/app/ui/PageSplitter.kt', 'w').write(content)

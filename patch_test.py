with open("app/src/test/java/com/nightread/app/ReaderPerformanceTest.kt", "r") as f:
    content = f.read()

import re
content = re.sub(r'private val measurer = TextMeasurer\(.*?\n.*?\n.*?\n    \)', 'private lateinit var measurer: TextMeasurer', content, flags=re.DOTALL)
content = re.sub(r'ReaderMetrics\.isEnabled = true', 'ReaderMetrics.isEnabled = true\n        measurer = TextMeasurer(\n            androidx.compose.ui.text.font.createFontFamilyResolver(context),\n            Density(1f, 1f),\n            androidx.compose.ui.unit.LayoutDirection.Ltr\n        )', content)

with open("app/src/test/java/com/nightread/app/ReaderPerformanceTest.kt", "w") as f:
    f.write(content)

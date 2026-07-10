import sys

file = 'app/src/main/java/com/nightread/app/ui/PageSplitter.kt'
content = open(file).read()

old_code = """AbsoluteSizeSpan((basePaintSize * 1.3f).toInt())"""
new_code = """AbsoluteSizeSpan((basePaintSize * 1.5f).toInt())"""

content = content.replace(old_code, new_code)
open(file, 'w').write(content)

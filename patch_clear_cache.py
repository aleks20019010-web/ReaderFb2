import sys

file = 'app/src/main/java/com/nightread/app/ui/ReadingActivity.kt'
content = open(file).read()

content = content.replace('if (false && BookCache.sha1 == sha1 && BookCache.content.isNotEmpty()) {', 'if (false && false && BookCache.sha1 == sha1 && BookCache.content.isNotEmpty()) {')
open(file, 'w').write(content)

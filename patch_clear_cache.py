import sys

file = 'app/src/main/java/com/nightread/app/ui/ReadingActivity.kt'
content = open(file).read()

# Force clearing the BookCache if the layout logic changed
# We can do this by adding something to the cache key or just clearing it on launch temporarily
content = content.replace('if (BookCache.sha1 == sha1 && BookCache.content.isNotEmpty()) {', 'if (false && BookCache.sha1 == sha1 && BookCache.content.isNotEmpty()) {')
open(file, 'w').write(content)

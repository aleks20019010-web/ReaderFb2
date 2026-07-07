import re

with open('app/src/main/java/com/nightread/app/ui/ReadingActivity.kt', 'r') as f:
    content = f.read()

# Make sure preprocessTextAndHyphenate replaces \n{2,} with \n, and \n\s+ with \n 
# But wait, wait! The user specifically said: "заменить \n\n на \n"
# It currently has: var processedText = text.replace(Regex("\n{2,}"), "\n")
# Which already does that.
# Let's check Fb2Parser.kt.

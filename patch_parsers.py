import re

# Fix Fb2Parser.kt
with open("app/src/main/java/com/nightread/app/service/Fb2Parser.kt", "r") as f:
    fb2 = f.read()

fb2 = re.sub(r'text = text\.replace\(Regex\("\(\[ \\\\t\\\\r\]\*\\\\n\[ \\\\t\\\\r\]\*\)\{3,\}"\), "\\\\n\\\\n"\)',
    'text = text.replace(Regex("([ \\\\t\\\\r]*\\\\n[ \\\\t\\\\r]*){2,}"), "\\\\n")', fb2, flags=re.DOTALL)

with open("app/src/main/java/com/nightread/app/service/Fb2Parser.kt", "w") as f:
    f.write(fb2)

# Fix EpubParser.kt
with open("app/src/main/java/com/nightread/app/service/EpubParser.kt", "r") as f:
    epub = f.read()

epub = epub.replace('\\n    ', '\\n\\u00A0\\u00A0\\u00A0\\u00A0')
epub = epub.replace('\\n  - ', '\\n\\u00A0\\u00A0- ')
epub = re.sub(r'text = text\.replace\(Regex\("\(\[ \\\\t\\\\r\\\\n\]\*\\\\n\[ \\\\t\\\\r\\\\n\]\*\)\+"\), "\\\\n\\\\u00A0\\\\u00A0\\\\u00A0\\\\u00A0"\)',
    """text = text.replace(Regex("([ \\\\t\\\\r]*\\\\n[ \\\\t\\\\r]*){2,}"), "\\\\n")
            text = text.replace(Regex("\\\\n[ \\\\t\\\\r]+(?=\\\\u00A0)"), "\\\\n")""", epub)

with open("app/src/main/java/com/nightread/app/service/EpubParser.kt", "w") as f:
    f.write(epub)

# Fix MobiParser.kt
with open("app/src/main/java/com/nightread/app/service/MobiParser.kt", "r") as f:
    mobi = f.read()

mobi = mobi.replace('text = text.replace(Regex("<p.*?>", RegexOption.IGNORE_CASE), "\\n")',
    'text = text.replace(Regex("<p.*?>", RegexOption.IGNORE_CASE), "\\n\\u00A0\\u00A0\\u00A0\\u00A0")')
mobi = re.sub(r'text = text\.replace\(Regex\("\\\\n\{3,\}"\), "\\\\n\\\\n"\)',
    'text = text.replace(Regex("([ \\\\t\\\\r]*\\\\n[ \\\\t\\\\r]*){2,}"), "\\\\n")\n        text = text.replace(Regex("\\\\n[ \\\\t\\\\r]+(?=\\\\u00A0)"), "\\\\n")', mobi)

with open("app/src/main/java/com/nightread/app/service/MobiParser.kt", "w") as f:
    f.write(mobi)

print("Patched all 3 parsers")

with open("app/src/main/java/com/nightread/app/service/EpubParser.kt", "r") as f:
    epub = f.read()

target = 'text = text.replace(Regex("([ \\\\t\\\\r\\\\n]*\\\\n[ \\\\t\\\\r\\\\n]*)+"), "\\n\\u00A0\\u00A0\\u00A0\\u00A0")'
repl = '''text = text.replace(Regex("([ \\\\t\\\\r]*\\\\n[ \\\\t\\\\r]*){2,}"), "\\n")
            text = text.replace(Regex("\\\\n[ \\\\t\\\\r]+(?=\\\\u00A0)"), "\\n")'''

if target in epub:
    epub = epub.replace(target, repl)
    with open("app/src/main/java/com/nightread/app/service/EpubParser.kt", "w") as f:
        f.write(epub)
    print("Success")
else:
    print("Not found")

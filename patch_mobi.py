with open("app/src/main/java/com/nightread/app/service/MobiParser.kt", "r") as f:
    mobi = f.read()

target = 'text = text.replace(Regex("\\n{3,}"), "\\n\\n")'
repl = '''text = text.replace(Regex("([ \\\\t\\\\r]*\\\\n[ \\\\t\\\\r]*){2,}"), "\\n")
        text = text.replace(Regex("\\\\n[ \\\\t\\\\r]+(?=\\\\u00A0)"), "\\n")'''

if target in mobi:
    mobi = mobi.replace(target, repl)
    with open("app/src/main/java/com/nightread/app/service/MobiParser.kt", "w") as f:
        f.write(mobi)
    print("Success")
else:
    print("Not found")

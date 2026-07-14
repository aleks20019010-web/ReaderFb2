import re
with open("app/src/main/java/com/nightread/app/service/TxtParser.kt", "r") as f:
    txt = f.read()

target = 'content = text.trim()'
repl = '''content = text.replace(Regex("([ \\\\t\\\\r]*\\\\n[ \\\\t\\\\r]*){2,}"), "\\n").trim()'''

if target in txt:
    txt = txt.replace(target, repl)
    with open("app/src/main/java/com/nightread/app/service/TxtParser.kt", "w") as f:
        f.write(txt)
    print("Success")
else:
    print("Not found")

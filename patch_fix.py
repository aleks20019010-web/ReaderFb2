import re

with open("app/src/main/java/com/nightread/app/service/Fb2Parser.kt", "r") as f:
    fb2 = f.read()

# Fix literal newlines
fb2 = re.sub(r'text = text\.replace\(Regex\("\(\[ \\t\\r\]\*\\n\[ \\t\\r\]\*\)\{3,\}"\), ".*?"\)',
    'text = text.replace(Regex("([ \\\\t\\\\r]*\\\\n[ \\\\t\\\\r]*){3,}"), "\\\\n\\\\n")', fb2, flags=re.DOTALL)

fb2 = re.sub(r'text = text\.replace\(Regex\("\\n\[ \\t\\r\]\+\(\?=\\u00A0\)"\), ".*?"\)',
    'text = text.replace(Regex("\\\\n[ \\\\t\\\\r]+(?=\\\\u00A0)"), "\\\\n")', fb2, flags=re.DOTALL)

with open("app/src/main/java/com/nightread/app/service/Fb2Parser.kt", "w") as f:
    f.write(fb2)


with open("app/src/main/java/com/nightread/app/ui/TextFormatter.kt", "r") as f:
    tf = f.read()

tf = tf.replace("\\n", "\\\\n")
# wait, wait! I don't want to replace all \n!


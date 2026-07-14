with open("app/src/main/java/com/nightread/app/service/Fb2Parser.kt", "r") as f:
    fb2 = f.read()

import re
# Just replace any replace that has {3,}
fb2 = re.sub(r'text = text\.replace\(Regex\("\(\[ \\t\\r\]\*\\n\[ \\t\\r\]\*\)\{3,\}"\), "\\n\\n"\)',
    'text = text.replace(Regex("([ \\\\t\\\\r]*\\\\n[ \\\\t\\\\r]*){2,}"), "\\\\n")', fb2)

with open("app/src/main/java/com/nightread/app/service/Fb2Parser.kt", "w") as f:
    f.write(fb2)

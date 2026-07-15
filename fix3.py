import re

with open('./app/src/main/java/com/nightread/app/ui/customlayout/PageSplitter.kt', 'r') as f:
    content = f.read()

# Replace layout.getLineStart(lastLine) with layout.getLineStart(pageEndLine - 1)
content = content.replace("layout.getLineStart(lastLine)", "layout.getLineStart(pageEndLine - 1)")

with open('./app/src/main/java/com/nightread/app/ui/customlayout/PageSplitter.kt', 'w') as f:
    f.write(content)

print("Fixed PageSplitter lastLine error")

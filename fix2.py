import re

with open('./app/src/main/java/com/nightread/app/ui/customlayout/PageSplitter.kt', 'r') as f:
    content = f.read()

old_logic = """if (backtrack > currentOffset) {
                        nextOffset = backtrack"""
new_logic = """if (backtrack > layout.getLineStart(pageEndLine - 1)) {
                        nextOffset = backtrack"""

content = content.replace(old_logic, new_logic)

with open('./app/src/main/java/com/nightread/app/ui/customlayout/PageSplitter.kt', 'w') as f:
    f.write(content)

print("Fixed PageSplitter word break")

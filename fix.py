import re

with open('./app/src/main/java/com/nightread/app/ui/customlayout/PageSplitter.kt', 'r') as f:
    content = f.read()

# We need to find the logic inside findPageEnd:
#                     if (lineWidth < width * 0.8f && charsCount > 2 && backtrack + 1 > currentOffset) {
# And change it to also check that backtrack + 1 > layout.getLineStart(lastLine)
old_logic = "if (lineWidth < width * 0.8f && charsCount > 2 && backtrack + 1 > currentOffset) {"
new_logic = "if (lineWidth < width * 0.8f && charsCount > 2 && backtrack + 1 > layout.getLineStart(lastLine)) {"

content = content.replace(old_logic, new_logic)

# Also update the orphan protection logic:
# if (charsCount in 1..2 && backtrack + 1 > currentOffset) {
old_orphan = "if (charsCount in 1..2 && backtrack + 1 > currentOffset) {"
new_orphan = "if (charsCount in 1..2 && backtrack + 1 > layout.getLineStart(lastLine)) {"

content = content.replace(old_orphan, new_orphan)

with open('./app/src/main/java/com/nightread/app/ui/customlayout/PageSplitter.kt', 'w') as f:
    f.write(content)

print("Fixed PageSplitter")

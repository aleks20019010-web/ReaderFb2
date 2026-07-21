import sys

file_path = "app/src/main/java/com/nightread/app/ui/BookReaderActivity.kt"
with open(file_path, "r") as f:
    content = f.read()

line_to_find = "val unescaped = "
new_line = 'val unescaped = result.substring(1, result.length - 1).replace("\\\\\"", "\\"").replace("\\\\\\\\", "\\\\")'
lines = content.split('\n')
for i, line in enumerate(lines):
    if line_to_find in line:
        lines[i] = "                                " + new_line

with open(file_path, "w") as f:
    f.write('\n'.join(lines))

import sys

file_path = "app/src/main/java/com/nightread/app/ui/BookReaderActivity.kt"
with open("correct_line.txt", "r") as f:
    correct_line = f.read().strip('\n')

with open(file_path, "r") as f:
    content = f.read()

lines = content.split('\n')
for i, line in enumerate(lines):
    if "val unescaped =" in line:
        lines[i] = correct_line

with open(file_path, "w") as f:
    f.write('\n'.join(lines))

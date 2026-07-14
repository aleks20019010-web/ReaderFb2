import re

file_path = "app/src/main/java/com/nightread/app/MainActivity.kt"
with open(file_path, "r") as f:
    content = f.read()

if "parsedBook.content.trim().trim('\\u000C').trim()" in content:
    print("MainActivity doesn't clean text again, it just trims it, which is fine since the parser already cleaned it.")
else:
    print("MainActivity pattern not matched")


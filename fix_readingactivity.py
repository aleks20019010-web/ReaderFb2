import re

file_path = "app/src/main/java/com/nightread/app/ui/ReadingActivity.kt"
with open(file_path, "r") as f:
    content = f.read()

# Only change inside ReadingActivity parsedBook assignments if it isn't clean
if "parsedBook.content.trim().trim('\\u000C').trim()" in content:
    print("ReadingActivity doesn't clean text again, it just trims it, which is fine since the parser already cleaned it.")
else:
    print("ReadingActivity pattern not matched")


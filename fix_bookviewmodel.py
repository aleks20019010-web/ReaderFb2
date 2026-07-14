import re

file_path = "app/src/main/java/com/nightread/app/ui/BookViewModel.kt"
with open(file_path, "r") as f:
    content = f.read()

if "com.nightread.app.service.TextCleaner.cleanText(parsed.content)" not in content:
    content = re.sub(
        r"content = parsed\.content,",
        r"content = com.nightread.app.service.TextCleaner.cleanText(parsed.content) as String,",
        content,
        count=1
    )
    with open(file_path, "w") as f:
        f.write(content)
    print("Updated BookViewModel.kt")
else:
    print("Already updated")

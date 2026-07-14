import re
import os

parsers = [
    "app/src/main/java/com/nightread/app/service/Fb2Parser.kt",
    "app/src/main/java/com/nightread/app/service/EpubParser.kt",
    "app/src/main/java/com/nightread/app/service/MobiParser.kt",
    "app/src/main/java/com/nightread/app/service/TxtParser.kt"
]

for p in parsers:
    if not os.path.exists(p): continue
    with open(p, "r") as f:
        content = f.read()

    if "return BookParser.ParsedBook(" in content and "TextCleaner.cleanText" not in content:
        content = re.sub(
            r"(return BookParser\.ParsedBook\(\s*title = [^,]+,\s*author = [^,]+,\s*content = )([^,]+)(,\s*notes = [^\)]+\))",
            r"\1TextCleaner.cleanText(\2) as String\3",
            content,
            flags=re.MULTILINE
        )
        with open(p, "w") as f:
            f.write(content)
        print(f"Updated {p}")

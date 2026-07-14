import re
import os
import glob

# Find all files ending with Parser.kt
parser_files = glob.glob("app/src/main/java/com/nightread/app/service/*Parser.kt")

for p in parser_files:
    if not os.path.exists(p): continue
    with open(p, "r") as f:
        content = f.read()

    original_content = content
    # Find `return BookParser.ParsedBook(...)`
    # We will use regex to find `content = <something>,` and replace it with `content = TextCleaner.cleanText(<something>) as String,`
    
    # Let's find content assignment inside ParsedBook constructor
    # A bit complex because of nested parenthesis. Let's do a simple string replace if it matches the pattern
    
    # Or just find `return BookParser.ParsedBook(` block and replace `content = `
    def repl(m):
        prefix = m.group(1)
        val = m.group(2)
        suffix = m.group(3)
        if "TextCleaner.cleanText" in val:
            return m.group(0)
        # Avoid cleaning an already cleaned text
        return f"{prefix}TextCleaner.cleanText({val}) as String{suffix}"
    
    # Regex for `content = ` followed by anything up to `,` or `\n` but careful with method calls
    # Actually, we can just replace `content = ` line.
    
    lines = content.split('\n')
    inside_parsed_book = False
    for i, line in enumerate(lines):
        if "return BookParser.ParsedBook(" in line:
            inside_parsed_book = True
        elif inside_parsed_book:
            if "content =" in line:
                if "TextCleaner.cleanText" not in line:
                    lines[i] = re.sub(r'content\s*=\s*(.*?)(,?\s*)$', r'content = TextCleaner.cleanText(\1) as String\2', line)
            if ")" in line:
                # Naive check for end of constructor, but might be on the same line as notes
                if "notes =" in line or "coverBytes =" in line or line.strip() == ")":
                    pass
                # if there is a closing parenthesis that is the end of the constructor
                if line.strip() == ")":
                    inside_parsed_book = False

    content = '\n'.join(lines)
    
    if content != original_content:
        with open(p, "w") as f:
            f.write(content)
        print(f"Updated {p}")


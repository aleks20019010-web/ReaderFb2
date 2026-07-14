import re

with open("app/src/main/java/com/nightread/app/ui/TextFormatter.kt", "r") as f:
    text_formatter = f.read()

# Remove Paragraph Indent logic from TextFormatter since it breaks StaticLayout pagination
pattern = r"// 5\. Paragraph Indent.*?return spannable"
replacement = "return spannable"
new_tf = re.sub(pattern, replacement, text_formatter, flags=re.DOTALL)
with open("app/src/main/java/com/nightread/app/ui/TextFormatter.kt", "w") as f:
    f.write(new_tf)
print("Patched TextFormatter.kt")


with open("app/src/main/java/com/nightread/app/service/Fb2Parser.kt", "r") as f:
    fb2_parser = f.read()

fb2_parser = fb2_parser.replace("""            .replace(Regex("<empty-line[^>]*>"), "\\n")""", """            .replace(Regex("<empty-line[^>]*>"), "\\n\\n")""")
fb2_parser = fb2_parser.replace("""            .replace(Regex("<p[^>]*>"), "\\n")""", """            .replace(Regex("<p[^>]*>"), "\\n\\u00A0\\u00A0\\u00A0\\u00A0")""")
fb2_parser = re.sub(r"""text = text\.replace\(Regex\("\(\[ \\\\t\\\\r\\\\n\]\*\\\\n\[ \\\\t\\\\r\\\\n\]\*\)\+"\), "\\n"\)""",
"""// Clean up multiple newlines to just one or two
        text = text.replace(Regex("([ \\\\t\\\\r]*\\\\n[ \\\\t\\\\r]*){3,}"), "\\n\\n")
        // Remove spaces before non-breaking spaces (our paragraph indent)
        text = text.replace(Regex("\\\\n[ \\\\t\\\\r]+(?=\\\\u00A0)"), "\\n")
""", fb2_parser)

with open("app/src/main/java/com/nightread/app/service/Fb2Parser.kt", "w") as f:
    f.write(fb2_parser)
print("Patched Fb2Parser.kt")

import re

with open('app/src/main/java/com/nightread/app/service/Fb2Parser.kt', 'r') as f:
    content = f.read()

content = content.replace(
    'text.replace(Regex("\\n{2,}"), "\\n").trim()',
    'return text.replace(Regex("\\n{2,}"), "\\n").trim()'
)
content = content.replace(
    'var text = contentToParse\n                .replace(Regex("<empty-line[^>]*>"), "\\n")\n                .replace(Regex("<p[^>]*>"), "\\n    ")',
    'var text = contentToParse\n                .replace(Regex("<empty-line[^>]*>"), "\\n")\n                .replace(Regex("<p[^>]*>"), "\\n    ")'
)

with open('app/src/main/java/com/nightread/app/service/Fb2Parser.kt', 'w') as f:
    f.write(content)

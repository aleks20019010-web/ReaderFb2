import re

with open('app/src/main/java/com/nightread/app/service/Fb2Parser.kt', 'r') as f:
    content = f.read()

# Replace the Regex formatting in Fb2Parser
old_replace = r"""            var text = contentToParse
                .replace(Regex("<empty-line[^>]*>"), "\n")
                .replace(Regex("<p[^>]*>"), "\n    ")
                .replace(Regex("</p>"), "")
                .replace(Regex("<v[^>]*>"), "\n")
                .replace(Regex("</v>"), "")
                .replace(Regex("<title[^>]*>"), "\n")
                .replace(Regex("</title>"), "\n")
                .replace(Regex("<subtitle[^>]*>"), "\n")
                .replace(Regex("</subtitle>"), "\n")"""

new_replace = r"""            var text = contentToParse
                .replace(Regex("<empty-line[^>]*>"), "\n")
                .replace(Regex("<p[^>]*>"), "\n    ")
                .replace(Regex("</p>"), "")
                .replace(Regex("<v[^>]*>"), "\n    ")
                .replace(Regex("</v>"), "")
                .replace(Regex("<title[^>]*>"), "\n")
                .replace(Regex("</title>"), "\n")
                .replace(Regex("<subtitle[^>]*>"), "\n")
                .replace(Regex("</subtitle>"), "\n")"""

content = content.replace(old_replace, new_replace)

old_cleanup = r"""            return text.replace(Regex("\n{2,}"), "\n").trim()"""
new_cleanup = r"""            // Replace multiple newlines (with optional whitespace) with a single newline
            text = text.replace(Regex("(\\s*\\n\\s*)+"), "\n    ")
            return text.trim()"""

content = content.replace(old_cleanup, new_cleanup)

with open('app/src/main/java/com/nightread/app/service/Fb2Parser.kt', 'w') as f:
    f.write(content)

import sys

file = 'app/src/main/java/com/nightread/app/service/Fb2Parser.kt'
content = open(file).read()

# I want to add space cleanup right after decoding entities.
old_entities = """            // Decode entities
            text = text
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&nbsp;", " ")"""

new_entities = """            // Decode entities
            text = text
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&nbsp;", " ")
                
            // Clean up multiple spaces
            text = text.replace(Regex("[ \\t]+"), " ")"""

content = content.replace(old_entities, new_entities)

open(file, 'w').write(content)

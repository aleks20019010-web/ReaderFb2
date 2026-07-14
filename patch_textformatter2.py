import re

with open("app/src/main/java/com/nightread/app/ui/TextFormatter.kt", "r") as f:
    tf = f.read()

# Instead of ZeroWidthSpan, let's just delete the tags!
# We can find all [CHAPTER] and [/CHAPTER], delete them, but first apply the title format!
# Since we delete them, we have to adjust indices.
# Actually, the simplest is to replace `ZeroWidthSpan()` back to `AbsoluteSizeSpan(0)` if we delete, wait, no, if we delete we don't need spans!

# Let's write a simple loop to process and delete tags.
# Wait, it's easier to just do:
# spannable.replace(startTag, startTag + "[CHAPTER]".length, "")
# And then the endTag will be shifted by "[CHAPTER]".length!


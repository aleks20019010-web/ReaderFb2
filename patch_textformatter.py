with open("app/src/main/java/com/nightread/app/ui/TextFormatter.kt", "r") as f:
    text_formatter = f.read()

import re

# We will replace the whole while (true) loops for CHAPTER, CITE, SUP, NOTE.
# Let's replace the whole body of formatAllSpans.

# Actually, doing it via a script is easier.

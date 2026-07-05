with open("app/src/main/java/com/nightread/app/ui/LibraryFragment.kt", "r") as f:
    content = f.read()

import re

# Remove adapter initialization
adapter_init = r'\s*continueReadingAdapter = ContinueReadingAdapter\(\s*books = emptyList\(\),\s*onOpenBook = \{ book ->\s*viewModel\.openBook\(book\)\s*\}\s*\)'
content = re.sub(adapter_init, '', content, flags=re.DOTALL)

with open("app/src/main/java/com/nightread/app/ui/LibraryFragment.kt", "w") as f:
    f.write(content)

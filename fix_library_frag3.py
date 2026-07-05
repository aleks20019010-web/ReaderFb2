with open("app/src/main/java/com/nightread/app/ui/LibraryFragment.kt", "r") as f:
    content = f.read()

import re

# Remove continueReadingAdapter.updateBooks
content = re.sub(r'\s*continueReadingAdapter\.updateBooks\(recentlyRead\)', '', content)

with open("app/src/main/java/com/nightread/app/ui/LibraryFragment.kt", "w") as f:
    f.write(content)

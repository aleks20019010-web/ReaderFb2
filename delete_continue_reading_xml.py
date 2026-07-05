with open("app/src/main/res/layout/library_fragment.xml", "r") as f:
    content = f.read()

import re
# Regex to remove the Continue Reading section
content = re.sub(r'\s*<!-- Continue Reading Section -->\s*<LinearLayout\s*android:id="@+id/layoutContinueReading".*?</LinearLayout>', '', content, flags=re.DOTALL)

with open("app/src/main/res/layout/library_fragment.xml", "w") as f:
    f.write(content)

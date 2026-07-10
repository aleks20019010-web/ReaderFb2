import sys

content = open('app/src/main/res/layout/fragment_page.xml').read()

import re

# Remove any layout_margin attributes from TextView
content = re.sub(r'android:layout_margin[a-zA-Z]*="[^"]*"', '', content)
# Remove padding attributes from TextView
content = re.sub(r'android:padding[a-zA-Z]*="[^"]*"', '', content)

open('app/src/main/res/layout/fragment_page.xml', 'w').write(content)

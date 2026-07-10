import sys
import re

# 1. PageSplitter.kt
file1 = 'app/src/main/java/com/nightread/app/ui/PageSplitter.kt'
content1 = open(file1).read()
# Remove the apply justificationMode line
content1 = re.sub(r'\.apply \{ if \(android\.os\.Build\.VERSION\.SDK_INT >= android\.os\.Build\.VERSION_CODES\.O\) \{ setJustificationMode\(android\.text\.Layout\.JUSTIFICATION_MODE_INTER_WORD\) \} \}', '', content1)
open(file1, 'w').write(content1)

# 2. ReadingActivity.kt
file2 = 'app/src/main/java/com/nightread/app/ui/ReadingActivity.kt'
content2 = open(file2).read()
# change paddingVertical from 16 to 8
old_pad_vert = 'val paddingVertical = (16 * resources.displayMetrics.density).toInt() + getTopInset()'
new_pad_vert = 'val paddingVertical = (8 * resources.displayMetrics.density).toInt() + getTopInset()'
content2 = content2.replace(old_pad_vert, new_pad_vert)

# change alignment to "left"
old_align = 'alignment = "justify"'
new_align = 'alignment = "left"'
content2 = content2.replace(old_align, new_align)
open(file2, 'w').write(content2)

# 3. PageFragment.kt
file3 = 'app/src/main/java/com/nightread/app/ui/PageFragment.kt'
content3 = open(file3).read()
# Change bottom padding to 0
old_pad = 'v.setPadding(dp16, dp8 + topInset, dp16, dp8)'
new_pad = 'v.setPadding(dp16, dp8 + topInset, dp16, 0)'
content3 = content3.replace(old_pad, new_pad)

# Change justificationMode back to NONE
old_just = 'textView.justificationMode = android.text.Layout.JUSTIFICATION_MODE_INTER_WORD'
new_just = 'textView.justificationMode = android.text.Layout.JUSTIFICATION_MODE_NONE'
content3 = content3.replace(old_just, new_just)
open(file3, 'w').write(content3)


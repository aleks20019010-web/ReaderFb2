import sys
import re

file1 = 'app/src/main/java/com/nightread/app/ui/PageSplitter.kt'
content1 = open(file1).read()
content1 = re.sub(r'\.apply \{ if \(android\.os\.Build\.VERSION\.SDK_INT >= android\.os\.Build\.VERSION_CODES\.O\) \{ setJustificationMode\(android\.text\.Layout\.JUSTIFICATION_MODE_INTER_WORD\) \} \}', '', content1)
open(file1, 'w').write(content1)

file2 = 'app/src/main/java/com/nightread/app/ui/ReadingActivity.kt'
content2 = open(file2).read()
content2 = content2.replace('alignment = "justify"', 'alignment = "left"')
open(file2, 'w').write(content2)

file3 = 'app/src/main/java/com/nightread/app/ui/PageFragment.kt'
content3 = open(file3).read()
content3 = content3.replace('textView.justificationMode = android.text.Layout.JUSTIFICATION_MODE_INTER_WORD', 'textView.justificationMode = android.text.Layout.JUSTIFICATION_MODE_NONE')
open(file3, 'w').write(content3)

import sys

file1 = 'app/src/main/java/com/nightread/app/ui/PageSplitter.kt'
content1 = open(file1).read()
content1 = content1.replace('android.text.Layout.BREAK_STRATEGY_BALANCED', 'android.text.Layout.BREAK_STRATEGY_HIGH_QUALITY')
open(file1, 'w').write(content1)

file3 = 'app/src/main/java/com/nightread/app/ui/PageFragment.kt'
content3 = open(file3).read()
content3 = content3.replace('android.text.Layout.BREAK_STRATEGY_BALANCED', 'android.text.Layout.BREAK_STRATEGY_HIGH_QUALITY')
open(file3, 'w').write(content3)

file4 = 'app/src/main/res/layout/fragment_page.xml'
content4 = open(file4).read()
content4 = content4.replace('android:breakStrategy="balanced"', 'android:breakStrategy="high_quality"')
open(file4, 'w').write(content4)

import re

file_path = "app/src/main/res/layout/dialog_word_action.xml"
with open(file_path, "r") as f:
    content = f.read()

content = content.replace('style="@style/Widget.Material3.Button.TonalButton"', 'style="@style/Widget.Material3.Button.TonalButton" android:textColor="@color/text_primary" android:backgroundTint="@color/bg_panel"')

with open(file_path, "w") as f:
    f.write(content)

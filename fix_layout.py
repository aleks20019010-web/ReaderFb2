import re

file_path = "app/src/main/res/layout/fragment_local_ai.xml"
with open(file_path, "r") as f:
    content = f.read()

# Update btnDownloadModel text
content = re.sub(
    r'<Button\s+android:id="\@\+id/btnDownloadModel"[\s\S]*?android:text=".*?"',
    '<Button\n                            android:id="@+id/btnDownloadModel"\n                            android:layout_width="match_parent"\n                            android:layout_height="52dp"\n                            android:text="Скачать ИИ-модель (.bin)"',
    content
)

# Update btnInitModel visibility
content = re.sub(
    r'<Button\s+android:id="\@\+id/btnInitModel"[\s\S]*?android:visibility="gone"',
    '<Button\n                            android:id="@+id/btnInitModel"\n                            style="@style/Widget.App.Button"\n                            android:layout_width="match_parent"\n                            android:layout_height="52dp"\n                            android:text="Инициализировать модель"\n                            android:textStyle="bold"\n                            android:visibility="visible"',
    content
)

with open(file_path, "w") as f:
    f.write(content)


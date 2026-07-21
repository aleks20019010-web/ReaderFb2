import re

file_path = "app/src/main/res/layout/fragment_local_ai.xml"
with open(file_path, "r") as f:
    content = f.read()

# Replace ElevatedButton for chips
content = content.replace('style="@style/Widget.Material3.Button.ElevatedButton"', 'style="@style/Widget.App.Button"')

# Replace TonalButton for init and upload rules
content = content.replace('style="@style/Widget.Material3.Button.TonalButton"', 'style="@style/Widget.App.Button"')

# Remove explicit textColor from init and upload to allow style to take over
# Since btnInitModel and btnUploadCustomRules have android:textColor="@color/text_primary"
content = re.sub(r'android:id="@+id/btnInitModel"\s+style="@style/Widget\.App\.Button"\s+android:layout_width="match_parent"\s+android:layout_height="52dp"\s+android:text="Инициализировать модель"\s+android:textColor="@color/text_primary"', 
                 'android:id="@+id/btnInitModel"\n                            style="@style/Widget.App.Button"\n                            android:layout_width="match_parent"\n                            android:layout_height="52dp"\n                            android:text="Инициализировать модель"', content)

content = re.sub(r'android:id="@+id/btnUploadCustomRules"\s+style="@style/Widget\.App\.Button"\s+android:layout_width="match_parent"\s+android:layout_height="52dp"\s+android:text="Импортировать правила/словарь \(\.json\)"\s+android:textColor="@color/text_primary"',
                 'android:id="@+id/btnUploadCustomRules"\n                            style="@style/Widget.App.Button"\n                            android:layout_width="match_parent"\n                            android:layout_height="52dp"\n                            android:text="Импортировать правила/словарь (.json)"', content)


with open(file_path, "w") as f:
    f.write(content)

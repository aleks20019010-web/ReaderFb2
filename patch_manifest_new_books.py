import sys

file_path = 'app/src/main/AndroidManifest.xml'
with open(file_path, 'r') as f:
    content = f.read()

new_activity = """        <activity
            android:name=".ui.NewBooksActivity"
            android:exported="false"
            android:theme="@style/Theme.MyApplication" />
"""

content = content.replace('        <activity\n            android:name=".ui.SettingsActivity"', new_activity + '        <activity\n            android:name=".ui.SettingsActivity"')

with open(file_path, 'w') as f:
    f.write(content)

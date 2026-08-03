import re

with open('app/src/main/res/layout/activity_book.xml', 'r') as f:
    text = f.read()

text = text.replace(
    'android:id="@+id/llSyncStatus"\n                android:layout_width="wrap_content"\n                android:layout_height="wrap_content"',
    'android:id="@+id/llSyncStatus"\n                android:layout_width="wrap_content"\n                android:layout_height="wrap_content"\n                android:visibility="gone"'
)

with open('app/src/main/res/layout/activity_book.xml', 'w') as f:
    f.write(text)


import re

with open("app/src/main/res/layout/activity_reading.xml", "r") as f:
    content = f.read()

target = """    <!-- Brightness Indicator -->"""

replacement = """    <!-- Reading Progress Indicator (always visible) -->
    <com.nightread.app.ui.customlayout.ReadingProgressView
        android:id="@+id/readingProgressView"
        android:layout_width="match_parent"
        android:layout_height="4dp"
        android:layout_gravity="bottom"
        android:layout_marginBottom="2dp" />

    <!-- Brightness Indicator -->"""

if target in content:
    with open("app/src/main/res/layout/activity_reading.xml", "w") as f:
        f.write(content.replace(target, replacement))
    print("Patched activity_reading.xml")
else:
    print("Target not found in activity_reading.xml")

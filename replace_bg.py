with open("app/src/main/res/layout/library_fragment.xml", "r") as f:
    content = f.read()

content = content.replace('android:background="@color/bg_panel"\n        android:padding="16dp"\n        android:elevation="4dp"', 
    'android:background="@drawable/bg_glass_panel"\n        android:layout_margin="8dp"\n        android:padding="16dp"\n        android:elevation="4dp"')

with open("app/src/main/res/layout/library_fragment.xml", "w") as f:
    f.write(content)

with open("app/src/main/res/layout/item_book_minimalist.xml", "r") as f:
    content = f.read()

content = content.replace('android:background="@drawable/bg_card_forest"', 'android:background="@drawable/bg_card_glass"\n            android:foreground="?android:attr/selectableItemBackground"')

with open("app/src/main/res/layout/item_book_minimalist.xml", "w") as f:
    f.write(content)

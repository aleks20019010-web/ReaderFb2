with open('app/src/main/res/layout/item_book_grid.xml', 'r') as f:
    text = f.read()

text = text.replace(
    'android:id="@+id/textContainer"\n            android:layout_width="match_parent"\n            android:layout_height="wrap_content"',
    'android:id="@+id/textContainer"\n            android:layout_width="match_parent"\n            android:layout_height="72dp"'
)

text = text.replace(
    'android:id="@+id/tvBookTitle"\n                android:layout_width="match_parent"\n                android:layout_height="wrap_content"\n                android:ellipsize="end"\n                android:maxLines="2"',
    'android:id="@+id/tvBookTitle"\n                android:layout_width="match_parent"\n                android:layout_height="wrap_content"\n                android:ellipsize="end"\n                android:lines="2"\n                android:maxLines="2"'
)

with open('app/src/main/res/layout/item_book_grid.xml', 'w') as f:
    f.write(text)


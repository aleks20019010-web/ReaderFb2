with open('app/src/main/res/layout/item_book_shimmer.xml', 'r') as f:
    text = f.read()

text = text.replace(
    '<!-- Text container under the cover -->\n        <LinearLayout\n            android:layout_width="match_parent"\n            android:layout_height="wrap_content"',
    '<!-- Text container under the cover -->\n        <LinearLayout\n            android:layout_width="match_parent"\n            android:layout_height="72dp"'
)

with open('app/src/main/res/layout/item_book_shimmer.xml', 'w') as f:
    f.write(text)


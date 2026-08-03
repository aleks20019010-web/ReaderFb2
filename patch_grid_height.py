import re

with open('app/src/main/res/layout/item_book_grid.xml', 'r') as f:
    code = f.read()

# Replace root height
code = re.sub(
    r'<com\.google\.android\.material\.card\.MaterialCardView(.*?)android:layout_height="wrap_content"',
    r'<com.google.android.material.card.MaterialCardView\1android:layout_height="280dp"',
    code, flags=re.DOTALL | re.MULTILINE, count=1
)

# Replace inner LinearLayout height
code = re.sub(
    r'<LinearLayout\s+android:layout_width="match_parent"\s+android:layout_height="wrap_content"\s+android:orientation="vertical">',
    r'<LinearLayout\n        android:layout_width="match_parent"\n        android:layout_height="match_parent"\n        android:orientation="vertical">',
    code, count=1
)

with open('app/src/main/res/layout/item_book_grid.xml', 'w') as f:
    f.write(code)


with open('app/src/main/res/layout/item_book_shimmer.xml', 'r') as f:
    shimmer = f.read()

shimmer = re.sub(
    r'<com\.google\.android\.material\.card\.MaterialCardView(.*?)android:layout_height="wrap_content"',
    r'<com.google.android.material.card.MaterialCardView\1android:layout_height="280dp"',
    shimmer, flags=re.DOTALL | re.MULTILINE, count=1
)

shimmer = re.sub(
    r'<LinearLayout\s+android:layout_width="match_parent"\s+android:layout_height="wrap_content"\s+android:orientation="vertical">',
    r'<LinearLayout\n        android:layout_width="match_parent"\n        android:layout_height="match_parent"\n        android:orientation="vertical">',
    shimmer, count=1
)

with open('app/src/main/res/layout/item_book_shimmer.xml', 'w') as f:
    f.write(shimmer)


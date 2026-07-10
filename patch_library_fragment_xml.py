import sys

file_path = 'app/src/main/res/layout/library_fragment.xml'
with open(file_path, 'r') as f:
    content = f.read()

new_banner = """        <!-- New Books Banner -->
        <LinearLayout
            android:id="@+id/layoutNewBooksBanner"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:background="@drawable/bg_panel"
            android:padding="12dp"
            android:layout_marginBottom="12dp"
            android:gravity="center_vertical"
            android:visibility="gone">

            <TextView
                android:id="@+id/tvNewBooksCount"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:text="Найдено новых книг: X"
                android:textColor="@color/text_primary"
                android:textSize="14sp" />

            <TextView
                android:id="@+id/btnShowNewBooks"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="Показать"
                android:textColor="@color/accent"
                android:textSize="14sp"
                android:textStyle="bold"
                android:padding="4dp"
                android:background="?attr/selectableItemBackground"
                android:clickable="true" />

            <ImageView
                android:id="@+id/btnCloseNewBooks"
                android:layout_width="24dp"
                android:layout_height="24dp"
                android:src="@android:drawable/ic_menu_close_clear_cancel"
                android:layout_marginStart="8dp"
                android:padding="4dp"
                android:background="?attr/selectableItemBackground"
                android:clickable="true"
                app:tint="@color/text_author" />
        </LinearLayout>

        <!-- Continue Reading Section -->"""

content = content.replace("        <!-- Continue Reading Section -->", new_banner)

with open(file_path, 'w') as f:
    f.write(content)

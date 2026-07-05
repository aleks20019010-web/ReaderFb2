with open("app/src/main/res/layout/fragment_sync.xml", "r") as f:
    content = f.read()

new_folder_ui = """                        <!-- NEW: Folder Selection -->
                        <LinearLayout
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:orientation="horizontal"
                            android:gravity="center_vertical"
                            android:layout_marginBottom="16dp">
                            <LinearLayout
                                android:layout_width="0dp"
                                android:layout_height="wrap_content"
                                android:layout_weight="1"
                                android:orientation="vertical">
                                <TextView
                                    android:layout_width="wrap_content"
                                    android:layout_height="wrap_content"
                                    android:text="Папка для синхронизации:"
                                    android:textColor="@color/text_secondary"
                                    android:textSize="12sp" />
                                <TextView
                                    android:id="@+id/txtSyncFolder"
                                    android:layout_width="wrap_content"
                                    android:layout_height="wrap_content"
                                    android:text="/Books"
                                    android:textColor="@color/text_primary"
                                    android:textSize="16sp"
                                    android:textStyle="bold" />
                            </LinearLayout>
                            <Button
                                android:id="@+id/btnSelectFolder"
                                android:layout_width="wrap_content"
                                android:layout_height="wrap_content"
                                android:text="Выбрать"
                                style="?attr/borderlessButtonStyle"
                                android:textColor="@color/accent" />
                        </LinearLayout>

                        <Button"""

content = content.replace("                        <Button\n                            android:id=\"@+id/btnSyncNow\"", new_folder_ui + "\n                            android:id=\"@+id/btnSyncNow\"")

with open("app/src/main/res/layout/fragment_sync.xml", "w") as f:
    f.write(content)

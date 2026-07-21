import re

file_path = "app/src/main/res/layout/fragment_local_ai.xml"
with open(file_path, "r") as f:
    content = f.read()

explanation = """
                        <!-- Explanation for AI Model -->
                        <TextView
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:text="Примечание: генерация краткого содержания, аннотаций и анализ персонажей работают только при установленной полной ИИ-модели."
                            android:textColor="@color/accent"
                            android:textSize="12sp"
                            android:layout_marginBottom="12dp"
                            android:textAlignment="center" />

                        <!-- Action Buttons -->
"""

content = content.replace("                        <!-- Action Buttons -->", explanation)

init_button = """
                        <Button
                            android:id="@+id/btnInitModel"
                            style="@style/Widget.Material3.Button.TonalButton"
                            android:layout_width="match_parent"
                            android:layout_height="52dp"
                            android:text="Инициализировать модель"
                            android:textColor="@color/text_primary"
                            android:textStyle="bold"
                            android:visibility="gone"
                            android:layout_marginBottom="8dp"
                            app:cornerRadius="12dp" />
"""
content = content.replace('app:cornerRadius="12dp" />\n\n                        <Button\n                            android:id="@+id/btnUploadCustomRules"', 'app:cornerRadius="12dp" />\n\n' + init_button + '\n                        <Button\n                            android:id="@+id/btnUploadCustomRules"')

with open(file_path, "w") as f:
    f.write(content)

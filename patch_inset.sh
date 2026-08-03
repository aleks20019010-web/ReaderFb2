sed -i -e '/private var systemTopInset: Int = 0/a\    var systemCutoutTop: Int = 0' app/src/main/java/com/nightread/app/ui/BookReaderActivity.kt
sed -i -e '/systemTopInset = topInset/a\            systemCutoutTop = displayCutout.top' app/src/main/java/com/nightread/app/ui/BookReaderActivity.kt

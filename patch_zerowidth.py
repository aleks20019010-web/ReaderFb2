import re

with open("app/src/main/java/com/nightread/app/ui/TextFormatter.kt", "r") as f:
    tf = f.read()

zero_width = """
    private class ZeroWidthSpan : android.text.style.ReplacementSpan() {
        override fun getSize(paint: android.graphics.Paint, text: CharSequence, start: Int, end: Int, fm: android.graphics.Paint.FontMetricsInt?): Int = 0
        override fun draw(canvas: android.graphics.Canvas, text: CharSequence, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: android.graphics.Paint) {}
    }
"""

if "ZeroWidthSpan" not in tf:
    tf = tf.replace("object TextFormatter {", "object TextFormatter {\n" + zero_width)

tf = tf.replace("AbsoluteSizeSpan(0)", "ZeroWidthSpan()")

with open("app/src/main/java/com/nightread/app/ui/TextFormatter.kt", "w") as f:
    f.write(tf)

print("Applied ZeroWidthSpan")

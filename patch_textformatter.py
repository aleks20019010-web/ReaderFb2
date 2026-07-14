with open("app/src/main/java/com/nightread/app/ui/TextFormatter.kt", "r") as f:
    tf = f.read()

tf = tf.replace("ZeroWidthSpan()", "AbsoluteSizeSpan(0)")

with open("app/src/main/java/com/nightread/app/ui/TextFormatter.kt", "w") as f:
    f.write(tf)
print("Reverted to AbsoluteSizeSpan")

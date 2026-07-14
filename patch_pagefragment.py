with open("app/src/main/java/com/nightread/app/ui/PageFragment.kt", "r") as f:
    text = f.read()

text = text.replace("v.setPadding(dp6, dp8 + topInset, dp6, dp8)", 
    "val dp14 = (14 * v.resources.displayMetrics.density).toInt()\n            v.setPadding(dp6, dp8 + topInset, dp6, dp14)")

with open("app/src/main/java/com/nightread/app/ui/PageFragment.kt", "w") as f:
    f.write(text)
print("Success")

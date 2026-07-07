import re

with open('app/src/main/java/com/nightread/app/ui/ReadingActivity.kt', 'r') as f:
    content = f.read()

# Replace btnBack click listener
content = content.replace(
    "findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }",
    "findViewById<ImageButton>(R.id.btnBack).setOnClickListener { onBackPressedDispatcher.onBackPressed() }"
)

# Add OnBackPressedCallback in onCreate
on_create_code = """
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isTaskRoot) {
                    startActivity(android.content.Intent(this@ReadingActivity, com.nightread.app.MainActivity::class.java))
                }
                finish()
            }
        })
"""

# Insert it before setupGestures()
content = content.replace(
    "setupGestures()",
    on_create_code + "\n        setupGestures()"
)

with open('app/src/main/java/com/nightread/app/ui/ReadingActivity.kt', 'w') as f:
    f.write(content)

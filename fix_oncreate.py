import re

with open('app/src/main/java/com/nightread/app/ui/ReadingActivity.kt', 'r') as f:
    content = f.read()

bad_block = r"        onBackPressedDispatcher\.addCallback\(this, object : androidx\.activity\.OnBackPressedCallback\(true\) \{\n            override fun handleOnBackPressed\(\) \{\n                if \(isTaskRoot\) \{\n                    startActivity\(android\.content\.Intent\(this@ReadingActivity, com\.nightread\.app\.MainActivity::class\.java\)\)\n                \}\n                finish\(\)\n            \}\n        \}\)\n\n        setupGestures\(\)\n        gestureDetector = android\.view\.GestureDetector\(this, object : android\.view\.GestureDetector\.SimpleOnGestureListener\(\) \{\n            override fun onSingleTapConfirmed\(e: android\.view\.MotionEvent\): Boolean \{\n                val cornerSize = 80 \* resources\.displayMetrics\.density\n                if \(e\.x < cornerSize && e\.y < cornerSize\) \{\n                    toggleNightMode\(\)\n                \} else \{\n                    toggleBars\(\)\n                \}\n                return super\.onSingleTapConfirmed\(e\)\n            \}\n        \}\)"

good_block = r"""        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isTaskRoot) {
                    startActivity(android.content.Intent(this@ReadingActivity, com.nightread.app.MainActivity::class.java))
                }
                finish()
            }
        })

        setupGestures()"""

content = re.sub(bad_block, good_block, content, flags=re.DOTALL)

with open('app/src/main/java/com/nightread/app/ui/ReadingActivity.kt', 'w') as f:
    f.write(content)

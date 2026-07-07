import re

with open('app/src/main/java/com/nightread/app/ui/ReadingActivity.kt', 'r') as f:
    content = f.read()

# Fix the messy function declaration
messy_pattern = r"    private fun \n        onBackPressedDispatcher\.addCallback\(this, object : androidx\.activity\.OnBackPressedCallback\(true\) \{\n            override fun handleOnBackPressed\(\) \{\n                if \(isTaskRoot\) \{\n                    startActivity\(android\.content\.Intent\(this@ReadingActivity, com\.nightread\.app\.MainActivity::class\.java\)\)\n                \}\n                finish\(\)\n            \}\n        \}\)\n\n        setupGestures\(\) \{"

fixed_replacement = r"    private fun setupGestures() {"

content = re.sub(messy_pattern, fixed_replacement, content, flags=re.DOTALL)

with open('app/src/main/java/com/nightread/app/ui/ReadingActivity.kt', 'w') as f:
    f.write(content)

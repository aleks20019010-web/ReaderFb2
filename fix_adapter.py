import re

with open("app/src/main/java/com/nightread/app/ui/BookAdapter.kt", "r") as f:
    content = f.read()

# Fix the areItemsTheSame injection
bad_block = '''            val pbProgress = itemView.findViewById<ProgressBar>(R.id.pbReadingProgress)
            if (pbProgress != null) {
                if (book.progress == 0) {
                    pbProgress.progress = 0
                    // Set color to 20% transparent (33 alpha)
                    pbProgress.progressBackgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#337A9B6A"))
                } else {
                    pbProgress.progress = book.progress
                    pbProgress.progressBackgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#337A9B6A"))
                }
            }'''
content = content.replace(bad_block, '')

# Add the proper pb progress logic replacing the old one
old_pb = '''            // Set reading progress
            val progressPercent = if (book.totalCharacters > 0) {
                ((book.currentProgressChar.toFloat() / book.totalCharacters) * 100).toInt().coerceIn(0, 100)
            } else {
                0
            }

            if (progressPercent > 0) {
                pbReadingProgress.visibility = View.VISIBLE
                pbReadingProgress.progress = progressPercent
            } else {
                pbReadingProgress.visibility = View.GONE
            }'''

new_pb = '''            // Set reading progress
            val progressPercent = if (book.totalCharacters > 0) {
                ((book.currentProgressChar.toFloat() / book.totalCharacters) * 100).toInt().coerceIn(0, 100)
            } else {
                0
            }

            pbReadingProgress.visibility = View.VISIBLE
            pbReadingProgress.progress = progressPercent
            pbReadingProgress.progressBackgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#337A9B6A"))
            
            if (progressPercent == 0) {
                pbReadingProgress.progressBackgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#337A9B6A"))
                pbReadingProgress.progressTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#337A9B6A"))
            } else {
                pbReadingProgress.progressTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#7A9B6A"))
            }'''

content = content.replace(old_pb, new_pb)

with open("app/src/main/java/com/nightread/app/ui/BookAdapter.kt", "w") as f:
    f.write(content)


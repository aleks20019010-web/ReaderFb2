import sys

file = 'app/src/main/java/com/nightread/app/ui/ReadingActivity.kt'
content = open(file).read()

old_code = """                val oldCount = splitResult.pages.size
                splitResult = result
                isSplittingFinished = result.isFinished
                
                (viewPager.adapter as ReaderPagerAdapter).pages = result.pages
                viewPager.adapter?.notifyDataSetChanged()
                
                if (isFirstRender && result.pages.isNotEmpty()) {"""

new_code = """                val oldCount = splitResult.pages.size
                splitResult = result
                isSplittingFinished = result.isFinished
                
                val adapter = viewPager.adapter as ReaderPagerAdapter
                adapter.pages = result.pages
                val newCount = result.pages.size
                
                if (isFirstRender) {
                    adapter.notifyDataSetChanged()
                } else if (newCount > oldCount) {
                    adapter.notifyItemRangeInserted(oldCount, newCount - oldCount)
                }
                
                if (isFirstRender && result.pages.isNotEmpty()) {"""

content = content.replace(old_code, new_code)
open(file, 'w').write(content)

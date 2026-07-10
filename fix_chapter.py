import sys

file = 'app/src/main/java/com/nightread/app/ui/ReadingActivity.kt'
content = open(file).read()

old_code = """        val pattern = java.util.regex.Pattern.compile("\[CHAPTER\](.*?)\[/CHAPTER\]", java.util.regex.Pattern.DOTALL)"""
new_code = """        val pattern = java.util.regex.Pattern.compile("\\\\[CHAPTER\\\\](.*?)\\\\[/CHAPTER\\\\]", java.util.regex.Pattern.DOTALL)"""
content = content.replace(old_code, new_code)
open(file, 'w').write(content)

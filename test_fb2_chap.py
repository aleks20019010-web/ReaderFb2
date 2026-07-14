import re

text = """
<title><p>Chapter 1</p></title>
<empty-line/>
<p>Text of chapter</p>
"""

text = re.sub(r'<empty-line[^>]*>', '\n\n', text)
text = re.sub(r'<title[^>]*>', '\n\u000C[CHAPTER]', text)
text = re.sub(r'</title>', '[/CHAPTER]\n', text)
text = re.sub(r'<p[^>]*>', '\n\u00A0\u00A0\u00A0\u00A0', text)
text = re.sub(r'</p>', '', text)
text = re.sub(r'<[^>]+>', '', text)

text = re.sub(r'([ \t\r]*\n[ \t\r]*){2,}', '\n', text)
text = re.sub(r'\n[ \t\r]+(?=\u00A0)', '\n', text)
text = re.sub(r'\u000C+', '\u000C', text)
print(text.replace('\u00A0', 'X'))

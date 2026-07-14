import re

text = """
        <p>Paragraph 1</p>
        <empty-line/>
        <empty-line/>
        <p>Paragraph 2</p>
        <p>- Dialog 1</p>
        <p> - Dialog 2</p>
"""

text = re.sub(r'<empty-line[^>]*>', '\n\n', text)
text = re.sub(r'<p[^>]*>', '\n\u00A0\u00A0\u00A0\u00A0', text)
text = re.sub(r'</p>', '', text)
text = re.sub(r'<[^>]+>', '', text)

print("After tags:")
print(text.replace('\u00A0', 'X'))

text = re.sub(r'([ \t\r]*\n[ \t\r]*){2,}', '\n', text)
text = re.sub(r'\n[ \t\r]+(?=\u00A0)', '\n', text)

print("Final text:")
print(text.replace('\u00A0', 'X'))

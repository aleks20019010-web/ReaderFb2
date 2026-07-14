import re
v = "[аеёиоуыэюя]"
c = "[бвгджзклмнпрстфхцчшщ]"
s = "[йьъ]"
l = "[а-яА-ЯёЁ]"

regex1 = re.compile(f"(?<={l})({v})(?={c}{v})")
regex2 = re.compile(f"({v}{c})(?={c}{v})")
regex3 = re.compile(f"({v}{c})(?={c}{c}{v})")
regex4 = re.compile(f"({v}{s})(?={c}{v})")

def hyphenate(text):
    text = regex1.sub(r"\1-", text)
    text = regex2.sub(r"\1-", text)
    text = regex3.sub(r"\1-", text)
    text = regex4.sub(r"\1-", text)
    return text

print(hyphenate("велосипед Велосипед ВЕЛОСИПЕД Привет ПРИВЕТ оса Оса"))
print(hyphenate("поддержать Поддержать ПОДДЕРЖАТЬ стройка Стройка СТРОЙКА подъезд Подъезд ПОДЪЕЗД"))


import re

v = "[аеёиоуыэюяАЕЁИОУЫЭЮЯ]"
c = "[бвгджзклмнпрстфхцчшщБВГДЖЗКЛМНПРСТФХЦЧШЩ]"
s = "[йьъЙЬЪ]"

def hyphenateWord(word):
    if len(word) < 4: return word
    res = word
    oldRes = ""
    while res != oldRes:
        oldRes = res
        res = re.sub(f"({v})({c}{v})", r"\1\xad\2", res)
        res = re.sub(f"({v}{c})({c}{v})", r"\1\xad\2", res)
        res = re.sub(f"({v}{c})({c}{c}{v})", r"\1\xad\2", res)
        res = re.sub(f"({v}{s})({c}{v})", r"\1\xad\2", res)
    
    if res.startswith('\xad'): res = res[1:]
    if len(res) > 1 and res[1] == '\xad': res = res[0] + res[2:]
    if res.endswith('\xad'): res = res[:-1]
    if len(res) > 2 and res[-2] == '\xad': res = res[:-2] + res[-1]
    
    return res

words = ["проверка", "слово", "солнце", "объявление", "построение", "электричество"]
for w in words:
    print(w, "->", hyphenateWord(w).replace('\xad', '-'))

with open("app/src/main/java/com/nightread/app/MainActivity.kt") as f:
    text = f.read()
class_start = text.find("class MainActivity")
content = text[class_start:]
print(f"Open: {content.count('{')}, Close: {content.count('}')}")

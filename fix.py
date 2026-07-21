with open("app/src/main/java/com/nightread/app/MainActivity.kt") as f:
    text = f.read()

lines = text.split("\n")
count = 0
for i, line in enumerate(lines):
    count += line.count("{")
    count -= line.count("}")
    if count < 0:
        print(f"Negative count at line {i+1}: {line}")

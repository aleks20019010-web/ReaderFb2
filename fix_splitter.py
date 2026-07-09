with open("app/src/main/java/com/nightread/app/ui/PageSplitter.kt", "r") as f:
    lines = f.readlines()

new_lines = []
inside_bad = False
for line in lines:
    if line.strip() == "}":
        pass
    if "-e" in line:
        pass

# We will just write a clean PageSplitter.kt script replacing the bottom part.

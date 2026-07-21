file_path = "app/src/main/res/layout/fragment_local_ai.xml"
with open(file_path, "r") as f:
    lines = f.readlines()

out = []
in_button = False
for line in lines:
    if "android:id=\"@+id/btnInitModel\"" in line or "android:id=\"@+id/btnUploadCustomRules\"" in line:
        in_button = True
    
    if in_button and "android:textColor=\"@color/text_primary\"" in line:
        continue # skip this line
        
    if in_button and "/>" in line:
        in_button = False
        
    out.append(line)

with open(file_path, "w") as f:
    f.writelines(out)

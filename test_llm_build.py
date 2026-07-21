import subprocess
print(subprocess.run(["gradle", ":app:compileDebugKotlin"], capture_output=True, text=True).stdout)

with open("app/src/test/java/com/nightread/app/ReaderLifecycleRestoreTest.kt", "r") as f:
    content = f.read()

content = content.replace("// Let pagination run", "// Let pagination run\n        waitForPages(pager)")
content = content.replace("pager.goToOffset(targetOffset)", "pager.goToOffset(targetOffset)\n        waitForPages(pager)")

with open("app/src/test/java/com/nightread/app/ReaderLifecycleRestoreTest.kt", "w") as f:
    f.write(content)

import re

with open('app/src/main/java/com/nightread/app/service/TtsForegroundService.kt', 'r') as f:
    code = f.read()

# Fix ACTION_START logic
code = code.replace(
    '''if (customText.isNotEmpty() && TtsDataProvider.paragraphs.isEmpty()) {
                    currentText = customText
                    currentParagraphIndex = 0
                } else {
                    currentParagraphIndex = startIdx
                }''',
    '''if (customText.isNotEmpty()) {
                    currentText = customText
                    currentParagraphIndex = -1
                } else {
                    currentParagraphIndex = startIdx
                }'''
)

# Fix speakCurrentText logic
code = code.replace(
    '''if (TtsDataProvider.paragraphs.isNotEmpty() && currentParagraphIndex in TtsDataProvider.paragraphs.indices) {''',
    '''if (currentParagraphIndex >= 0 && TtsDataProvider.paragraphs.isNotEmpty() && currentParagraphIndex in TtsDataProvider.paragraphs.indices) {'''
)

# Fix onDone logic
code = code.replace(
    '''if (continuous) {
                        currentParagraphIndex++''',
    '''if (continuous && currentParagraphIndex >= 0) {
                        currentParagraphIndex++'''
)

with open('app/src/main/java/com/nightread/app/service/TtsForegroundService.kt', 'w') as f:
    f.write(code)


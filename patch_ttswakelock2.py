with open('app/src/main/java/com/nightread/app/service/TtsForegroundService.kt', 'r') as f:
    code = f.read()

code = code.replace(
    'private fun pauseTts() {\n        if (tts?.isSpeaking == true) {\n            tts?.stop()\n        }\n        isSpeakingState = false\n        updateNotification(false)\n        sendStatusBroadcast(isPlaying = false, isDone = false)\n    }',
    'private fun pauseTts() {\n        if (tts?.isSpeaking == true) {\n            tts?.stop()\n        }\n        isSpeakingState = false\n        updateNotification(false)\n        releaseWakeLock()\n        sendStatusBroadcast(isPlaying = false, isDone = false)\n    }'
)

code = code.replace(
    'private fun stopTts() {\n        tts?.stop()\n        isSpeakingState = false\n        sendStatusBroadcast(isPlaying = false, isDone = false)\n    }',
    'private fun stopTts() {\n        tts?.stop()\n        isSpeakingState = false\n        releaseWakeLock()\n        sendStatusBroadcast(isPlaying = false, isDone = false)\n    }'
)

with open('app/src/main/java/com/nightread/app/service/TtsForegroundService.kt', 'w') as f:
    f.write(code)

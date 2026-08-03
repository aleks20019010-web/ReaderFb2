import re

with open('app/src/main/java/com/nightread/app/service/TtsForegroundService.kt', 'r') as f:
    code = f.read()

# Add variable
code = code.replace(
    'private var isSpeakingState: Boolean = false',
    'private var isSpeakingState: Boolean = false\n    private var wakeLock: android.os.PowerManager.WakeLock? = null'
)

# Acquire wakelock on start/resume
code = code.replace(
    'tts?.setSpeechRate(speechRate)',
    'tts?.setSpeechRate(speechRate)\n                acquireWakeLock()'
)

code = code.replace(
    'isSpeakingState = true\n        val params',
    'isSpeakingState = true\n        acquireWakeLock()\n        val params'
)

# Create acquireWakeLock and releaseWakeLock functions
funcs = '''
    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
            wakeLock = powerManager.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "NightRead:TtsWakeLock")
        }
        if (wakeLock?.isHeld == false) {
            wakeLock?.acquire(10 * 60 * 1000L /*10 minutes max per acquire just in case*/)
        }
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
    }
'''

code = code.replace(
    'private fun speakCurrentText() {',
    funcs + '\n    private fun speakCurrentText() {'
)

# Release wakelock on pause/stop/done
code = code.replace(
    'tts?.stop()\n        isSpeakingState = false\n        updateNotification(false)',
    'tts?.stop()\n        isSpeakingState = false\n        updateNotification(false)\n        releaseWakeLock()'
)

code = code.replace(
    'isSpeakingState = false\n                        updateNotification(false)',
    'isSpeakingState = false\n                        updateNotification(false)\n                        releaseWakeLock()'
)

code = code.replace(
    'isSpeakingState = false\n                    updateNotification(false)',
    'isSpeakingState = false\n                    updateNotification(false)\n                    releaseWakeLock()'
)

code = code.replace(
    'isServiceRunning = false\n        tts?.stop()',
    'isServiceRunning = false\n        releaseWakeLock()\n        tts?.stop()'
)

with open('app/src/main/java/com/nightread/app/service/TtsForegroundService.kt', 'w') as f:
    f.write(code)


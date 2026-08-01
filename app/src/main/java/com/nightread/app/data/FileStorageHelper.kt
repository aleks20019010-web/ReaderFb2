package com.nightread.app.data

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Вспомогательный класс для управления файлами приложения во внутреннем хранилище.
 * Отвечает за сохранение и получение пользовательского фона (/files/user_bg.jpg).
 */
object FileStorageHelper {

    private const val TAG = "FileStorageHelper"
    const val USER_BG_DARK_FILE_NAME = "user_bg_dark.jpg"
    const val USER_BG_LIGHT_FILE_NAME = "user_bg_light.jpg"
    const val USER_BG_LEGACY_FILE_NAME = "user_bg.jpg"

    /**
     * Возвращает файл пользовательского фона для указанной или текущей темы.
     */
    fun getUserBackgroundFile(context: Context, isNightMode: Boolean = ThemeHelper.shouldBeNightMode(context)): File {
        val fileName = if (isNightMode) USER_BG_DARK_FILE_NAME else USER_BG_LIGHT_FILE_NAME
        val file = File(context.filesDir, fileName)
        if (!file.exists()) {
            val legacyFile = File(context.filesDir, USER_BG_LEGACY_FILE_NAME)
            if (legacyFile.exists()) return legacyFile
        }
        return file
    }

    /**
     * Проверяет, существует ли сохраненное пользовательское изображение фона.
     */
    fun hasUserBackground(context: Context, isNightMode: Boolean = ThemeHelper.shouldBeNightMode(context)): Boolean {
        val file = getUserBackgroundFile(context, isNightMode)
        return file.exists() && file.length() > 0
    }

    /**
     * Сохраняет выбранную пользователем картинку из Uri в соответствующий файл темы.
     */
    fun saveUserBackground(context: Context, uri: Uri): Boolean {
        var inputStream: InputStream? = null
        var outputStream: FileOutputStream? = null
        val isNightMode = ThemeHelper.shouldBeNightMode(context)
        return try {
            val destFile = getUserBackgroundFile(context, isNightMode)
            if (destFile.exists()) {
                destFile.delete()
            }
            inputStream = context.contentResolver.openInputStream(uri) ?: return false
            outputStream = FileOutputStream(destFile)

            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }
            outputStream.flush()
            Log.d(TAG, "Custom background saved successfully: ${destFile.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save user background from URI $uri", e)
            false
        } finally {
            try {
                inputStream?.close()
                outputStream?.close()
            } catch (_: Exception) {}
        }
    }

    /**
     * Удаляет пользовательский фон.
     */
    fun removeUserBackground(context: Context, isNightMode: Boolean = ThemeHelper.shouldBeNightMode(context)): Boolean {
        val file = getUserBackgroundFile(context, isNightMode)
        return if (file.exists()) {
            file.delete()
        } else false
    }
}

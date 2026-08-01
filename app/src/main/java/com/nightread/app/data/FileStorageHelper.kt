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
    const val USER_BG_FILE_NAME = "user_bg.jpg"

    /**
     * Возвращает файл пользовательского фона во внутреннем хранилище (/files/user_bg.jpg).
     */
    fun getUserBackgroundFile(context: Context): File {
        return File(context.filesDir, USER_BG_FILE_NAME)
    }

    /**
     * Проверяет, существует ли сохраненное пользовательское изображение фона.
     */
    fun hasUserBackground(context: Context): Boolean {
        val file = getUserBackgroundFile(context)
        return file.exists() && file.length() > 0
    }

    /**
     * Сохраняет выбранную пользователем картинку из Uri во внутреннее хранилище (/files/user_bg.jpg).
     */
    fun saveUserBackground(context: Context, uri: Uri): Boolean {
        var inputStream: InputStream? = null
        var outputStream: FileOutputStream? = null
        return try {
            val destFile = getUserBackgroundFile(context)
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
    fun removeUserBackground(context: Context): Boolean {
        val file = getUserBackgroundFile(context)
        return if (file.exists()) {
            file.delete()
        } else false
    }
}

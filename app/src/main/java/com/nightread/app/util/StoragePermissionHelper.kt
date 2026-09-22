package com.nightread.app.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.nightread.app.R
import com.nightread.app.ui.CustomToast

object StoragePermissionHelper {

    const val REQUEST_CODE_LEGACY_STORAGE = 1001
    const val REQUEST_CODE_ALL_FILES = 1002

    /**
     * Checks if the app has required permissions to scan and read book files.
     */
    fun hasStoragePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Ensures permission is granted before executing [onGranted].
     * If permission is missing, prompts the user via explanation dialog and system permission screen.
     */
    fun checkAndRequestStoragePermission(activity: Activity, onGranted: () -> Unit) {
        if (hasStoragePermission(activity)) {
            onGranted()
            return
        }

        AlertDialog.Builder(activity)
            .setTitle(R.string.storage_permission_title)
            .setMessage(R.string.storage_permission_message)
            .setPositiveButton(R.string.storage_permission_grant) { _, _ ->
                requestPermissionDirectly(activity)
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                CustomToast.show(activity, activity.getString(R.string.storage_permission_denied))
            }
            .show()
    }

    private fun requestPermissionDirectly(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${activity.packageName}")
                }
                activity.startActivityForResult(intent, REQUEST_CODE_ALL_FILES)
            } catch (e: Exception) {
                try {
                    val fallbackIntent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    activity.startActivityForResult(fallbackIntent, REQUEST_CODE_ALL_FILES)
                } catch (e2: Exception) {
                    CustomToast.show(activity, "Не удалось открыть настройки разрешений: ${e2.message}")
                }
            }
        } else {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ),
                REQUEST_CODE_LEGACY_STORAGE
            )
        }
    }
}

package com.nightread.app.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.nightread.app.BuildConfig
import com.nightread.app.R
import com.nightread.app.ui.CustomToast

object BugReportHelper {

    private const val SUPPORT_EMAIL = "aleks20019010@gmail.com"

    fun showBugReportDialog(activity: Activity) {
        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_bug_report, null)
        val etDescription = dialogView.findViewById<EditText>(R.id.etBugDescription)
        val tvDeviceInfo = dialogView.findViewById<TextView>(R.id.tvDeviceInfoPreview)

        val sysInfo = buildSystemDiagnostics(activity)
        tvDeviceInfo?.text = "Устройство: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})\nВерсия: ${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})"

        AlertDialog.Builder(activity)
            .setTitle(R.string.bug_report_dialog_title)
            .setView(dialogView)
            .setPositiveButton(R.string.bug_report_send_btn) { _, _ ->
                val userDescription = etDescription.text.toString().trim()
                if (userDescription.isEmpty()) {
                    CustomToast.show(activity, activity.getString(R.string.bug_report_empty_desc_warning))
                    return@setPositiveButton
                }
                sendBugReportEmail(activity, userDescription, sysInfo)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    fun sendBugReportEmail(context: Context, userDescription: String, extraDiagnostics: String = "") {
        try {
            val diagnostics = if (extraDiagnostics.isNotEmpty()) extraDiagnostics else buildSystemDiagnostics(context)
            val subject = "[NightRead Bug Report] v${BuildConfig.VERSION_NAME} - ${Build.MANUFACTURER} ${Build.MODEL}"
            
            val emailBody = buildString {
                append("=== ОПИСАНИЕ ПРОБЛЕМЫ ===\n")
                append(userDescription)
                append("\n\n")
                append("=== СИСТЕМНАЯ ДИАГНОСТИКА ===\n")
                append(diagnostics)
            }

            val mailUri = Uri.parse("mailto:$SUPPORT_EMAIL")
            val emailIntent = Intent(Intent.ACTION_SENDTO, mailUri).apply {
                putExtra(Intent.EXTRA_EMAIL, arrayOf(SUPPORT_EMAIL))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, emailBody)
            }

            val chooser = Intent.createChooser(emailIntent, context.getString(R.string.bug_report_choose_email_client))
            context.startActivity(chooser)
        } catch (e: Exception) {
            CustomToast.show(context, "Не найдено почтовое приложение: ${e.message}")
        }
    }

    private fun buildSystemDiagnostics(context: Context): String {
        return buildString {
            append("App Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n")
            append("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.PRODUCT})\n")
            append("Android OS: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n")
            append("Display: ${context.resources.displayMetrics.widthPixels}x${context.resources.displayMetrics.heightPixels} (${context.resources.displayMetrics.densityDpi}dpi)\n")
            
            val runtime = Runtime.getRuntime()
            val maxMemoryMb = runtime.maxMemory() / (1024 * 1024)
            val totalMemoryMb = runtime.totalMemory() / (1024 * 1024)
            val freeMemoryMb = runtime.freeMemory() / (1024 * 1024)
            append("Memory: Max=${maxMemoryMb}MB, Allocated=${totalMemoryMb}MB, Free=${freeMemoryMb}MB\n")

            val crashPrefs = context.getSharedPreferences("crash_prefs", Context.MODE_PRIVATE)
            val lastCrash = crashPrefs.getString("last_crash", null)
            if (!lastCrash.isNullOrBlank()) {
                append("\n--- ПОСЛЕДНИЙ СБОЙ / CRASH LOG ---\n")
                append(lastCrash.take(2000))
            }
        }
    }
}

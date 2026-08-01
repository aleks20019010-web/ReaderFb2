package com.nightread.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nightread.app.data.SettingsManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            if (SettingsManager.isAutoSyncEnabled(context)) {
                AutoSyncScheduler.scheduleAutoSync(context, forceReplace = false)
            }
        }
    }
}

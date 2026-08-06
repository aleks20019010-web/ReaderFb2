package com.nightread.app.core.preferences

import android.content.SharedPreferences

class SyncPreferences(private val getPrefs: () -> SharedPreferences?) {

    fun isAutoSyncEnabled(): Boolean = getPrefs()?.getBoolean("auto_sync", true) ?: true
    fun setAutoSyncEnabled(value: Boolean) {
        getPrefs()?.edit()?.putBoolean("auto_sync", value)?.apply()
    }

    fun getAutoSyncIntervalDays(): Int = getPrefs()?.getInt("auto_sync_interval_days", 1) ?: 1
    fun setAutoSyncIntervalDays(value: Int) {
        getPrefs()?.edit()?.putInt("auto_sync_interval_days", value)?.apply()
    }

    fun getAutoSyncStartTime(): String = getPrefs()?.getString("auto_sync_start_time", "02:00") ?: "02:00"
    fun setAutoSyncStartTime(value: String) {
        getPrefs()?.edit()?.putString("auto_sync_start_time", value)?.apply()
    }
}

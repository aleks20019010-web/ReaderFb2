package com.nightread.app.core.preferences

import android.content.Context
import android.content.SharedPreferences

class SyncPreferences(private val context: Context) {
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    var token: String?
        get() = prefs.getString(KEY_OAUTH_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_OAUTH_TOKEN, value).apply()

    var isAutoSyncEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SYNC, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_SYNC, value).apply()

    var syncWifiOnly: Boolean
        get() = prefs.getBoolean(KEY_WIFI_ONLY, true)
        set(value) = prefs.edit().putBoolean(KEY_WIFI_ONLY, value).apply()

    var lastSyncTimestamp: Long
        get() = prefs.getLong(KEY_LAST_SYNC_TIME, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNC_TIME, value).apply()

    var isSyncing: Boolean
        get() = prefs.getBoolean(KEY_IS_SYNCING, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_SYNCING, value).apply()

    var syncFolder: String
        get() = prefs.getString(KEY_SYNC_FOLDER, "/Books") ?: "/Books"
        set(value) = prefs.edit().putString(KEY_SYNC_FOLDER, value).apply()

    fun clearToken() {
        prefs.edit().remove(KEY_OAUTH_TOKEN).apply()
    }

    companion object {
        private const val PREFS_NAME = "yandex_sync"
        private const val KEY_OAUTH_TOKEN = "oauth_token"
        private const val KEY_AUTO_SYNC = "auto_sync_enabled"
        private const val KEY_WIFI_ONLY = "sync_wifi_only"
        private const val KEY_LAST_SYNC_TIME = "last_sync_timestamp"
        private const val KEY_IS_SYNCING = "is_syncing"
        private const val KEY_SYNC_FOLDER = "sync_folder"
    }
}

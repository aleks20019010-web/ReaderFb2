package com.nightread.app

import android.app.Application
import android.util.Log

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.d("MainApplication", "MainApplication onCreate: Initializing app.")
    }
}

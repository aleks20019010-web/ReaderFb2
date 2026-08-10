package com.nightread.app.ui.customlayout.ai

import android.util.Log

object NativeLlamaBridge {
    private const val TAG = "NativeLlamaBridge"
    private var isLibraryLoaded = false

    init {
        try {
            System.loadLibrary("nightread_llama")
            isLibraryLoaded = true
            Log.i(TAG, "Successfully loaded native llama library")
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to load native llama library, using fallback mode", e)
            isLibraryLoaded = false
        }
    }

    external fun nativeInitModel(modelPath: String): Long
    external fun nativeGenerate(modelHandle: Long, prompt: String): String
    external fun nativeFree(modelHandle: Long)

    fun isNativeReady(): Boolean = isLibraryLoaded
}

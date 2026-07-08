package com.nightread.app.data

import android.util.Log
import java.io.File

class Sha1Extractor {
    private val TAG = "Sha1Extractor"

    fun extractSha1(file: File): String? {
        Log.d(TAG, "Extracting SHA-1 from ${file.name}")
        return Sha1Helper.computeSha1FromContent(file)
    }
}

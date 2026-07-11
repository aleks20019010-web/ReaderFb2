package com.nightread.app.data

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.nightread.app.BuildConfig
import kotlinx.coroutines.tasks.await

object FirestoreSyncManager {
    private const val TAG = "FirestoreSyncManager"
    private var isInitialized = false

    // Initialize Firebase programmatically using our secrets from BuildConfig
    @Synchronized
    fun initialize(context: Context): Boolean {
        if (isInitialized) return true
        
        try {
            // Check if Firebase is already initialized (e.g. by google-services.json if provided by the user)
            if (FirebaseApp.getApps(context).isNotEmpty()) {
                isInitialized = true
                Log.d(TAG, "Firebase already initialized (default app).")
                return true
            }

            // Otherwise, initialize programmatically using BuildConfig values
            val projectId = BuildConfig.FIREBASE_PROJECT_ID
            val apiKey = BuildConfig.FIREBASE_API_KEY
            val appId = BuildConfig.FIREBASE_APPLICATION_ID

            // Check if credentials are valid (and not just placeholders)
            if (projectId.isEmpty() || projectId.contains("MY_FIREBASE_PROJECT_ID") || 
                apiKey.isEmpty() || apiKey.contains("MY_FIREBASE_API_KEY") || 
                appId.isEmpty() || appId.contains("MY_FIREBASE_APPLICATION_ID")) {
                Log.w(TAG, "Firestore sync credentials are not configured in Secrets panel.")
                return false
            }

            val options = FirebaseOptions.Builder()
                .setProjectId(projectId)
                .setApiKey(apiKey)
                .setApplicationId(appId)
                .build()

            FirebaseApp.initializeApp(context.applicationContext, options)
            isInitialized = true
            Log.d(TAG, "Firebase initialized successfully using programmatic options.")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Firebase: ${e.message}", e)
            return false
        }
    }

    // Get a reference to Firestore
    private fun getFirestore(context: Context): FirebaseFirestore? {
        if (!initialize(context)) return null
        return try {
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting Firestore instance: ${e.message}", e)
            null
        }
    }

    // Get a unique user/device ID
    fun getUserId(context: Context): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        return if (!androidId.isNullOrEmpty()) androidId else "device_anonymous"
    }

    // Check if Firestore sync is enabled in settings
    fun isSyncEnabled(context: Context): Boolean {
        return false
    }

    // Save reading progress for a book to Firestore
    suspend fun saveProgress(
        context: Context,
        sha1: String,
        title: String,
        pageIndex: Int,
        charOffset: Int,
        totalCharacters: Int
    ) {
        if (!isSyncEnabled(context)) return
        val firestore = getFirestore(context) ?: return
        val userId = getUserId(context)

        val data = hashMapOf(
            "userId" to userId,
            "sha1" to sha1,
            "title" to title,
            "pageIndex" to pageIndex,
            "charOffset" to charOffset,
            "totalCharacters" to totalCharacters,
            "lastReadTime" to System.currentTimeMillis(),
            "deviceModel" to "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
        )

        try {
            // Document ID is a combination of user ID and book SHA1 to prevent overwriting other users' progress
            val docId = "${userId}_${sha1}"
            firestore.collection("reading_progress")
                .document(docId)
                .set(data, SetOptions.merge())
                .await()
            Log.d(TAG, "Successfully saved progress to Firestore for book: $title, page: $pageIndex")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving progress to Firestore: ${e.message}", e)
        }
    }

    // Retrieve reading progress from Firestore for a specific book
    suspend fun retrieveProgress(context: Context, sha1: String): Map<String, Any>? {
        if (!isSyncEnabled(context)) return null
        val firestore = getFirestore(context) ?: return null
        val userId = getUserId(context)
        val docId = "${userId}_${sha1}"

        return try {
            val doc = firestore.collection("reading_progress")
                .document(docId)
                .get()
                .await()
            if (doc.exists()) {
                doc.data
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving progress from Firestore: ${e.message}", e)
            null
        }
    }
    
    // Retrieve ALL progress entries from Firestore for the current user
    suspend fun retrieveAllProgress(context: Context): List<Map<String, Any>> {
        if (!isSyncEnabled(context)) return emptyList()
        val firestore = getFirestore(context) ?: return emptyList()
        val userId = getUserId(context)

        return try {
            val snapshot = firestore.collection("reading_progress")
                .whereEqualTo("userId", userId)
                .get()
                .await()
            snapshot.documents.mapNotNull { it.data }
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving all progress from Firestore: ${e.message}", e)
            emptyList()
        }
    }
}

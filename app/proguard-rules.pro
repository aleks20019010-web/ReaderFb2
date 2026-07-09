# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep the line number information for debugging stack traces.
-keepattributes SourceFile,LineNumberTable,Signature,InnerClasses,EnclosingMethod,*Annotation*

# Preserve the original source file name.
-renamesourcefileattribute SourceFile

# =========================================================================
# NIGHTREAD APP RULES - KEEP ALL APP CLASSES INTACT
# This prevents obfuscation, shrinking, or optimization of your custom code
# =========================================================================
-keep class com.nightread.app.** { *; }
-keepinterface com.nightread.app.** { *; }

# =========================================================================
# ROOM DATABASE RULES
# =========================================================================
-keep class * extends androidx.room.RoomDatabase {
    <init>(...);
}
-keep class * implements androidx.room.RoomDatabase {
    <init>(...);
}
-keep class * { @androidx.room.Dao *; }
-keep @androidx.room.Entity class * { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase {
    @androidx.room.Database *;
}
-dontwarn androidx.room.paging.**

# =========================================================================
# RETROFIT & OKHTTP RULES
# =========================================================================
-keepattributes Signature, InnerClasses, EnclosingMethod, AnnotationDefault, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, RuntimeVisibleTypeAnnotations
-keepclassmembers class * {
    @retrofit2.http.* *;
}
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }
-dontwarn okio.**

# =========================================================================
# MOSHI RULES
# =========================================================================
-keep class com.squareup.moshi.** { *; }
-keepinterface com.squareup.moshi.** { *; }
-dontwarn com.squareup.moshi.**
-keep @com.squareup.moshi.JsonClass class * { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json *;
}

# =========================================================================
# WORKMANAGER & SERVICE RULES
# =========================================================================
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends android.app.Service { *; }
-keep class * extends android.app.Activity { *; }
-keep class * extends android.content.BroadcastReceiver { *; }

# =========================================================================
# YANDEX SDK
# =========================================================================
-dontwarn com.yandex.authsdk.**
-keep class com.yandex.authsdk.** { *; }

# =========================================================================
# KOTLIN & COROUTINES
# =========================================================================
-dontwarn kotlinx.coroutines.**
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.parcelize.**
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}



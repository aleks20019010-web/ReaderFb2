# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep the line number information for debugging stack traces.
-keepattributes SourceFile,LineNumberTable,Signature,InnerClasses,EnclosingMethod,AnnotationDefault,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,RuntimeVisibleTypeAnnotations,*Annotation*

# Preserve the original source file name.
-renamesourcefileattribute SourceFile

# =========================================================================
# NIGHTREAD APP RULES - KEEP ALL APP CLASSES INTACT
# This prevents obfuscation, shrinking, or optimization of your custom code
# =========================================================================
-keep class com.nightread.app.** { *; }
-keep interface com.nightread.app.** { *; }

# =========================================================================
# ROOM DATABASE & SQLITE RULES
# =========================================================================
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**
-keep class androidx.sqlite.** { *; }
-dontwarn androidx.sqlite.**
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
-keep class com.nightread.app.data.**_Impl { *; }
-dontwarn androidx.room.paging.**

# =========================================================================
# RETROFIT & OKHTTP RULES
# =========================================================================
-keepclassmembers class * {
    @retrofit2.http.* *;
}
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }
-dontwarn okio.**

# =========================================================================
# MOSHI & KOTLIN REFLECTION RULES
# =========================================================================
-keep class com.squareup.moshi.** { *; }
-keep interface com.squareup.moshi.** { *; }
-dontwarn com.squareup.moshi.**
-keep @com.squareup.moshi.JsonClass class * { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json *;
}
-keep class *JsonAdapter { *; }
-keep class * extends com.squareup.moshi.JsonAdapter { *; }
-keep class kotlin.reflect.jvm.internal.** { *; }
-keep class kotlin.reflect.** { *; }
-dontwarn kotlin.reflect.**
-keep class com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory { *; }

# =========================================================================
# WORKMANAGER & SERVICE & APP STARTUP RULES
# =========================================================================
-keep class androidx.work.** { *; }
-dontwarn androidx.work.**
-keep class androidx.startup.** { *; }
-dontwarn androidx.startup.**
-keep class androidx.work.impl.WorkManagerInitializer { *; }

-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keepclassmembers class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends android.app.Service { *; }
-keep class * extends android.app.Activity { *; }
-keep class * extends android.content.BroadcastReceiver { *; }

# =========================================================================
# YANDEX SDK
# =========================================================================
-dontwarn com.yandex.**
-keep class com.yandex.** { *; }

# =========================================================================
# KOTLIN, METADATA & COROUTINES
# =========================================================================
-dontwarn kotlinx.coroutines.**
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.parcelize.**
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}
-keep class kotlin.Metadata { *; }

# =========================================================================
# LLAMA.CPP / AI LOCAL LLM RULES
# =========================================================================
-keep class io.github.ljcamargo.** { *; }
-keep interface io.github.ljcamargo.** { *; }

# Do not warn about missing references in the llama.cpp library
-dontwarn io.github.ljcamargo.**

# Keep all native methods and their class definitions intact for JNI linking
-keepclasseswithmembernames class * {
    native <methods>;
}



plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
}
android {
  namespace = "com.nightread.app"
  compileSdk { version = release(36) { minorApiLevel = 1 } }
  defaultConfig {
    manifestPlaceholders["YANDEX_CLIENT_ID"] = "bfdea73d1e6242ba826f15d9d0374005"
    applicationId = "com.nightread.app"
    minSdk = 24
    targetSdk = 36
    versionCode = 6
    versionName = "2.3.3"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }
  signingConfigs {
    getByName("debug") {
      val debugKeystore = listOf(
        file("${System.getProperty("user.home")}/.android/debug.keystore"),
        rootProject.file("debug.keystore"),
        file("debug.keystore")
      ).firstOrNull { it.exists() } ?: file("${System.getProperty("user.home")}/.android/debug.keystore")
      
      storeFile = debugKeystore
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "release.keystore"
      val storeFileObj = if (file(keystorePath).isAbsolute) {
        file(keystorePath)
      } else {
        rootProject.file(keystorePath)
      }
      
      if (storeFileObj.exists()) {
        storeFile = storeFileObj
        storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "android"
        keyAlias = System.getenv("KEY_ALIAS") ?: "androiddebugkey"
        keyPassword = System.getenv("KEY_PASSWORD") ?: "android"
      } else {
        val debugKeystore = listOf(
          file("${System.getProperty("user.home")}/.android/debug.keystore"),
          rootProject.file("debug.keystore"),
          file("debug.keystore")
        ).firstOrNull { it.exists() } ?: file("${System.getProperty("user.home")}/.android/debug.keystore")
        storeFile = debugKeystore
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
      }
    }
  }
  buildTypes {
    debug {
      signingConfig = signingConfigs.getByName("debug")
    }
    release {
      isMinifyEnabled = false
      isShrinkResources = false
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro"
      )
      signingConfig = signingConfigs.getByName("release")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
    viewBinding = true
  }
  packaging {
    jniLibs {
      useLegacyPackaging = true
    }
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
}
androidComponents {
  beforeVariants { variantBuilder ->
  }
}
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}
dependencies {
  implementation(libs.yandex.authsdk)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.viewpager2)
  implementation(libs.androidx.appcompat)
  implementation("com.google.android.material:material:1.11.0")
  implementation(libs.androidx.fragment.ktx)
  implementation(libs.androidx.swiperefreshlayout)
  implementation("com.facebook.shimmer:shimmer:0.5.0")
  implementation(libs.androidx.palette.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  implementation(platform(libs.firebase.bom))
  implementation(libs.firebase.firestore)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}

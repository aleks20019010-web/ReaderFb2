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
    versionCode = 5
    versionName = "2.3.2"

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
      // Path to release keystore (used in CI)
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "release.keystore"
      println("--- SIGNING CONFIG DEBUG ---")
      println("System.getenv(\"KEYSTORE_PATH\") = ${System.getenv("KEYSTORE_PATH")}")
      println("System.getenv(\"KEYSTORE_PASSWORD\") is empty = ${System.getenv("KEYSTORE_PASSWORD").isNullOrEmpty()}")
      println("System.getenv(\"KEY_ALIAS\") = ${System.getenv("KEY_ALIAS")}")
      
      val storeFileObj = if (file(keystorePath).isAbsolute) {
        file(keystorePath)
      } else {
        rootProject.file(keystorePath)
      }
      println("Resolved storeFileObj path = ${storeFileObj.absolutePath}")
      println("Resolved storeFileObj exists = ${storeFileObj.exists()}")
      
      // Let's also list files in root directory to see if release.keystore is there
      try {
        val rootFiles = rootProject.projectDir.listFiles()?.map { it.name } ?: emptyList()
        println("Root project files: $rootFiles")
      } catch (e: Exception) {
        println("Error listing root project files: ${e.message}")
      }

      if (storeFileObj.exists()) {
        storeFile = storeFileObj
        storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "android"
        keyAlias = System.getenv("KEY_ALIAS") ?: "androiddebugkey"
        keyPassword = System.getenv("KEY_PASSWORD") ?: "android"
      } else {
        // Fallback to debug signature for safety in environments without secrets
        val debugKeystore = listOf(
          file("${System.getProperty("user.home")}/.android/debug.keystore"),
          rootProject.file("debug.keystore"),
          file("debug.keystore")
        ).firstOrNull { it.exists() } ?: file("${System.getProperty("user.home")}/.android/debug.keystore")
        println("Fallback debugKeystore path = ${debugKeystore.absolutePath}")
        println("Fallback debugKeystore exists = ${debugKeystore.exists()}")
        storeFile = debugKeystore
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
      }
      println("--- END SIGNING CONFIG DEBUG ---")
    }
  }

  buildTypes {
    debug {
      signingConfig = signingConfigs.getByName("debug")
    }
    release {
      // Сжатие и обфускация включены (оптимизируют сторонние библиотеки).
      // Все классы приложения защищены правилами в proguard-rules.pro.
      // Если вам нужно ПОЛНОСТЬЮ отключить сжатие и обфускацию для проверки:
      // установите: isMinifyEnabled = false, isShrinkResources = false
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
    if (variantBuilder.buildType == "release") {
      variantBuilder.enable = false
    }
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}


// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(libs.yandex.authsdk)
  implementation(platform(libs.androidx.compose.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
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
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  // implementation(libs.androidx.navigation.compose)
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
  // implementation(libs.play.services.location)
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

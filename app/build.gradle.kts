import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.google.services)
  alias(libs.plugins.roborazzi)
}


// Resolves an API key for BuildConfig: process env first, then KEY=VALUE lines
// from .env / .env.example, else the placeholder (SecureApiKeyManager ignores
// YOUR_* values, so unconfigured builds simply disable the feature).
fun envValue(lines: List<String>, name: String, placeholder: String): String {
  val fromEnv = System.getenv(name)
  if (fromEnv != null) return fromEnv
  for (line in lines) {
    val trimmed = line.trim()
    if (trimmed.startsWith("$name=")) {
      return trimmed.substring(name.length + 1).trim().removeSurrounding("\"")
    }
  }
  return placeholder
}

android {
  namespace = "com.example"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.devcode.forex"
    minSdk = 24
    targetSdk = 36
    // CI can bump the version without a code change (e.g. VERSION_CODE=<run number>);
    // local builds keep the 1 / "1.0" defaults. Google Play requires a strictly
    // increasing versionCode per upload.
    versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
    versionName = System.getenv("VERSION_NAME") ?: "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    // Third-party API keys as BuildConfig fields. Previously these were injected
    // by the (removed) secrets-gradle-plugin from .env; declared explicitly now,
    // with the same resolution order: process env -> .env file -> placeholder.
    // NOTE: anything baked into BuildConfig is extractable from a shipped APK —
    // these are build-time conveniences; SecureApiKeyManager user keys win at runtime.
    val envFile = listOf(rootProject.file(".env"), rootProject.file(".env.example")).firstOrNull { it.exists() }
    val envLines: List<String> = if (envFile != null) envFile.readLines() else emptyList()
    buildConfigField("String", "FCS_API_KEY", "\"${envValue(envLines, "FCS_API_KEY", "YOUR_FCS_API_KEY")}\"")
    buildConfigField("String", "OPENROUTER_API_KEY", "\"${envValue(envLines, "OPENROUTER_API_KEY", "YOUR_OPENROUTER_API_KEY")}\"")
    buildConfigField("String", "ALPHAVANTAGE_API_KEY", "\"${envValue(envLines, "ALPHAVANTAGE_API_KEY", "YOUR_ALPHAVANTAGE_API_KEY")}\"")
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = System.getenv("KEY_ALIAS") ?: "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    // No custom debug signing config: AGP's built-in debug keystore is
    // auto-generated, so a fresh clone can always build debug variants.
  }

  // Release signing is applied only when a real keystore + passwords are present
  // (CI release jobs). Otherwise release builds fall back to the debug key so
  // `assembleRelease` still works locally — such builds are NOT uploadable to Play.
  val releaseSigningAvailable = System.getenv("STORE_PASSWORD") != null &&
    file(System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks").exists()

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = if (releaseSigningAvailable) {
        signingConfigs.getByName("release")
      } else {
        signingConfigs.getByName("debug")
      }
    }
    // debug: default AGP debug signing (auto-generated ~/.android/debug.keystore)
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

// API keys reach the app via the BuildConfig fields declared below (defaults are
// placeholders). The mapsplatform secrets-gradle-plugin previously injected .env
// values at build time; it is not applied on Gradle 9 — export real values by
// editing the buildConfigField defaults locally or via your CI secret manager.

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
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
  implementation(libs.androidx.work.runtime.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  // implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  // NOTE: firebase-ai, navigation-compose and firebase-appcheck were removed as
  // unused (no references in the codebase) — re-add with the feature that needs them.

  // Firebase Auth + Google Sign-In via Credential Manager:
  implementation(libs.firebase.auth)
  implementation(libs.androidx.security.crypto)
  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services)
  implementation(libs.googleid)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  // Task<T>.await() for Firebase Auth / Play services Tasks (ForexAuthManager)
  implementation(libs.kotlinx.coroutines.playservices)
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
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}

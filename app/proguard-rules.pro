# Forex Chart Analyzer — release (R8/ProGuard) rules.
# Minify + shrinkResources are enabled for release builds
# (app/build.gradle.kts); these keeps cover the reflection-heavy stack:
# Moshi (kotlin-reflect), Retrofit, Room and Firebase.

# Keep line numbers for readable crash stacks.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes *Annotation*, Signature, Exceptions, InnerClasses, EnclosingMethod

# --- Moshi + kotlin-reflect (KotlinJsonAdapterFactory) ----------------------
-keep class com.squareup.moshi.** { *; }
-keep interface com.squareup.moshi.** { *; }
-dontwarn com.squareup.moshi.**
# App data models (de)serialised reflectively by Moshi / passed across layers:
-keep class com.example.forex.data.repository.Fcs** { *; }
-keep class com.example.forex.data.repository.OpenRouter** { *; }
-keep class com.example.forex.data.remote.AlphaVantageErrorPayload { *; }
-keep class com.example.forex.data.model.** { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}

# --- Retrofit ----------------------------------------------------------------
-keep interface com.example.forex.data.repository.FcsApi { *; }
-keep interface com.example.forex.data.repository.OpenRouterApi { *; }
-keep interface com.example.forex.data.remote.AlphaVantageApi { *; }
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-dontwarn retrofit2.**
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn org.jetbrains.annotations.**

# --- OkHttp ------------------------------------------------------------------
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# --- Room --------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class com.example.forex.data.db.** { *; }
-dontwarn androidx.room.paging.**

# --- Firebase / Play services / Credential Manager ---------------------------
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
-keep class androidx.credentials.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# --- Kotlin coroutines ---------------------------------------------------------
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# --- App entry points ----------------------------------------------------------
-keep class com.example.MainActivity { *; }
-keep class com.example.forex.worker.SignalWorker { *; }

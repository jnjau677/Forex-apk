# Release shrink rules for the Forex Chart Analyzer.
# Applies when isMinifyEnabled/isShrinkResources are enabled (app/build.gradle.kts).

# Keep readable stack traces for crash triage.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- Retrofit ---
-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation,allowshrinking class <3>
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit

# --- Moshi ---
# Market data models and provider DTOs are deserialized via KotlinJsonAdapterFactory
# (reflection), so their shape and metadata must survive shrinking.
-keep class com.example.forex.data.model.** { *; }
-keep class com.example.forex.data.repository.FcsInfo { *; }
-keep class com.example.forex.data.repository.FcsPriceInfo { *; }
-keep class com.example.forex.data.repository.FcsLatestResponse { *; }
-keep class com.example.forex.data.repository.FcsHistoryResponse { *; }
-keep class com.example.forex.data.repository.FcsHistoryCandle { *; }
-keep class com.example.forex.data.repository.OpenRouterRequest { *; }
-keep class com.example.forex.data.repository.OpenRouterMessage { *; }
-keep class com.example.forex.data.repository.OpenRouterResponse { *; }
-keep class com.example.forex.data.repository.OpenRouterChoice { *; }
-keep class com.example.forex.data.remote.AlphaVantageErrorPayload { *; }

# --- OkHttp / third-party ---
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Firebase / Play services libraries ship their own consumer rules; silence remaining
# optional-dependency warnings from their internals.
-dontwarn com.google.android.gms.**

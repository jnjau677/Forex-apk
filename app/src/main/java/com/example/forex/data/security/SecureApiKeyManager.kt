package com.example.forex.data.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.BuildConfig

/**
 * Single read path for third-party API keys (F-05): user-supplied keys saved here
 * (Android Keystore + EncryptedSharedPreferences) take precedence over the
 * build-time `BuildConfig` values.
 *
 * Caveat that no client-side storage can fix: a key baked into `BuildConfig` at
 * build time is extractable from a distributed APK. For production distribution,
 * prefer user-supplied keys (saved via the `save…` methods) or a backend proxy —
 * treat bundled keys as low-value, quota-limited development keys.
 */
class SecureApiKeyManager(private val context: Context) {

    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    // EncryptedSharedPreferences.create() is declared to return SharedPreferences
    // (the encrypted implementation); typing the delegate as the subtype does not compile.
    private val encryptedPreferences: android.content.SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            context,
            SECURE_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * A stored/bundled value counts as a real key only when it is non-blank and
     * not a placeholder. Placeholders in this project look like
     * `YOUR_FCS_API_KEY` / `YOUR_OPENROUTER_API_KEY` (from .env.example), so any
     * `YOUR_…` value is rejected — the previous exact-match check against a
     * single placeholder string never matched and let placeholders through.
     */
    private fun usable(value: String?): String? =
        value?.trim()?.takeIf { it.isNotEmpty() && !it.startsWith("YOUR_", ignoreCase = true) && it != PLACEHOLDER_KEY }

    private fun resolve(prefsKey: String, buildConfigValue: String, save: (String) -> Unit): String? {
        usable(encryptedPreferences.getString(prefsKey, null))?.let { return it }
        usable(buildConfigValue)?.let { buildKey ->
            // Cache the bundled key in encrypted storage so a user-supplied key
            // saved later cleanly overrides it in one place.
            save(buildKey)
            return buildKey
        }
        return null
    }

    /** FCS API key from encrypted storage, falling back to the bundled BuildConfig value. */
    fun getFcsApiKey(): String? = resolve(KEY_FCS_API, BuildConfig.FCS_API_KEY, ::saveFcsApiKey)

    /** OpenRouter API key from encrypted storage, falling back to the bundled BuildConfig value. */
    fun getOpenRouterApiKey(): String? = resolve(KEY_OPENROUTER_API, BuildConfig.OPENROUTER_API_KEY, ::saveOpenRouterApiKey)

    /** Alpha Vantage API key from encrypted storage, falling back to the bundled BuildConfig value. */
    fun getAlphaVantageApiKey(): String? = resolve(KEY_ALPHAVANTAGE_API, BuildConfig.ALPHAVANTAGE_API_KEY, ::saveAlphaVantageApiKey)

    /**
     * Retrieves the configured AI model name, with sensible default.
     */
    fun getAiModel(): String {
        val stored = encryptedPreferences.getString(KEY_AI_MODEL, null)
        return if (!stored.isNullOrBlank()) {
            stored
        } else {
            DEFAULT_AI_MODEL
        }
    }

    /** Securely saves FCS API Key to encrypted SharedPreferences. */
    fun saveFcsApiKey(apiKey: String) {
        usable(apiKey)?.let { encryptedPreferences.edit().putString(KEY_FCS_API, it).apply() }
    }

    /** Securely saves OpenRouter API Key to encrypted SharedPreferences. */
    fun saveOpenRouterApiKey(apiKey: String) {
        usable(apiKey)?.let { encryptedPreferences.edit().putString(KEY_OPENROUTER_API, it).apply() }
    }

    /** Securely saves Alpha Vantage API Key to encrypted SharedPreferences. */
    fun saveAlphaVantageApiKey(apiKey: String) {
        usable(apiKey)?.let { encryptedPreferences.edit().putString(KEY_ALPHAVANTAGE_API, it).apply() }
    }

    /**
     * Securely saves AI Model preference.
     */
    fun saveAiModel(model: String) {
        if (model.isNotBlank()) {
            encryptedPreferences.edit().putString(KEY_AI_MODEL, model).apply()
        }
    }

    /** Checks if FCS API is properly configured (real key, not a placeholder). */
    fun isFcsApiConfigured(): Boolean = getFcsApiKey() != null

    /** Checks if OpenRouter API is properly configured (real key, not a placeholder). */
    fun isOpenRouterApiConfigured(): Boolean = getOpenRouterApiKey() != null

    /** Checks if Alpha Vantage is properly configured (real key, not a placeholder). */
    fun isAlphaVantageConfigured(): Boolean = getAlphaVantageApiKey() != null

    /**
     * Clears all stored API keys (for logout or account switching).
     */
    fun clearAllKeys() {
        encryptedPreferences.edit().clear().apply()
    }

    /**
     * Clears FCS API key only.
     */
    fun clearFcsApiKey() {
        encryptedPreferences.edit().remove(KEY_FCS_API).apply()
    }

    /**
     * Clears OpenRouter API key only.
     */
    fun clearOpenRouterApiKey() {
        encryptedPreferences.edit().remove(KEY_OPENROUTER_API).apply()
    }

    /** Clears Alpha Vantage API key only. */
    fun clearAlphaVantageApiKey() {
        encryptedPreferences.edit().remove(KEY_ALPHAVANTAGE_API).apply()
    }

    companion object {
        private const val SECURE_PREFS_NAME = "forex_secure_prefs"
        private const val KEY_FCS_API = "fcs_api_key"
        private const val KEY_OPENROUTER_API = "openrouter_api_key"
        private const val KEY_ALPHAVANTAGE_API = "alphavantage_api_key"
        private const val KEY_AI_MODEL = "ai_model"
        private const val PLACEHOLDER_KEY = "YOUR_API_KEY_HERE"
        private const val DEFAULT_AI_MODEL = "google/gemini-2.5-flash"

        @Volatile
        private var instance: SecureApiKeyManager? = null

        fun getInstance(context: Context): SecureApiKeyManager {
            return instance ?: synchronized(this) {
                SecureApiKeyManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}

package com.example.forex.data.auth

import android.content.Context
import android.util.Log
import android.util.Patterns
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.example.forex.data.model.UserProfile
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * The app's single authentication path: **Firebase Authentication**.
 *
 * Supported providers: Email/Password and Google (via Credential Manager).
 *
 * Design rules (deliberately strict — see REVIEW.md F-04):
 *  - No local credential store. Passwords are only ever sent to Firebase;
 *    no password, hash or user record is persisted in Room or SharedPreferences.
 *  - No fallback accounts. If Firebase is unreachable or unconfigured, sign-in
 *    fails with an actionable error instead of silently creating a local user.
 *  - No dummy Firebase initialisation. Firebase is initialised by the
 *    google-services plugin from `app/google-services.json`; when that file is
 *    absent [FirebaseAuth.getInstance] throws, we treat the app as
 *    "auth not configured" ([isConfigured] == false) and say so in the UI.
 *  - The signed-in user is derived exclusively from [FirebaseAuth]'s auth
 *    state, so it survives process death exactly as Firebase persists it.
 */
class ForexAuthManager(private val appContext: Context) {

    private val firebaseAuth: FirebaseAuth? by lazy {
        try {
            FirebaseAuth.getInstance()
        } catch (e: Exception) {
            // Default FirebaseApp not initialised: google-services.json missing
            // or the google-services Gradle plugin did not run.
            Log.w(TAG, "Firebase Auth unavailable: ${e.message}")
            null
        }
    }

    /** True when a real Firebase project backs this build (google-services.json present). */
    val isConfigured: Boolean get() = firebaseAuth != null

    /** The currently signed-in Firebase user, or null. Mirrors Firebase's own session persistence. */
    val currentFirebaseUser: FirebaseUser? get() = firebaseAuth?.currentUser

    /**
     * Auth state as a cold [Flow] backed by Firebase's [FirebaseAuth.AuthStateListener].
     * Emits the current user immediately on collection, then on every sign-in/out.
     */
    val currentUser: Flow<UserProfile?> = callbackFlow {
        val auth = firebaseAuth
        if (auth == null) {
            trySend(null)
            close()
            return@callbackFlow
        }
        val listener = FirebaseAuth.AuthStateListener { firebase ->
            trySend(firebase.currentUser?.toUserProfile())
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    // ---------------------------------------------------------------------
    // Email / Password
    // ---------------------------------------------------------------------

    suspend fun signUpWithEmail(email: String, password: String, displayName: String): AuthResult {
        val auth = firebaseAuth ?: return AuthResult.failure(NOT_CONFIGURED_MESSAGE)
        val cleanEmail = email.trim().lowercase()
        val cleanName = displayName.trim()
            .ifBlank { cleanEmail.substringBefore("@").replaceFirstChar { it.uppercase() } }

        validateEmail(cleanEmail)?.let { return AuthResult.failure(it) }
        if (password.length < 6) {
            return AuthResult.failure("Password must be at least 6 characters.")
        }

        return try {
            val result = auth.createUserWithEmailAndPassword(cleanEmail, password).await()
            val user = result.user ?: return AuthResult.failure("Account creation failed. Please try again.")
            try {
                user.updateProfile(
                    UserProfileChangeRequest.Builder().setDisplayName(cleanName).build()
                ).await()
            } catch (e: Exception) {
                Log.w(TAG, "Display name update failed (non-fatal): ${e.message}")
            }
            // Best effort: verification mail confirms ownership of the address.
            try {
                user.sendEmailVerification().await()
            } catch (e: Exception) {
                Log.w(TAG, "Email verification send failed (non-fatal): ${e.message}")
            }
            AuthResult.success(user.toUserProfile().copy(displayName = cleanName))
        } catch (e: Exception) {
            Log.w(TAG, "Sign-up failed: ${e.message}")
            AuthResult.failure(friendlyAuthError(e))
        }
    }

    suspend fun signInWithEmail(email: String, password: String): AuthResult {
        val auth = firebaseAuth ?: return AuthResult.failure(NOT_CONFIGURED_MESSAGE)
        val cleanEmail = email.trim().lowercase()

        validateEmail(cleanEmail)?.let { return AuthResult.failure(it) }
        if (password.isBlank()) {
            return AuthResult.failure("Please enter your password.")
        }

        return try {
            val result = auth.signInWithEmailAndPassword(cleanEmail, password).await()
            val user = result.user ?: return AuthResult.failure("Sign-in failed. Please try again.")
            AuthResult.success(user.toUserProfile())
        } catch (e: Exception) {
            Log.w(TAG, "Sign-in failed: ${e.message}")
            AuthResult.failure(friendlyAuthError(e))
        }
    }

    /** Sends a Firebase password-reset email. Used by the "Forgot password?" action. */
    suspend fun sendPasswordResetEmail(email: String): AuthResult {
        val auth = firebaseAuth ?: return AuthResult.failure(NOT_CONFIGURED_MESSAGE)
        val cleanEmail = email.trim().lowercase()
        validateEmail(cleanEmail)?.let { return AuthResult.failure(it) }

        return try {
            auth.sendPasswordResetEmail(cleanEmail).await()
            AuthResult.success(null)
        } catch (e: Exception) {
            Log.w(TAG, "Password reset failed: ${e.message}")
            AuthResult.failure(friendlyAuthError(e))
        }
    }

    // ---------------------------------------------------------------------
    // Google (Credential Manager -> Firebase)
    // ---------------------------------------------------------------------

    /**
     * Google Sign-In.
     *
     * [activityContext] must be an Activity context: Credential Manager needs
     * an Activity to render the credential picker, so the application context
     * is not sufficient here. The Web client ID is read from
     * `R.string.default_web_client_id`, which the google-services plugin only
     * generates when `google-services.json` (with an OAuth web client) exists —
     * hence the reflective lookup: this code must still compile in builds where
     * Firebase has not been set up yet, and then fail with a clear message.
     */
    suspend fun signInWithGoogle(activityContext: Context): AuthResult {
        val auth = firebaseAuth ?: return AuthResult.failure(NOT_CONFIGURED_MESSAGE)

        val webClientId = resolveWebClientId(activityContext)
            ?: return AuthResult.failure(
                "Google Sign-In is not configured for this build. Add app/google-services.json " +
                    "and enable the Google provider in the Firebase console."
            )

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(webClientId)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            val response = CredentialManager.create(activityContext)
                .getCredential(activityContext, request)
            val credential = response.credential

            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val firebaseCredential = GoogleAuthProvider.getCredential(googleCredential.idToken, null)
                val result = auth.signInWithCredential(firebaseCredential).await()
                val user = result.user ?: return AuthResult.failure("Google Sign-In failed. Please try again.")
                AuthResult.success(user.toUserProfile())
            } else {
                AuthResult.failure("Invalid Google credential received.")
            }
        } catch (e: GetCredentialException) {
            // Subtype by class name (Cancellation / NoCredential are subclasses whose
            // exact availability varies across credentials-ktx versions).
            Log.w(TAG, "Credential Manager failed (${e.javaClass.simpleName}): ${e.message}")
            AuthResult.failure(
                when {
                    e.javaClass.simpleName.contains("Cancellation") ->
                        "Google Sign-In was cancelled."
                    e.javaClass.simpleName.contains("NoCredential") ->
                        "No Google account is available on this device. Add one in device Settings, then try again."
                    else -> e.message ?: "Google Sign-In failed."
                }
            )
        } catch (e: Exception) {
            Log.w(TAG, "Google Sign-In failed: ${e.message}")
            AuthResult.failure(friendlyAuthError(e))
        }
    }

    // ---------------------------------------------------------------------
    // Session
    // ---------------------------------------------------------------------

    /** Signs out of Firebase and clears Credential Manager state so the next sign-in shows the account picker. */
    suspend fun signOut() {
        try {
            firebaseAuth?.signOut()
        } catch (e: Exception) {
            Log.w(TAG, "Firebase sign-out failed: ${e.message}")
        }
        try {
            CredentialManager.create(appContext).clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            // Non-fatal: Firebase sign-out above already ended the app session.
            Log.w(TAG, "Clearing credential state failed: ${e.message}")
        }
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private fun resolveWebClientId(context: Context): String? {
        val resId = context.resources.getIdentifier(
            "default_web_client_id", "string", context.packageName
        )
        if (resId == 0) return null
        return context.getString(resId).takeIf { it.isNotBlank() && !it.startsWith("YOUR_") }
    }

    private fun validateEmail(email: String): String? = when {
        email.isBlank() -> "Please enter your email address."
        !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> "Please enter a valid email address."
        else -> null
    }

    companion object {
        private const val TAG = "ForexAuthManager"

        const val NOT_CONFIGURED_MESSAGE =
            "Firebase is not configured for this build. Add app/google-services.json " +
                "(Firebase console → Project settings → Your apps) and rebuild. See README → Firebase Authentication."

        /** Maps Firebase failures to messages a trader can act on. Never leaks credential detail. */
        internal fun friendlyAuthError(e: Exception): String = when (e) {
            is FirebaseAuthWeakPasswordException ->
                "That password is too weak. Use at least 6 characters, mixing letters and numbers."
            is FirebaseAuthUserCollisionException ->
                "An account with this email already exists. Try logging in instead."
            is FirebaseAuthInvalidUserException ->
                "No account found with this email, or the account has been disabled. Please sign up first."
            is FirebaseAuthInvalidCredentialsException ->
                "Incorrect email or password. Please check both and try again."
            is FirebaseNetworkException ->
                "Network error. Check your connection and try again."
            is FirebaseTooManyRequestsException ->
                "Too many sign-in attempts. Please wait a few minutes and try again."
            is FirebaseAuthException -> when (e.errorCode) {
                "ERROR_INVALID_EMAIL" -> "Please enter a valid email address."
                "ERROR_USER_NOT_FOUND" -> "No account found with this email. Please sign up first."
                "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL" ->
                    "Incorrect email or password. Please check both and try again."
                "ERROR_EMAIL_ALREADY_IN_USE" -> "An account with this email already exists. Try logging in instead."
                "ERROR_REQUIRES_RECENT_LOGIN" -> "Please log in again to complete this action."
                "ERROR_OPERATION_NOT_ALLOWED" ->
                    "This sign-in method is disabled. Enable it in the Firebase console → Authentication → Sign-in method."
                else -> e.message ?: "Authentication failed. Please try again."
            }
            else -> e.message ?: "Authentication failed. Please try again."
        }
    }
}

/** Firebase -> UI profile. `joinedAt` comes from Firebase account metadata, never a local row. */
private fun FirebaseUser.toUserProfile(): UserProfile {
    val provider = providerData
        .map { it.providerId }
        .firstOrNull { it != "firebase" }
        ?: providerData.firstOrNull()?.providerId
    return UserProfile(
        uid = uid,
        email = email.orEmpty(),
        displayName = displayName?.takeIf { it.isNotBlank() }
            ?: email?.substringBefore("@")?.replaceFirstChar { it.uppercase() }
            ?: "Trader",
        photoUrl = photoUrl?.toString(),
        isAnonymous = isAnonymous,
        joinedAt = metadata?.creationTimestamp ?: System.currentTimeMillis(),
        isEmailVerified = isEmailVerified,
        authProvider = provider
    )
}

/** Result of an auth operation. On success [user] is set (except for password reset). */
data class AuthResult(
    val success: Boolean,
    val user: UserProfile? = null,
    val errorMessage: String? = null
) {
    companion object {
        fun success(user: UserProfile?) = AuthResult(success = true, user = user)
        fun failure(message: String) = AuthResult(success = false, errorMessage = message)
    }
}

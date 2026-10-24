package com.example.forex.data.auth

import android.content.Context
import android.util.Log
import android.util.Patterns
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.example.forex.data.db.ForexDao
import com.example.forex.data.db.UserProfileEntity
import com.example.forex.data.model.UserProfile
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

// Password hashing parameters (salted PBKDF2-HMAC-SHA256, standard JCA implementation).
private const val PBKDF2_PREFIX = "pbkdf2_sha256:"
private const val PBKDF2_ITERATIONS = 120_000
private const val PBKDF2_KEY_BITS = 256
private const val PBKDF2_SALT_BYTES = 16

class ForexAuthManager(
    private val context: Context,
    private val forexDao: ForexDao
) {
    private val auth: FirebaseAuth? by lazy {
        try {
            FirebaseAuth.getInstance()
        } catch (e: IllegalStateException) {
            try {
                FirebaseApp.initializeApp(context)
                FirebaseAuth.getInstance()
            } catch (e2: Exception) {
                try {
                    val options = FirebaseOptions.Builder()
                        .setApplicationId("1:1234567890:android:abcdef1234567890")
                        .setApiKey("dummy_api_key_for_firebase")
                        .setProjectId("dummy-project")
                        .build()
                    FirebaseApp.initializeApp(context, options)
                    FirebaseAuth.getInstance()
                } catch (e3: Exception) {
                    null
                }
            }
        }
    }

    private val credentialManager = CredentialManager.create(context)

    val activeUserProfileFlow: Flow<UserProfile?> = forexDao.getActiveUserProfile().map { entity ->
        entity?.let {
            UserProfile(
                uid = it.uid,
                email = it.email,
                displayName = it.displayName,
                photoUrl = it.photoUrl,
                accountTier = it.accountTier,
                joinedAt = it.joinedAt
            )
        }
    }

    /**
     * Salted PBKDF2-HMAC-SHA256 password hash, stored as
     * "pbkdf2_sha256:<iterations>:<saltHex>:<hashHex>".
     *
     * Replaces the previous unsalted SHA-256 scheme. Legacy hashes remain verifiable via
     * [verifyPassword] and are transparently upgraded to PBKDF2 on the next successful login.
     */
    private fun hashPassword(password: String): String {
        val salt = ByteArray(PBKDF2_SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val hash = pbkdf2(password, salt, PBKDF2_ITERATIONS)
        return PBKDF2_PREFIX + PBKDF2_ITERATIONS + ":" + salt.toHex() + ":" + hash.toHex()
    }

    /**
     * Verifies [password] against a stored hash. Accepts the current PBKDF2 format and the
     * legacy unsalted SHA-256 format so pre-migration local accounts keep working.
     */
    private fun verifyPassword(password: String, stored: String): Boolean {
        if (stored.startsWith(PBKDF2_PREFIX)) {
            val parts = stored.split(":")
            if (parts.size != 4) return false
            val iterations = parts[1].toIntOrNull() ?: return false
            val salt = parts[2].hexToBytes() ?: return false
            val expected = parts[3]
            return constantTimeEquals(expected, pbkdf2(password, salt, iterations).toHex())
        }
        // Legacy unsalted SHA-256 account (pre-migration only).
        val legacy = MessageDigest.getInstance("SHA-256").digest(password.toByteArray()).toHex()
        return constantTimeEquals(stored, legacy)
    }

    private fun isLegacyHash(stored: String): Boolean = !stored.startsWith(PBKDF2_PREFIX)

    private fun pbkdf2(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, PBKDF2_KEY_BITS)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    /** Length-checked constant-time comparison; avoids leaking hash material via timing. */
    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
        return diff == 0
    }

    private fun isValidEmail(email: String): Boolean =
        email.isNotEmpty() && Patterns.EMAIL_ADDRESS.matcher(email).matches()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.hexToBytes(): ByteArray? {
        if (length % 2 != 0) return null
        return try {
            ByteArray(length / 2) { i -> substring(i * 2, i * 2 + 2).toInt(16).toByte() }
        } catch (_: NumberFormatException) {
            null
        }
    }

    suspend fun signUpWithEmail(email: String, password: String, displayName: String): AuthResultWrapper {
        val cleanEmail = email.trim().lowercase()
        val cleanName = displayName.trim().ifBlank { cleanEmail.substringBefore("@").replaceFirstChar { it.uppercase() } }

        if (!isValidEmail(cleanEmail)) {
            return AuthResultWrapper(success = false, errorMessage = "Please enter a valid email address.")
        }
        if (password.length < 6) {
            return AuthResultWrapper(success = false, errorMessage = "Password must be at least 6 characters.")
        }

        // Try Firebase first if initialized
        var firebaseUid: String? = null
        try {
            auth?.let { fbAuth ->
                val result = fbAuth.createUserWithEmailAndPassword(cleanEmail, password).await()
                result.user?.let { u ->
                    firebaseUid = u.uid
                    try {
                        val profileUpdates = UserProfileChangeRequest.Builder()
                            .setDisplayName(cleanName)
                            .build()
                        u.updateProfile(profileUpdates).await()
                    } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            Log.w("ForexAuthManager", "Firebase signup failed/skipped: ${e.message}")
            // If email is already in use in Firebase, return error
            if (e.message?.contains("email address is already in use", ignoreCase = true) == true) {
                return AuthResultWrapper(success = false, errorMessage = "An account with this email already exists.")
            }
        }

        // Check local DB
        val existingLocal = forexDao.getUserByEmail(cleanEmail)
        if (existingLocal != null && firebaseUid == null) {
            return AuthResultWrapper(success = false, errorMessage = "An account with this email already exists locally.")
        }

        val finalUid = firebaseUid ?: existingLocal?.uid ?: UUID.randomUUID().toString()
        forexDao.logoutAllUsers()

        val newUser = UserProfileEntity(
            uid = finalUid,
            email = cleanEmail,
            displayName = cleanName,
            passwordHash = hashPassword(password),
            accountTier = "Pro Trader",
            joinedAt = System.currentTimeMillis(),
            isLoggedIn = true
        )
        forexDao.insertOrUpdateUser(newUser)

        return AuthResultWrapper(
            success = true,
            user = UserProfile(
                uid = newUser.uid,
                email = newUser.email,
                displayName = newUser.displayName,
                accountTier = newUser.accountTier,
                joinedAt = newUser.joinedAt
            )
        )
    }

    suspend fun signInWithEmail(email: String, password: String): AuthResultWrapper {
        val cleanEmail = email.trim().lowercase()
        if (!isValidEmail(cleanEmail)) {
            return AuthResultWrapper(success = false, errorMessage = "Please enter a valid email address.")
        }
        if (password.isBlank()) {
            return AuthResultWrapper(success = false, errorMessage = "Please enter your password.")
        }

        // Try Firebase first
        var firebaseSuccess = false
        var fbUser: FirebaseUser? = null
        try {
            auth?.let { fbAuth ->
                val result = fbAuth.signInWithEmailAndPassword(cleanEmail, password).await()
                fbUser = result.user
                if (fbUser != null) {
                    firebaseSuccess = true
                }
            }
        } catch (e: Exception) {
            Log.w("ForexAuthManager", "Firebase signIn failed/skipped: ${e.message}")
        }

        if (firebaseSuccess && fbUser != null) {
            val u = fbUser!!
            forexDao.logoutAllUsers()
            val userEntity = UserProfileEntity(
                uid = u.uid,
                email = u.email ?: cleanEmail,
                displayName = u.displayName ?: cleanEmail.substringBefore("@"),
                photoUrl = u.photoUrl?.toString(),
                passwordHash = hashPassword(password),
                accountTier = "Pro Trader",
                isLoggedIn = true
            )
            forexDao.insertOrUpdateUser(userEntity)
            return AuthResultWrapper(
                success = true,
                user = UserProfile(
                    uid = userEntity.uid,
                    email = userEntity.email,
                    displayName = userEntity.displayName,
                    photoUrl = userEntity.photoUrl,
                    accountTier = userEntity.accountTier,
                    joinedAt = userEntity.joinedAt
                )
            )
        }

        // Fallback to local secure user store (offline / Firebase not configured).
        // SECURITY: an account without a stored password hash can NEVER authenticate
        // here; previously a null hash matched any input password.
        val localUser = forexDao.getUserByEmail(cleanEmail)
        if (localUser != null) {
            val storedHash = localUser.passwordHash
            if (storedHash.isNullOrBlank()) {
                return AuthResultWrapper(
                    success = false,
                    errorMessage = "This account has no local password set. Please reset your password or sign in with Google."
                )
            }
            if (!verifyPassword(password, storedHash)) {
                return AuthResultWrapper(success = false, errorMessage = "Incorrect password. Please try again.")
            }
            // Transparent credential upgrade: re-hash legacy SHA-256 accounts with PBKDF2.
            val upgradedHash = if (isLegacyHash(storedHash)) hashPassword(password) else storedHash
            forexDao.logoutAllUsers()
            forexDao.insertOrUpdateUser(localUser.copy(passwordHash = upgradedHash, isLoggedIn = true))
            forexDao.setActiveUser(localUser.uid)
            return AuthResultWrapper(
                success = true,
                user = UserProfile(
                    uid = localUser.uid,
                    email = localUser.email,
                    displayName = localUser.displayName,
                    photoUrl = localUser.photoUrl,
                    accountTier = localUser.accountTier,
                    joinedAt = localUser.joinedAt
                )
            )
        }

        return AuthResultWrapper(
            success = false,
            errorMessage = "No account found with this email. Please sign up."
        )
    }

    suspend fun signInWithGoogle(): AuthResultWrapper {
        try {
            if (auth == null) {
                return AuthResultWrapper(success = false, errorMessage = "Authentication service unavailable.")
            }

            val webClientId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
                .let { if (it != 0) context.getString(it) else "YOUR_WEB_CLIENT_ID" }

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(context, request)
            val credential = result.credential

            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleIdTokenCredential.idToken
                
                val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
                val authResult = auth!!.signInWithCredential(firebaseCredential).await()
                val user = authResult.user

                if (user != null) {
                    forexDao.logoutAllUsers()
                    val profileEntity = UserProfileEntity(
                        uid = user.uid,
                        email = user.email ?: "trader@forex.ai",
                        displayName = user.displayName ?: "Trader",
                        photoUrl = user.photoUrl?.toString(),
                        accountTier = "Pro Trader",
                        isLoggedIn = true
                    )
                    forexDao.insertOrUpdateUser(profileEntity)

                    return AuthResultWrapper(
                        success = true,
                        user = UserProfile(
                            uid = profileEntity.uid,
                            email = profileEntity.email,
                            displayName = profileEntity.displayName,
                            photoUrl = profileEntity.photoUrl,
                            accountTier = profileEntity.accountTier,
                            joinedAt = profileEntity.joinedAt
                        )
                    )
                }
            }
            return AuthResultWrapper(success = false, errorMessage = "Invalid Google credential received.")
        } catch (e: GetCredentialException) {
            Log.e("ForexAuthManager", "Google Credential Exception: ${e.message}")
            return AuthResultWrapper(success = false, errorMessage = e.message ?: "Google Sign-In was cancelled.")
        } catch (e: Exception) {
            Log.e("ForexAuthManager", "Google Sign-in Exception: ${e.message}")
            return AuthResultWrapper(success = false, errorMessage = e.message ?: "Failed to sign in with Google.")
        }
    }

    suspend fun signOut() {
        try {
            auth?.signOut()
        } catch (e: Exception) {
            Log.w("ForexAuthManager", "Error signing out from Firebase: ${e.message}")
        }
        forexDao.logoutAllUsers()
    }
}

data class AuthResultWrapper(
    val success: Boolean,
    val user: UserProfile? = null,
    val errorMessage: String? = null
)

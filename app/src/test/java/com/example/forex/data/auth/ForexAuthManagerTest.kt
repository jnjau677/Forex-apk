package com.example.forex.data.auth

import com.example.forex.data.db.UserProfileEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

/**
 * Deterministic unit tests for the local password credential logic:
 * PBKDF2-HMAC-SHA256 hashing, legacy SHA-256 migration and the local-login
 * decision (no Room or Firebase involved — pure functions).
 */
class ForexAuthManagerTest {

    // --------------------------------------------------------------
    // PBKDF2 password hashing
    // --------------------------------------------------------------

    @Test
    fun `hash is salted so identical passwords hash differently`() {
        val first = ForexAuthManager.hashPassword("CorrectHorse42")
        val second = ForexAuthManager.hashPassword("CorrectHorse42")
        assertNotEquals(first, second)
        assertTrue(first.startsWith("pbkdf2_sha256:"))
        assertEquals(4, first.split(":").size)
    }

    @Test
    fun `pbkdf2 hash roundtrip verifies and rejects wrong password`() {
        val hash = ForexAuthManager.hashPassword("CorrectHorse42")
        assertTrue(ForexAuthManager.verifyPassword("CorrectHorse42", hash))
        assertFalse(ForexAuthManager.verifyPassword("wrong-password", hash))
    }

    @Test
    fun `legacy unsalted sha256 hash still verifies and is flagged legacy`() {
        val legacy = legacySha256("oldpw")
        assertTrue(ForexAuthManager.isLegacyHash(legacy))
        assertTrue(ForexAuthManager.verifyPassword("oldpw", legacy))
        assertFalse(ForexAuthManager.verifyPassword("otherpw", legacy))
    }

    @Test
    fun `malformed stored hashes are rejected instead of crashing`() {
        assertFalse(ForexAuthManager.verifyPassword("x", "pbkdf2_sha256:notanumber:zz:ff"))
        assertFalse(ForexAuthManager.verifyPassword("x", "pbkdf2_sha256:120000:"))
        assertFalse(ForexAuthManager.verifyPassword("x", ""))
    }

    // --------------------------------------------------------------
    // Local login decisions
    // --------------------------------------------------------------

    @Test
    fun `account without password hash can never log in`() {
        val decision = ForexAuthManager.evaluateLocalLogin(user(null), "anything")
        assertFalse(decision.allow)
        assertNull(decision.upgradedHash)
    }

    @Test
    fun `blank password hash can never log in`() {
        val decision = ForexAuthManager.evaluateLocalLogin(user("  "), "anything")
        assertFalse(decision.allow)
    }

    @Test
    fun `wrong password is rejected`() {
        val hash = ForexAuthManager.hashPassword("CorrectHorse42")
        val decision = ForexAuthManager.evaluateLocalLogin(user(hash), "wrong")
        assertFalse(decision.allow)
    }

    @Test
    fun `legacy sha256 account upgrades to pbkdf2 on successful login`() {
        val decision = ForexAuthManager.evaluateLocalLogin(user(legacySha256("oldpw")), "oldpw")
        assertTrue(decision.allow)
        val upgraded = decision.upgradedHash
        assertTrue(upgraded != null && upgraded.startsWith("pbkdf2_sha256:"))
        assertTrue(ForexAuthManager.verifyPassword("oldpw", upgraded!!))
    }

    @Test
    fun `pbkdf2 account stays on its existing hash after login`() {
        val hash = ForexAuthManager.hashPassword("CorrectHorse42")
        val decision = ForexAuthManager.evaluateLocalLogin(user(hash), "CorrectHorse42")
        assertTrue(decision.allow)
        assertNull(decision.upgradedHash)
    }

    private fun user(hash: String?): UserProfileEntity = UserProfileEntity(
        uid = "uid-1",
        email = "trader@example.com",
        displayName = "Trader",
        photoUrl = null,
        passwordHash = hash
    )

    private fun legacySha256(password: String): String =
        MessageDigest.getInstance("SHA-256").digest(password.toByteArray())
            .joinToString("") { "%02x".format(it) }
}

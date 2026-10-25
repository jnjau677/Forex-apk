package com.example.forex.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Local (non-Firebase) behaviour of the Firebase auth layer: error mapping for
 * unexpected failures and the [AuthResult] contract the ViewModel relies on.
 * Firebase-specific exception mapping is verified on-device / in Firebase's own SDK.
 */
class ForexAuthManagerTest {

    @Test
    fun `unexpected errors surface their message without credential detail`() {
        assertEquals("boom", ForexAuthManager.friendlyAuthError(IllegalStateException("boom")))
    }

    @Test
    fun `unexpected errors without a message fall back to a generic prompt`() {
        assertEquals(
            "Authentication failed. Please try again.",
            ForexAuthManager.friendlyAuthError(RuntimeException())
        )
    }

    @Test
    fun `auth result contract distinguishes success and failure`() {
        val ok = AuthResult.success(null)
        assertTrue(ok.success)
        assertNull(ok.errorMessage)

        val bad = AuthResult.failure("nope")
        assertFalse(bad.success)
        assertEquals("nope", bad.errorMessage)
        assertNull(bad.user)
    }

    @Test
    fun `not-configured message points at google-services setup`() {
        assertTrue(ForexAuthManager.NOT_CONFIGURED_MESSAGE.contains("google-services.json"))
    }
}

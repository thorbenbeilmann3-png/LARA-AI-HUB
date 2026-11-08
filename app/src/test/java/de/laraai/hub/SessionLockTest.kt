package de.laraai.hub

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionLockTest {
    @After
    fun tearDown() = SessionVault.lock()

    @Test
    fun vaultStartsLockedAndEmpty() {
        SessionVault.lock()
        assertFalse(SessionVault.isUnlocked)
        assertNull(SessionVault.get("anthropic"))
    }

    @Test
    fun unlockMakesValuesAvailableAndLockWipesThem() {
        SessionVault.unlock(mapOf("anthropic" to "secret-1"))
        assertTrue(SessionVault.isUnlocked)
        assertEquals("secret-1", SessionVault.get("anthropic"))
        SessionVault.lock()
        assertFalse(SessionVault.isUnlocked)
        assertNull(SessionVault.get("anthropic"))
        assertEquals(0, SessionVault.size())
    }

    @Test
    fun unlockReplacesPreviousSession() {
        SessionVault.unlock(mapOf("a" to "1"))
        SessionVault.unlock(mapOf("b" to "2"))
        assertNull(SessionVault.get("a"))
        assertEquals("2", SessionVault.get("b"))
    }

    @Test
    fun noLockWithoutBackgroundPhase() {
        assertFalse(LockPolicy.shouldLockOnResume(null, 1_000_000))
    }

    @Test
    fun lockAfterTimeoutOnly() {
        val t = 10_000_000L
        assertFalse(LockPolicy.shouldLockOnResume(t, t + LockPolicy.BACKGROUND_TIMEOUT_MS - 1))
        assertTrue(LockPolicy.shouldLockOnResume(t, t + LockPolicy.BACKGROUND_TIMEOUT_MS))
    }

    @Test
    fun clockGoingBackwardsLocks() {
        assertTrue(LockPolicy.shouldLockOnResume(10_000L, 5_000L))
    }
}

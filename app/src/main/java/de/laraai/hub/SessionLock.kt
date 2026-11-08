package de.laraai.hub

/**
 * Decrypted credentials for the current unlocked session. Memory only: never
 * written to disk or logs. Cleared on [lock].
 */
internal object SessionVault {
    private val values = mutableMapOf<String, String>()

    @Volatile
    var isUnlocked: Boolean = false
        private set

    @Synchronized
    fun unlock(decrypted: Map<String, String>) {
        values.clear()
        values.putAll(decrypted)
        isUnlocked = true
    }

    @Synchronized
    fun lock() {
        values.clear()
        isUnlocked = false
    }

    @Synchronized
    fun get(providerId: String): String? = values[providerId]

    @Synchronized
    fun put(providerId: String, value: String) {
        values[providerId] = value
    }

    @Synchronized
    fun remove(providerId: String) {
        values.remove(providerId)
    }

    @Synchronized
    fun size(): Int = values.size
}

/** When the app must ask for authentication again. Pure logic, unit-tested. */
internal object LockPolicy {
    /** Lock after this long in the background. */
    const val BACKGROUND_TIMEOUT_MS = 5 * 60 * 1000L

    fun shouldLockOnResume(backgroundSinceMs: Long?, nowMs: Long, timeoutMs: Long = BACKGROUND_TIMEOUT_MS): Boolean {
        if (backgroundSinceMs == null) return false
        // Clock moved backwards (manual change): fail safe and lock.
        if (nowMs < backgroundSinceMs) return true
        return nowMs - backgroundSinceMs >= timeoutMs
    }
}

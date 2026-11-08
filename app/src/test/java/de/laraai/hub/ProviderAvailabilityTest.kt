package de.laraai.hub

import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderAvailabilityTest {
    private val day = ProviderAvailability.DEFAULT_MAX_AGE_MS

    @Test
    fun untestedIsNotAvailable() {
        assertEquals(ProviderAvailability.State.UNGEPRUEFT, ProviderAvailability.state(null, 0))
    }

    @Test
    fun failedIsNotAvailable() {
        val c = ProviderCheck("openai", false, "HTTP 401", 1000, null)
        assertEquals(ProviderAvailability.State.FEHLER, ProviderAvailability.state(c, 2000))
    }

    @Test
    fun recentSuccessIsAvailableOldSuccessIsStale() {
        val c = ProviderCheck("gemini", true, "ok", 1000, 1000)
        assertEquals(ProviderAvailability.State.VERFUEGBAR, ProviderAvailability.state(c, 1000 + day))
        assertEquals(ProviderAvailability.State.VERALTET, ProviderAvailability.state(c, 1001 + day))
    }

    @Test
    fun failureKeepsLastSuccessTime() {
        val ok = ProviderAvailability.merge(null, "openai", true, "ok", 100)
        val fail = ProviderAvailability.merge(ok, "openai", false, "HTTP 429", 200)
        assertEquals(100L, fail.lastSuccessMs)
        assertEquals(false, fail.ok)
        assertEquals(null, ProviderAvailability.merge(null, "x", false, "e", 5).lastSuccessMs)
    }

    @Test
    fun labels() {
        assertEquals("nicht erreichbar", ProviderAvailability.label(ProviderAvailability.State.FEHLER))
        assertEquals("verfügbar", ProviderAvailability.label(ProviderAvailability.State.VERFUEGBAR))
    }
}

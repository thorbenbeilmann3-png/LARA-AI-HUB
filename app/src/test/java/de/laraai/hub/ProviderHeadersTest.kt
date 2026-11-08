package de.laraai.hub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderHeadersTest {
    private fun names(p: AiProvider, key: String?) = providerHeaders(p, key).map { it.first }

    @Test
    fun anthropicUsesXApiKeyNotBearer() {
        val h = providerHeaders(AiProvider.ANTHROPIC, "k").toMap()
        assertEquals("k", h["x-api-key"])
        assertEquals("2026-10-21", h["anthropic-version"])
        assertTrue("Authorization" !in h)
    }

    @Test
    fun geminiUsesGoogleHeader() {
        assertEquals(listOf("x-goog-api-key" to "g"), providerHeaders(AiProvider.GEMINI, "g"))
    }

    @Test
    fun openAiCompatibleUseBearer() {
        for (p in listOf(AiProvider.OPENAI, AiProvider.OPENROUTER, AiProvider.CEREBRAS, AiProvider.OPENCLAW, AiProvider.CUSTOM)) {
            assertEquals(p.name, listOf("Authorization" to "Bearer s"), providerHeaders(p, "s"))
        }
    }

    @Test
    fun noKeyNoAuthHeader() {
        assertEquals(emptyList<String>(), names(AiProvider.CUSTOM, null))
        assertEquals(emptyList<String>(), names(AiProvider.OLLAMA, "ignored"))
    }
}

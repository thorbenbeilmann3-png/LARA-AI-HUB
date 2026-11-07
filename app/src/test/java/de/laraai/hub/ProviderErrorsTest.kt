package de.laraai.hub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ProviderErrorsTest {
    private fun http(code: Int) = IllegalStateException("HTTP $code: {\"error\":\"x\"}")

    @Test
    fun httpCodesAreExplained() {
        assertTrue(ProviderErrors.describe("OpenAI", http(401)).startsWith("OpenAI: API-Schlüssel ungültig"))
        assertTrue(ProviderErrors.describe("Gemini", http(403)).contains("Zugriff verweigert"))
        assertTrue(ProviderErrors.describe("OpenAI", http(404)).contains("Modell oder Adresse nicht gefunden"))
        assertTrue(ProviderErrors.describe("Gemini", http(429)).contains("Kontingent"))
        assertTrue(ProviderErrors.describe("OpenAI", http(503)).contains("Störung beim Anbieter (HTTP 503)"))
        assertTrue(ProviderErrors.describe("OpenAI", http(401)).contains("Details: HTTP 401"))
    }

    @Test
    fun networkErrorsAreExplained() {
        assertTrue(ProviderErrors.describe("Gemini", UnknownHostException("x")).contains("keine Internetverbindung"))
        assertTrue(ProviderErrors.describe("Ollama", ConnectException("refused")).contains("Server nicht erreichbar"))
        assertTrue(ProviderErrors.describe("OpenAI", SocketTimeoutException()).contains("Zeitüberschreitung"))
    }

    @Test
    fun larasOwnMessagesStayReadable() {
        assertEquals(
            "OpenAI: Für OpenAI ist noch kein API-Schlüssel gespeichert.",
            ProviderErrors.describe("OpenAI", IllegalStateException("Für OpenAI ist noch kein API-Schlüssel gespeichert."))
        )
    }

    @Test
    fun emptyMessageGetsType() {
        assertEquals("X: Unbekannter Fehler (RuntimeException).", ProviderErrors.describe("X", RuntimeException()))
    }

    @Test
    fun defaultModelSkipsNonChatModels() {
        val list = listOf("text-embedding-3-small", "whisper-1", "tts-1", "dall-e-3", "gpt-chat-model")
        assertEquals("gpt-chat-model", ModelChoice.pickDefault(list))
        assertEquals("only-embedding", ModelChoice.pickDefault(listOf("only-embedding")))
        assertEquals("", ModelChoice.pickDefault(emptyList()))
        assertEquals("qwen2.5-0.5b-instruct-q4_k_m", ModelChoice.pickDefault(listOf("qwen2.5-0.5b-instruct-q4_k_m")))
    }
}

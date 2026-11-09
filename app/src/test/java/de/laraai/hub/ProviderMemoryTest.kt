package de.laraai.hub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderMemoryTest {
    private val settings = FakeSettings()
    private val memory = ProviderMemory(settings)

    @Test
    fun `nothing remembered means no suggestion`() {
        assertEquals(RememberedInputs(), memory.load("ollama"))
    }

    @Test
    fun `address model and mcp address are remembered`() {
        memory.remember("ollama", "http://127.0.0.1:12434/", "qwen2.5:0.5b", "http://127.0.0.1:8765/mcp")
        assertEquals(
            RememberedInputs("http://127.0.0.1:12434/", "qwen2.5:0.5b", "http://127.0.0.1:8765/mcp"),
            memory.load("ollama")
        )
    }

    @Test
    fun `each provider keeps its own values`() {
        memory.remember("ollama", address = "http://127.0.0.1:12434/", model = "a")
        memory.remember("freellmapi", address = "https://llm.example.org/v1", model = "b")
        assertEquals("http://127.0.0.1:12434/", memory.load("ollama").address)
        assertEquals("a", memory.load("ollama").model)
        assertEquals("https://llm.example.org/v1", memory.load("freellmapi").address)
        assertEquals("b", memory.load("freellmapi").model)
    }

    @Test
    fun `last value wins and spaces are trimmed`() {
        memory.remember("openai", address = "https://a.example/v1", model = "m1")
        memory.remember("openai", address = "  https://b.example/v1 ", model = " m2 ")
        assertEquals(RememberedInputs("https://b.example/v1", "m2"), memory.load("openai"))
    }

    @Test
    fun `empty input keeps the previous value`() {
        memory.remember("openai", address = "https://a.example/v1", model = "m1", mcp = "http://127.0.0.1:8765/mcp")
        memory.remember("openai", address = " ", model = "", mcp = null)
        assertEquals(RememberedInputs("https://a.example/v1", "m1", "http://127.0.0.1:8765/mcp"), memory.load("openai"))
    }

    @Test
    fun `blocked home network address may still be suggested`() {
        memory.remember("freellmapi", address = AiProvider.FREELLMAPI.defaultEndpoint)
        assertEquals(AiProvider.FREELLMAPI.defaultEndpoint, memory.load("freellmapi").address)
    }

    @Test
    fun `every provider default and the mcp default are accepted`() {
        AiProvider.entries.map { it.defaultEndpoint }.filter { it.isNotEmpty() }.forEach {
            assertNotNull(it, ProviderMemory.cleanAddress(it))
        }
        assertNotNull(ProviderMemory.cleanAddress(AlpineProbe.DEFAULT_MCP_URL))
        assertNotNull(ProviderMemory.cleanAddress(AlpineProbe.DEFAULT_OLLAMA_URL))
    }

    @Test
    fun `addresses that could carry a secret are not stored`() {
        listOf(
            "https://user:geheim@example.org/v1",
            "https://example.org/v1?key=abc",
            "https://example.org/v1#abc",
            "https://example.org/v1/sk-abcdef",
            "https://example.org/v1/ghp_abcdef",
            "https://example.org/aaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            "https://example.org/a/b/c/d/e",
            "ftp://example.org/v1",
            "http:///v1",
            "kein link",
            "sk-abcdefghijklmnop"
        ).forEach {
            memory.remember("custom", address = it, mcp = it)
            assertEquals(it, RememberedInputs(), memory.load("custom"))
        }
        assertTrue(settings.values.isEmpty())
    }

    @Test
    fun `model field keeps no credential typed by mistake`() {
        listOf(
            "sk-proj-abcdef",
            "sk_live_abcdef",
            "ghp_abcdef",
            "github_pat_abcdef",
            "freellmapi-abcdef",
            "AIzaSyabcdef",
            "gsk_abcdef",
            "csk-abcdef",
            "hf_abcdef",
            "eyJhbGciOiJIUzI1NiJ9",
            "Bearer abcdef",
            "bearerabcdef",
            "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6",
            "zwei worte"
        ).forEach {
            memory.remember("custom", model = it)
            assertNull(it, memory.load("custom").model)
        }
        assertTrue(settings.values.isEmpty())
    }

    @Test
    fun `a bad entry does not erase the good value before it`() {
        memory.remember("custom", model = "gut")
        memory.remember("custom", model = "sk-abcdef")
        assertEquals("gut", memory.load("custom").model)
    }

    @Test
    fun `real model names are accepted`() {
        listOf(
            "qwen2.5-0.5b-instruct-q4_k_m",
            "qwen2.5:0.5b",
            "gpt-4o-mini",
            "claude-opus-5-5",
            "openrouter/auto",
            "meta-llama/llama-3.3-70b-versatile:free",
            "models/gemini-2.5-flash",
            "auto"
        ).forEach { assertEquals(it, ProviderMemory.cleanModel(it)) }
    }

    @Test
    fun `too long values are not stored`() {
        assertNull(ProviderMemory.cleanModel("a-".repeat(61)))
        assertNull(ProviderMemory.cleanAddress("https://example.org/" + "a/".repeat(100)))
    }
}

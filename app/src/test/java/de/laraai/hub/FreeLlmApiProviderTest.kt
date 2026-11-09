package de.laraai.hub

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.InetAddress
import java.util.concurrent.TimeUnit

class FreeLlmApiProviderTest {
    private val provider = AiProvider.FREELLMAPI
    private lateinit var server: MockWebServer

    @Before
    fun start() {
        server = MockWebServer().apply { start(InetAddress.getByName("127.0.0.1"), 0) }
    }

    @After
    fun stop() = server.shutdown()

    private fun address(path: String) = "http://127.0.0.1:${server.port}$path"
    private fun modelList() = MockResponse().setBody("""{"object":"list","data":[{"id":"auto"},{"id":"gemini-2.5-flash"}]}""")

    @Test
    fun `is a provider with the agreed default address and an optional key`() {
        assertEquals(provider, AiProvider.fromId("freellmapi"))
        assertEquals("FreeLLMAPI", provider.label)
        assertEquals("http://192.168.178.21:3001/v1", provider.defaultEndpoint)
        assertFalse(provider.requiresApiKey)
        assertTrue(provider.acceptsApiKey)
    }

    @Test
    fun `connection test is one GET of the model list and nothing else`() {
        server.enqueue(modelList())
        val result = ApiConnection.listModels(ApiConnection.rootUrl(address("/v1"), provider), provider, null)
        assertEquals(listOf("auto", "gemini-2.5-flash"), result.models)
        assertEquals(1, server.requestCount)
        val request = assertNotNull(server.takeRequest(1, TimeUnit.SECONDS))
        assertEquals("GET", request.method)
        assertEquals("/v1/models", request.path)
        assertEquals(0L, request.bodySize)
        assertNull(request.getHeader("Authorization"))
    }

    @Test
    fun `optional key goes out as bearer token`() {
        server.enqueue(modelList())
        ApiConnection.listModels(ApiConnection.rootUrl(address("/v1/"), provider), provider, "testwert")
        val request = assertNotNull(server.takeRequest(1, TimeUnit.SECONDS))
        assertEquals("Bearer testwert", request.getHeader("Authorization"))
        assertEquals("/v1/models", request.path)
    }

    @Test
    fun `no key means no header`() {
        assertEquals(emptyList<Pair<String, String>>(), providerHeaders(provider, null))
        assertEquals(emptyList<Pair<String, String>>(), providerHeaders(provider, " "))
    }

    @Test
    fun `http default address in the home network gets the existing block message`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            ApiConnection.rootUrl(provider.defaultEndpoint, provider)
        }
        assertEquals(
            "Unverschlüsseltes HTTP ist nur für Dienste auf diesem Handy erlaubt " +
                "(z. B. http://127.0.0.1:12434). Für andere Geräte bitte HTTPS verwenden.",
            error.message
        )
        assertEquals("FreeLLMAPI: ${error.message}", ProviderErrors.describe(provider.label, error))
    }

    @Test
    fun `other home network addresses over http are blocked too`() {
        for (url in listOf("http://10.0.0.5:3001/v1", "http://172.16.0.9:3001/v1", "http://fritz.box:3001/v1")) {
            assertFalse(url, ApiConnection.isCleartextAllowed(url))
        }
    }

    @Test
    fun `https and this phone are allowed`() {
        assertEquals("https://llm.example.org", ApiConnection.rootUrl("https://llm.example.org/v1", provider))
        assertEquals("https://192.168.178.21:3001", ApiConnection.rootUrl("https://192.168.178.21:3001/v1", provider))
        assertEquals("http://127.0.0.1:3001", ApiConnection.rootUrl("http://127.0.0.1:3001/v1", provider))
        assertEquals("http://localhost:3001", ApiConnection.rootUrl("http://localhost:3001/v1", provider))
    }

    @Test
    fun `key travels only over https or inside this phone`() {
        assertTrue(ApiConnection.mayCarryCredentials(provider, "https://llm.example.org"))
        assertTrue(ApiConnection.mayCarryCredentials(provider, "http://127.0.0.1:3001"))
        assertTrue(ApiConnection.mayCarryCredentials(provider, "http://localhost:3001"))
        assertFalse(ApiConnection.mayCarryCredentials(provider, "http://192.168.178.21:3001"))
    }

    @Test
    fun `the phone exception stays limited to FreeLLMAPI`() {
        assertFalse(ApiConnection.mayCarryCredentials(AiProvider.CUSTOM, "http://127.0.0.1:3001"))
        assertFalse(ApiConnection.mayCarryCredentials(AiProvider.OPENAI, "http://127.0.0.1:3001"))
    }

    @Test
    fun `network security config has no exception for the home network`() {
        val config = File("src/main/res/xml/network_security_config.xml").readText()
        val domains = Regex("<domain[^>]*>([^<]+)</domain>").findAll(config).map { it.groupValues[1] }.toSet()
        assertEquals(setOf("127.0.0.1", "localhost"), domains)
        assertTrue(config.contains("""<base-config cleartextTrafficPermitted="false">"""))
        assertTrue(config.contains("""<certificates src="system" />"""))
        assertFalse(config.contains("""src="user""""))
    }

    @Test
    fun `app explains the consequence for this provider only`() {
        val note = assertNotNull(ProviderNotes.forProvider(provider))
        assertTrue(note.contains("erst mit HTTPS nutzbar"))
        assertTrue(note.contains("Reverse-Proxy"))
        assertTrue(note.contains("127.0.0.1"))
        AiProvider.entries.filter { it != provider }.forEach { assertNull(ProviderNotes.forProvider(it)) }
    }

    private fun <T> assertNotNull(value: T?): T {
        org.junit.Assert.assertNotNull(value)
        return value!!
    }
}

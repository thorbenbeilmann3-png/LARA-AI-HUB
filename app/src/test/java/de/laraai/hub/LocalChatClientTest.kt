package de.laraai.hub

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.TimeUnit

/** Runs the client through real OkHttp against loopback sockets. Says nothing about the Pixel. */
class LocalChatClientTest {
    private val loopback = InetAddress.getByName("127.0.0.1")
    private val server = MockWebServer()
    private val client = LocalChatClient(timeoutMs = 500)
    private val model = LocalChatClient.DEFAULT_MODEL
    private val address get() = "http://127.0.0.1:${server.port}/"

    @Before
    fun start() = server.start(loopback, 0)

    @After
    fun stop() = server.shutdown()

    private fun openAi(text: Any?) = MockResponse().setBody(
        JSONObject()
            .put("choices", JSONArray().put(JSONObject().put("message", JSONObject().put("role", "assistant").put("content", text ?: JSONObject.NULL))))
            .put("usage", JSONObject().put("prompt_tokens", 93).put("completion_tokens", 9))
            .toString()
    )

    private fun send(to: String = address, api: ApiMode = ApiMode.OPENAI_COMPATIBLE, using: LocalChatClient = client) =
        using.send(to, model, api, emptyList(), "Hallo")

    private fun failure(block: () -> Unit): LocalChatException =
        assertThrows(LocalChatException::class.java) { block() }

    private fun taken(): RecordedRequest = server.takeRequest(1, TimeUnit.SECONDS)!!

    private fun closedPort(): Int = ServerSocket(0, 1, loopback).use { it.localPort }

    private fun answerFails(answer: MockResponse, api: ApiMode = ApiMode.OPENAI_COMPATIBLE): LocalChatException {
        server.enqueue(answer)
        return failure { send(api = api) }
    }

    // Requests

    @Test
    fun `default model and limit are the agreed ones`() {
        assertEquals("qwen2.5-0.5b-instruct-q4_k_m", LocalChatClient.DEFAULT_MODEL)
        assertEquals(15_000L, LocalChatClient.TIMEOUT_MS)
    }

    @Test
    fun `sends one openai compatible request without any key`() {
        server.enqueue(openAi("Hallo!"))
        val reply = send()
        assertEquals("Hallo!", reply.text)
        assertEquals(93, reply.usage?.inputTokens)
        assertEquals(9, reply.usage?.outputTokens)
        assertEquals(1, server.requestCount)
        val request = taken()
        assertEquals("POST", request.method)
        assertEquals("/v1/chat/completions", request.path)
        assertNull(request.getHeader("Authorization"))
        val body = JSONObject(request.body.readUtf8())
        assertEquals(model, body.getString("model"))
        assertFalse(body.getBoolean("stream"))
        val message = body.getJSONArray("messages").getJSONObject(0)
        assertEquals("user", message.getString("role"))
        assertEquals("Hallo", message.getString("content"))
    }

    @Test
    fun `api chat can be tried too`() {
        server.enqueue(MockResponse().setBody("""{"message":{"role":"assistant","content":" Hi "},"prompt_eval_count":5,"eval_count":2}"""))
        val reply = send(api = ApiMode.OLLAMA_NATIVE)
        assertEquals("Hi", reply.text)
        assertEquals(5, reply.usage?.inputTokens)
        assertEquals("/api/chat", taken().path)
    }

    @Test
    fun `earlier messages go along in order`() {
        server.enqueue(openAi("ok"))
        client.send(address, model, ApiMode.OPENAI_COMPATIBLE, listOf(ChatTurn("user", "A"), ChatTurn("assistant", "B")), "C")
        val messages = JSONObject(taken().body.readUtf8()).getJSONArray("messages")
        val seen = (0 until messages.length()).map { messages.getJSONObject(it).let { m -> m.getString("role") + ":" + m.getString("content") } }
        assertEquals(listOf("user:A", "assistant:B", "user:C"), seen)
    }

    // F2: the configured port

    @Test
    fun `only the configured port receives the request`() {
        val other = MockWebServer().apply { start(loopback, 0) }
        try {
            server.enqueue(openAi("ok"))
            send("http://127.0.0.1:${server.port}/")
            assertEquals(1, server.requestCount)
            assertEquals(0, other.requestCount)
        } finally {
            other.shutdown()
        }
    }

    @Test
    fun `a stopped server is reported and no other port is tried`() {
        val other = MockWebServer().apply { start(loopback, 0) }
        try {
            val error = failure { send("http://127.0.0.1:${closedPort()}/") }
            assertEquals(LocalChatProblem.SERVER_OFF, error.problem)
            assertEquals(0, other.requestCount)
        } finally {
            other.shutdown()
        }
    }

    @Test
    fun `usual ways of writing the address reach the same path`() {
        for (suffix in listOf("", "/", "/v1", "/v1/", "/v1/chat/completions", "/api/chat")) {
            server.enqueue(openAi("ok"))
            send("http://127.0.0.1:${server.port}$suffix")
            assertEquals(suffix, "/v1/chat/completions", taken().path)
        }
    }

    // F4: timeout

    private fun assertCutOff(using: LocalChatClient, limitRange: LongRange) {
        val started = System.nanoTime()
        val error = failure { send(using = using) }
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        assertEquals(LocalChatProblem.TIMEOUT, error.problem)
        assertTrue(error.message, error.message!!.contains("keine Antwort (Server eingefroren?)"))
        assertTrue("took $elapsedMs ms", elapsedMs in limitRange)
    }

    @Test
    fun `frozen server is given up on`() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        assertCutOff(client, 0L..3_000L)
    }

    @Test
    fun `an answer that only trickles in is cut off as well`() {
        server.enqueue(MockResponse().setBody("x".repeat(1000)).throttleBody(1, 100, TimeUnit.MILLISECONDS))
        assertCutOff(client, 0L..3_000L)
    }

    @Test
    fun `the real default gives up after fifteen seconds`() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        assertCutOff(LocalChatClient(), 14_000L..18_000L)
    }

    // F5: one error per cause

    @Test
    fun `wrong addresses are named as such and nothing is sent`() {
        for (wrong in listOf("", "   ", "abc", "ftp://127.0.0.1:12434", "http://", "http://127.0.0.1:99999/", "http://192.168.178.21:12434/v1")) {
            val error = failure { send(wrong) }
            assertEquals(wrong, LocalChatProblem.ADDRESS_WRONG, error.problem)
            assertTrue(wrong, error.message!!.startsWith("Adresse falsch"))
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a name that does not resolve is a wrong address`() {
        assertEquals(LocalChatProblem.ADDRESS_WRONG, failure { send("https://lara-chat-test.invalid/") }.problem)
    }

    @Test
    fun `server off says so`() {
        val error = failure { send("http://127.0.0.1:${closedPort()}/") }
        assertEquals(LocalChatProblem.SERVER_OFF, error.problem)
        assertTrue(error.message, error.message!!.startsWith("Server aus"))
    }

    @Test
    fun `missing model is named`() {
        val answers = listOf(
            MockResponse().setResponseCode(404).setBody("""{"error":{"message":"The model `x` does not exist","code":"model_not_found"}}"""),
            MockResponse().setResponseCode(404).setBody("""{"error":"model 'x' not found"}"""),
            MockResponse().setResponseCode(400).setBody("""{"error":"model 'x' not found, try pulling it first"}"""),
            MockResponse().setBody("""{"error":"unknown model x"}""")
        )
        for (answer in answers) {
            val error = answerFails(answer)
            assertEquals(LocalChatProblem.MODEL_MISSING, error.problem)
            assertTrue(error.message, error.message!!.contains(model))
        }
    }

    @Test
    fun `missing interface is not mistaken for a missing model`() {
        val answers = listOf(
            MockResponse().setResponseCode(404).setBody("404 page not found"),
            MockResponse().setResponseCode(404),
            MockResponse().setResponseCode(405)
        )
        for (answer in answers) {
            val error = answerFails(answer, ApiMode.OLLAMA_NATIVE)
            assertEquals(LocalChatProblem.API_MISSING, error.problem)
            assertTrue(error.message, error.message!!.contains("/api/chat"))
        }
    }

    @Test
    fun `other model complaints are not a missing model`() {
        val error = answerFails(MockResponse().setResponseCode(400).setBody("""{"error":{"message":"This model's maximum context length is 2048 tokens"}}"""))
        assertEquals(LocalChatProblem.SERVER_ERROR, error.problem)
        assertTrue(error.message, error.message!!.contains("400"))
    }

    @Test
    fun `server errors name the status`() {
        val error = answerFails(MockResponse().setResponseCode(500).setBody("kaputt"))
        assertEquals(LocalChatProblem.SERVER_ERROR, error.problem)
        assertTrue(error.message, error.message!!.contains("500"))
        assertFalse(error.message, error.message!!.contains("kaputt"))
    }

    @Test
    fun `unreadable answers are reported`() {
        for (body in listOf("<html>", "{}", """{"choices":[]}""", """{"choices":[{}]}""", """{"error":"irgendwas"}""")) {
            assertEquals(body, LocalChatProblem.BAD_RESPONSE, answerFails(MockResponse().setBody(body)).problem)
        }
    }

    @Test
    fun `empty answers are reported`() {
        for (text in listOf<Any?>("", "   ", null)) {
            assertEquals(LocalChatProblem.EMPTY_REPLY, answerFails(openAi(text)).problem)
        }
    }

    @Test
    fun `every cause has its own wording`() {
        val messages = listOf(
            failure { send("abc") },
            failure { send("http://127.0.0.1:${closedPort()}/") },
            answerFails(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)),
            answerFails(MockResponse().setResponseCode(404).setBody("""{"error":"model 'x' not found"}""")),
            answerFails(MockResponse().setResponseCode(404)),
            answerFails(MockResponse().setResponseCode(500)),
            answerFails(MockResponse().setBody("<html>")),
            answerFails(openAi(""))
        ).map { it.message!!.substringBefore(":") }
        assertEquals(messages.toString(), messages.size, messages.toSet().size)
    }

    // Model list

    @Test
    fun `model list is read from v1 models only`() {
        server.enqueue(MockResponse().setBody("""{"data":[{"id":"a"},{"id":"b"}]}"""))
        val result = client.models(address)
        assertEquals(ApiMode.OPENAI_COMPATIBLE, result.mode)
        assertEquals(listOf("a", "b"), result.models)
        assertEquals(1, server.requestCount)
        val request = taken()
        assertEquals("GET", request.method)
        assertEquals("/v1/models", request.path)
    }

    @Test
    fun `api tags is the fallback when v1 models is missing`() {
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setBody("""{"models":[{"name":"qwen2.5:0.5b"}]}"""))
        val result = client.models(address)
        assertEquals(ApiMode.OLLAMA_NATIVE, result.mode)
        assertEquals(listOf("qwen2.5:0.5b"), result.models)
        assertEquals("/v1/models", taken().path)
        assertEquals("/api/tags", taken().path)
    }

    @Test
    fun `a frozen server is not asked a second time`() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        assertEquals(LocalChatProblem.TIMEOUT, failure { client.models(address) }.problem)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `model list reports server off`() {
        assertEquals(LocalChatProblem.SERVER_OFF, failure { client.models("http://127.0.0.1:${closedPort()}/") }.problem)
    }

    @Test
    fun `model list reports missing interfaces`() {
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setResponseCode(404))
        assertEquals(LocalChatProblem.API_MISSING, failure { client.models(address) }.problem)
    }

    @Test
    fun `model list reports server errors`() {
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setResponseCode(500))
        assertEquals(LocalChatProblem.SERVER_ERROR, failure { client.models(address) }.problem)
    }
}

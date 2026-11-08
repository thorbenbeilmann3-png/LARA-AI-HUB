package de.laraai.hub

import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.Proxy
import java.net.ProxySelector
import java.net.ServerSocket
import java.net.SocketAddress
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import java.net.UnknownServiceException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Runs the probe through real OkHttp and real loopback sockets.
 * These tests prove the probe logic only. They say nothing about the Pixel:
 * whether the LARA process can reach Alpine must be shown on the device.
 */
class AlpineProbeTest {
    private val server = MockWebServer()
    private val requests = CopyOnWriteArrayList<RecordedRequest>()

    @After
    fun tearDown() {
        runCatching { server.shutdown() }
    }

    private fun serve(routes: (String) -> MockResponse): String {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests.add(request)
                return routes(request.path.orEmpty())
            }
        }
        server.start(InetAddress.getByName("127.0.0.1"), 0)
        return "http://127.0.0.1:${server.port}"
    }

    private fun json(body: String) =
        MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(body)

    private fun probe(readMs: Long = 2000) =
        AlpineProbe(OkHttpProbeTransport(connectMs = 1000, readMs = readMs, callMs = 5000))

    private val healthy: (String) -> MockResponse = { path ->
        when (path) {
            "/api/version" -> json("""{"version":"0.12.6"}""")
            "/api/tags" -> json("""{"models":[{"name":"qwen2.5:0.5b","model":"qwen2.5:0.5b"},{"name":"llama3:latest"}]}""")
            "/mcp" -> MockResponse().setResponseCode(401).setHeader("WWW-Authenticate", "Bearer")
            else -> MockResponse().setResponseCode(404)
        }
    }

    private fun freePort(): Int = ServerSocket(0).use { it.localPort }

    @Test
    fun healthyServerIsReachedAndExpectedModelFound() {
        val base = serve(healthy)
        val report = probe().run(base, "$base/mcp", "qwen2.5:0.5b")
        assertTrue(report.ollamaReached)
        assertEquals("0.12.6", report.ollamaVersion)
        assertEquals(listOf("qwen2.5:0.5b", "llama3:latest"), report.models)
        assertEquals(ModelState.FOUND, report.modelState)
        assertTrue(report.expectedModelFound)
        assertEquals(ProbeResult.OK, report.steps[0].result)
        assertEquals(200, report.steps[0].httpCode)
        assertEquals(ProbeResult.OK, report.steps[1].result)
        assertEquals(ProbeResult.AUTH_REQUIRED, report.steps[2].result)
        assertEquals(401, report.steps[2].httpCode)
    }

    @Test
    fun onlyPlainProtocolHeadersAreSent() {
        val base = serve(healthy)
        probe().run(base, "$base/mcp", "qwen2.5:0.5b")
        assertEquals(3, requests.size)
        val allowed = setOf("host", "connection", "accept-encoding", "user-agent", "accept")
        requests.forEach { request ->
            assertEquals("GET", request.method)
            request.headers.names().forEach { name ->
                assertTrue("unexpected header $name on ${request.path}", name.lowercase() in allowed)
            }
        }
    }

    @Test
    fun refusedPortIsReportedAsRefusedAndNotReached() {
        val port = freePort()
        val report = probe().run("http://127.0.0.1:$port", "http://127.0.0.1:$port/mcp", "qwen2.5:0.5b")
        assertFalse(report.ollamaReached)
        assertFalse(report.expectedModelFound)
        report.steps.forEach { assertEquals(ProbeResult.REFUSED, it.result) }
        assertEquals("ConnectException", report.steps[0].detail?.substringBefore(" ("))
    }

    @Test
    fun readLimitEndsACallToASilentServer() {
        val base = serve { MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE) }
        val report = AlpineProbe(OkHttpProbeTransport(connectMs = 1000, readMs = 300, callMs = 60_000))
            .run(base, "$base/mcp", "qwen2.5:0.5b")
        assertFalse(report.ollamaReached)
        assertEquals(ProbeResult.READ_TIMEOUT, report.steps[0].result)
        assertTrue("took ${report.steps[0].millis} ms", report.steps[0].millis < 5_000)
    }

    @Test
    fun callLimitEndsACallEvenWithALongReadLimit() {
        val base = serve { MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE) }
        val report = AlpineProbe(OkHttpProbeTransport(connectMs = 1000, readMs = 60_000, callMs = 300))
            .run(base, "$base/mcp", "qwen2.5:0.5b")
        assertEquals(ProbeResult.TIMEOUT, report.steps[0].result)
        assertTrue("took ${report.steps[0].millis} ms", report.steps[0].millis < 5_000)
    }

    @Test
    fun httpErrorKeepsTheStatusCode() {
        val base = serve { MockResponse().setResponseCode(500).setBody("kaputt") }
        val report = probe().run(base, "$base/mcp", "qwen2.5:0.5b")
        assertFalse(report.ollamaReached)
        assertEquals(ProbeResult.HTTP_ERROR, report.steps[0].result)
        assertEquals(500, report.steps[0].httpCode)
    }

    @Test
    fun onlyARealVersionStringCounts() {
        val body = AtomicReference("")
        val base = serve { MockResponse().setResponseCode(200).setBody(body.get()) }
        listOf(
            "<html>hallo</html>", "{}", """{"a":1}""", """{"version":0}""", """{"version":false}""",
            """{"version":null}""", """{"version":""}""", """{"version":"hallo"}"""
        ).forEach { sample ->
            body.set(sample)
            val report = probe().run(base, "$base/mcp", "qwen2.5:0.5b")
            assertNull(sample, report.ollamaVersion)
            assertFalse(sample, report.ollamaReached)
            assertEquals(sample, ProbeResult.BAD_RESPONSE, report.steps[0].result)
        }
    }

    @Test
    fun aVersionWithoutAModelListIsNotCountedAsOllama() {
        val base = serve { path -> if (path == "/api/tags") MockResponse().setResponseCode(404) else healthy(path) }
        val report = probe().run(base, "$base/mcp", "qwen2.5:0.5b")
        assertNotNull(report.ollamaVersion)
        assertFalse(report.ollamaReached)
        assertEquals(ModelState.UNKNOWN, report.modelState)
        assertTrue(report.verdict, report.verdict.contains("nicht belegt"))
        assertFalse(report.verdict, report.verdict.contains("fehlt"))
    }

    @Test
    fun failedModelListNeverSaysTheModelIsMissing() {
        val base = serve { path -> if (path == "/api/tags") MockResponse().setResponseCode(500) else healthy(path) }
        val report = probe().run(base, "$base/mcp", "qwen2.5:0.5b")
        assertEquals(ModelState.UNKNOWN, report.modelState)
        assertFalse(report.verdict, report.verdict.contains("fehlt"))
    }

    @Test
    fun missingModelIsReportedWhileOllamaCountsAsReached() {
        val base = serve { path ->
            if (path == "/api/tags") json("""{"models":[{"name":"llama3:latest"}]}""") else healthy(path)
        }
        val report = probe().run(base, "$base/mcp", "qwen2.5:0.5b")
        assertTrue(report.ollamaReached)
        assertEquals(ModelState.MISSING, report.modelState)
        assertTrue(report.steps[1].message.contains("NICHT"))
        assertTrue(report.verdict, report.verdict.contains("fehlt"))
        assertFalse(report.verdict, report.verdict.contains("NICHT"))
    }

    @Test
    fun blankExpectedModelIsNotReportedAsMissing() {
        val base = serve(healthy)
        val report = probe().run(base, "$base/mcp", "   ")
        assertTrue(report.ollamaReached)
        assertEquals(ModelState.UNKNOWN, report.modelState)
        assertFalse(report.verdict, report.verdict.contains("fehlt"))
        assertTrue(report.verdict, report.verdict.contains("kein Modell"))
    }

    @Test
    fun emptyModelListIsHandled() {
        val base = serve { path -> if (path == "/api/tags") json("""{"models":[]}""") else healthy(path) }
        val report = probe().run(base, "$base/mcp", "qwen2.5:0.5b")
        assertTrue(report.ollamaReached)
        assertEquals(emptyList<String>(), report.models)
        assertEquals(ModelState.MISSING, report.modelState)
    }

    @Test
    fun mcpAnsweringWithoutLoginIsFlagged() {
        val base = serve { path -> if (path == "/mcp") json("{}") else healthy(path) }
        val report = probe().run(base, "$base/mcp", "qwen2.5:0.5b")
        assertEquals(ProbeResult.BAD_RESPONSE, report.steps[2].result)
        assertEquals(200, report.steps[2].httpCode)
    }

    @Test
    fun redirectsAreNotFollowed() {
        val base = serve { path ->
            if (path == "/api/version") MockResponse().setResponseCode(302).setHeader("Location", "/anderswo")
            else healthy(path)
        }
        val report = probe().run(base, "$base/mcp", "qwen2.5:0.5b")
        assertEquals(ProbeResult.HTTP_ERROR, report.steps[0].result)
        assertEquals(302, report.steps[0].httpCode)
        assertFalse(requests.any { it.path == "/anderswo" })
    }

    @Test
    fun aSystemProxyIsBypassed() {
        val base = serve(healthy)
        val deadProxy = Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", freePort()))
        val original = ProxySelector.getDefault()
        ProxySelector.setDefault(object : ProxySelector() {
            override fun select(uri: URI?): List<Proxy> = listOf(deadProxy)
            override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) = Unit
        })
        try {
            assertTrue(probe().run(base, "$base/mcp", "qwen2.5:0.5b").ollamaReached)
        } finally {
            ProxySelector.setDefault(original)
        }
    }

    @Test
    fun mcpProbeDoesNotWaitForAStreamingBody() {
        val base = serve { path ->
            if (path == "/mcp") MockResponse().setResponseCode(401)
                .setHeader("Content-Type", "text/event-stream")
                .setBody("data: 1\n\n")
                .setBodyDelay(3, TimeUnit.SECONDS)
            else healthy(path)
        }
        val report = probe(readMs = 2000).run(base, "$base/mcp", "qwen2.5:0.5b")
        assertEquals(ProbeResult.AUTH_REQUIRED, report.steps[2].result)
        assertTrue("took ${report.steps[2].millis} ms", report.steps[2].millis < 1900)
    }

    @Test
    fun oversizedBodyIsCutOffAtTheLimit() {
        val base = serve { path ->
            if (path == "/api/version") json("""{"version":"0.12.6","padding":"${"x".repeat(200_000)}"}""") else healthy(path)
        }
        val report = probe().run(base, "$base/mcp", "qwen2.5:0.5b")
        assertEquals(ProbeResult.BAD_RESPONSE, report.steps[0].result)
        assertFalse(report.ollamaReached)
    }

    @Test
    fun serverTextNeverReachesTheReport() {
        val base = serve { MockResponse().setStatus("GEHEIM4 kaputt") }
        val report = probe().run(base, "$base/mcp", "qwen2.5:0.5b")
        assertEquals(ProbeResult.ERROR, report.steps[0].result)
        assertFalse(AlpineProbe.formatReport(report, environment, "t").contains("GEHEIM4"))
    }

    @Test
    fun plainHttpToAnotherDeviceIsBlockedWithoutAnyRequest() {
        val report = AlpineProbe(forbiddenTransport).run("http://192.168.1.10:11434", "http://192.168.1.10:8765/mcp", "x")
        report.steps.forEach { assertEquals(ProbeResult.BLOCKED, it.result) }
        assertFalse(report.ollamaReached)
    }

    @Test
    fun addressesWithCredentialsOrParametersAreRejectedAndNeverEchoed() {
        val report = AlpineProbe(forbiddenTransport).run(
            "http://nutzer:GEHEIM1@127.0.0.1:11434",
            "http://127.0.0.1:8765/mcp?token=GEHEIM2",
            "x"
        )
        report.steps.forEach { assertEquals(ProbeResult.INVALID_ADDRESS, it.result) }
        val text = AlpineProbe.formatReport(report, environment, "08.11.2026 21:00:00")
        assertFalse(text.contains("GEHEIM1"))
        assertFalse(text.contains("GEHEIM2"))
    }

    @Test
    fun tokensInPathsAreRejectedBeforeAnyRequest() {
        listOf(
            "http://127.0.0.1:8765/mcp/sk-GEHEIM3",
            "http://127.0.0.1:8765/mcp;token=GEHEIM3",
            "http://127.0.0.1:8765/GEHEIM3"
        ).forEach { address ->
            val report = AlpineProbe(forbiddenTransport).run("http://127.0.0.1:11434/GEHEIM3", address, "x")
            report.steps.forEach { assertEquals(address, ProbeResult.INVALID_ADDRESS, it.result) }
            assertFalse(AlpineProbe.formatReport(report, environment, "t").contains("GEHEIM3"))
        }
    }

    @Test
    fun onlyCleanAddressesMayBeStored() {
        assertNull(AlpineProbe.ollamaAddressProblem(AlpineProbe.DEFAULT_OLLAMA_URL))
        assertNull(AlpineProbe.ollamaAddressProblem("http://127.0.0.1:11434/"))
        assertNull(AlpineProbe.mcpAddressProblem(AlpineProbe.DEFAULT_MCP_URL))
        listOf("http://u:p@127.0.0.1:11434", "http://127.0.0.1:11434/x", "http://127.0.0.1:11434?t=1", "").forEach {
            assertNotNull(it, AlpineProbe.ollamaAddressProblem(it))
        }
        listOf("http://u:p@127.0.0.1:8765/mcp", "http://127.0.0.1:8765/mcp/x", "http://127.0.0.1:8765/mcp?t=1", "").forEach {
            assertNotNull(it, AlpineProbe.mcpAddressProblem(it))
        }
    }

    @Test
    fun malformedAddressesAreRejectedInEveryStep() {
        listOf("", "127.0.0.1:11434", "ftp://127.0.0.1", "http://").forEach { address ->
            val report = AlpineProbe(forbiddenTransport).run(address, address, "x")
            report.steps.forEach { assertEquals("address '$address'", ProbeResult.INVALID_ADDRESS, it.result) }
        }
    }

    private fun connectError(cause: String) =
        ConnectException("Failed to connect to /127.0.0.1:11434").apply { initCause(ConnectException(cause)) }

    @Test
    fun exceptionsAreClassifiedPrecisely() {
        val cases = listOf(
            connectError("failed to connect: connect failed: ECONNREFUSED (Connection refused)") to ProbeResult.REFUSED,
            connectError("Connection refused") to ProbeResult.REFUSED,
            connectError("connect failed: EACCES (Permission denied)") to ProbeResult.CONNECT_FAILED,
            connectError("connect failed: ENETUNREACH (Network is unreachable)") to ProbeResult.CONNECT_FAILED,
            ConnectException("Failed to connect to /127.0.0.1:11434") to ProbeResult.CONNECT_FAILED,
            NoRouteToHostException("No route to host") to ProbeResult.CONNECT_FAILED,
            SocketTimeoutException("Connect timed out") to ProbeResult.CONNECT_TIMEOUT,
            SocketTimeoutException("failed to connect to /127.0.0.1 (port 11434) from /127.0.0.1 (port 40000) after 3000ms") to ProbeResult.CONNECT_TIMEOUT,
            SocketTimeoutException("timeout") to ProbeResult.READ_TIMEOUT,
            SocketTimeoutException("Read timed out") to ProbeResult.READ_TIMEOUT,
            InterruptedIOException("timeout") to ProbeResult.TIMEOUT,
            UnknownServiceException("CLEARTEXT communication to 10.0.0.1 not permitted by network security policy") to ProbeResult.BLOCKED,
            UnknownServiceException("Unable to find acceptable protocols") to ProbeResult.ERROR,
            UnknownHostException("nope") to ProbeResult.UNKNOWN_HOST,
            IllegalArgumentException("bad url") to ProbeResult.INVALID_ADDRESS,
            IOException("sonstwas") to ProbeResult.ERROR
        )
        cases.forEach { (error, expected) -> assertEquals(error.toString(), expected, AlpineProbe.classify(error)) }
    }

    @Test
    fun detailNamesOnlyTheTypeAndAKnownErrorCode() {
        val report = AlpineProbe(ProbeTransport { _, _ ->
            throw connectError("failed from /10.0.0.7 (port 1): connect failed: EACCES (Permission denied)")
        }).run("http://127.0.0.1:11434", "http://127.0.0.1:8765/mcp", "x")
        assertEquals(ProbeResult.CONNECT_FAILED, report.steps[0].result)
        assertEquals("ConnectException (EACCES)", report.steps[0].detail)
    }

    @Test
    fun modelMatchingTreatsAMissingTagAsLatest() {
        assertTrue(AlpineProbe.hasModel(listOf("llama3:latest"), "llama3"))
        assertTrue(AlpineProbe.hasModel(listOf("llama3"), "llama3:latest"))
        assertTrue(AlpineProbe.hasModel(listOf("Qwen2.5:0.5b"), "qwen2.5:0.5b"))
        assertFalse(AlpineProbe.hasModel(listOf("qwen2.5:1.5b"), "qwen2.5:0.5b"))
        assertFalse(AlpineProbe.hasModel(emptyList(), "qwen2.5:0.5b"))
    }

    @Test
    fun reportNamesEveryStepAndContainsNoCredentialText() {
        val base = serve(healthy)
        val report = probe().run(base, "$base/mcp", "qwen2.5:0.5b")
        val text = AlpineProbe.formatReport(report, environment, "08.11.2026 21:00:00")
        report.steps.forEach { assertTrue(it.title, text.contains(it.title)) }
        assertTrue(text.contains("de.laraai.hub.alpinetest"))
        assertTrue(text.contains("abc1234"))
        assertTrue(text.contains("UID 10123"))
        assertTrue(text.contains("08.11.2026 21:00:00"))
        assertFalse(text.contains("Authorization"))
        assertFalse(text.contains("Bearer"))
    }

    @Test
    fun verdictNeverClaimsMoreThanWasObserved() {
        val base = serve(healthy)
        val reached = probe().run(base, "$base/mcp", "qwen2.5:0.5b")
        assertEquals("Ollama ist aus diesem App-Prozess erreichbar, das erwartete Modell ist vorhanden.", reached.verdict)
        val port = freePort()
        val missed = probe().run("http://127.0.0.1:$port", "http://127.0.0.1:$port/mcp", "qwen2.5:0.5b")
        assertEquals("Ollama ist aus diesem App-Prozess NICHT nachweislich erreichbar.", missed.verdict)
    }

    private val forbiddenTransport = ProbeTransport { url, _ ->
        throw AssertionError("no request may be sent to $url")
    }

    private val environment = ProbeEnvironment(
        packageName = "de.laraai.hub.alpinetest",
        buildSha = "abc1234",
        versionName = "0.1.0-alpinetest",
        uid = 10123,
        pid = 4242,
        sdkInt = 36,
        androidRelease = "16",
        device = "Google Pixel 9a"
    )
}

package de.laraai.hub

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket

class SetupWizardTest {
    private val environment = ProbeEnvironment("de.laraai.hub.alpinetest", "abc1234", "0.1.0", 10494, 1, 37, "17", "Google Pixel 9a")

    private fun step(result: ProbeResult, message: String) = ProbeStep("t", "u", result, null, 1, message)

    private fun alpineReport(version: String?, listRead: Boolean, state: ModelState, steps: List<ProbeStep>) =
        ProbeReport(steps, version, emptyList(), "qwen2.5:0.5b", state, listRead)

    private inner class Fake : SetupActions {
        var scan = LocalScan(ProbeResult.OK, 200, listOf(SetupWizard.KNOWN_LOCAL_MODEL), "1 Modell(e) gefunden.")
        var saveResult = true
        val saved = mutableListOf<String>()
        var chat: () -> String = { "Hallo" }
        val copied = mutableListOf<String>()
        var copyResult = true
        var probe = alpineReport(
            "0.5.1", true, ModelState.FOUND,
            listOf(step(ProbeResult.OK, "v"), step(ProbeResult.OK, "m"), step(ProbeResult.AUTH_REQUIRED, "mcp"))
        )
        var automate = SetupAutomateFacts(true, "1.40", false)
        val keys = mutableSetOf<AiProvider>()
        var unlocked = true
        var list: (AiProvider) -> List<String> = { listOf("a", "b") }
        var listCalls = 0
        var shareResult = true
        var shared: String? = null

        override fun environment() = this@SetupWizardTest.environment
        override fun deviceFacts() = SetupDeviceFacts("Pixel", true)
        override suspend fun scanLocalServer() = scan
        override fun saveLocalModel(model: String): Boolean { saved += model; return saveResult }
        override suspend fun chatLocal(model: String) = chat()
        override fun copyAlpineCommand(command: String): Boolean { copied += command; return copyResult }
        override suspend fun probeAlpine() = probe
        override fun automateFacts() = automate
        override fun hasKey(provider: AiProvider) = provider in keys
        override fun isUnlocked() = unlocked
        override suspend fun listProviderModels(provider: AiProvider): List<String> { listCalls++; return list(provider) }
        override fun shareReport(text: String): Boolean { shared = text; return shareResult }
    }

    private val fake = Fake()
    private val runner = SetupRunner(fake, nowMs = { 1_000L }, timestamp = { "JETZT" }, timeLabel = { "ZEIT" })

    private fun run(step: SetupStep, state: SetupState = SetupState()) = runBlocking { runner.run(step, state) }

    private fun ok(vararg steps: SetupStep, base: SetupState = SetupState()): SetupState =
        base.copy(outcomes = base.outcomes + steps.associateWith { StepOutcome(SetupStatus.OK, "ok", 1L) })

    @Test
    fun `there are ten numbered stages in order`() {
        assertEquals((1..10).toList(), SetupStep.entries.map { it.number })
    }

    @Test
    fun `next goes to the first stage that neither worked nor was skipped`() {
        assertEquals(SetupStep.APP_DEVICE, SetupState().next)
        val state = ok(SetupStep.APP_DEVICE).let { SetupWizard.skip(it, SetupStep.ALPINE_START, 1) }
        assertEquals(SetupStep.LOCAL_SERVER, state.next)
        assertNull(ok(*SetupStep.entries.toTypedArray()).next)
    }

    @Test
    fun `device stage reports a missing screen lock`() {
        val outcome = run(SetupStep.APP_DEVICE).state.outcomes.getValue(SetupStep.APP_DEVICE)
        assertEquals(SetupStatus.OK, outcome.status)
        assertTrue(outcome.message.contains("Displaysperre ist eingerichtet"))
    }

    @Test
    fun `a failed local search stays failed and stores no models`() {
        fake.scan = LocalScan(ProbeResult.REFUSED, null, emptyList(), "abgewiesen")
        val state = run(SetupStep.LOCAL_SERVER, SetupState(localModels = listOf("alt"))).state
        assertEquals(SetupStatus.FAILED, state.statusOf(SetupStep.LOCAL_SERVER))
        assertEquals("abgewiesen", state.outcomes.getValue(SetupStep.LOCAL_SERVER).message)
        assertTrue(state.localModels.isEmpty())
        assertEquals(SetupStep.APP_DEVICE, state.next)
    }

    @Test
    fun `a server without models does not count as found`() {
        fake.scan = LocalScan(ProbeResult.OK, 200, emptyList(), "leer")
        assertEquals(SetupStatus.FAILED, run(SetupStep.LOCAL_SERVER).state.statusOf(SetupStep.LOCAL_SERVER))
    }

    @Test
    fun `the known model wins, otherwise the first likely chat model`() {
        assertEquals(SetupWizard.KNOWN_LOCAL_MODEL, SetupWizard.chooseLocalModel(listOf("x", SetupWizard.KNOWN_LOCAL_MODEL)))
        assertEquals("llama", SetupWizard.chooseLocalModel(listOf("nomic-embed", "llama")))
        assertEquals("", SetupWizard.chooseLocalModel(emptyList()))
    }

    @Test
    fun `saving the model needs the search to have worked`() {
        val result = run(SetupStep.LOCAL_MODEL).state
        assertEquals(SetupStatus.NEEDS_USER, result.statusOf(SetupStep.LOCAL_MODEL))
        assertTrue(fake.saved.isEmpty())
    }

    @Test
    fun `the model is saved and remembered for the test message`() {
        val state = run(SetupStep.LOCAL_MODEL, ok(SetupStep.LOCAL_SERVER, base = SetupState(localModels = listOf("llama")))).state
        assertEquals(SetupStatus.OK, state.statusOf(SetupStep.LOCAL_MODEL))
        assertEquals("llama", state.chosenModel)
        assertEquals(listOf("llama"), fake.saved)
    }

    @Test
    fun `a model that could not be saved is a failure`() {
        fake.saveResult = false
        val state = run(SetupStep.LOCAL_MODEL, ok(SetupStep.LOCAL_SERVER, base = SetupState(localModels = listOf("llama")))).state
        assertEquals(SetupStatus.FAILED, state.statusOf(SetupStep.LOCAL_MODEL))
        assertEquals("", state.chosenModel)
    }

    private val readyForChat = ok(SetupStep.LOCAL_SERVER, SetupStep.LOCAL_MODEL, base = SetupState(localModels = listOf("m"), chosenModel = "m"))

    @Test
    fun `a real answer passes and is shown but not stored`() {
        fake.chat = { "  Hallo!  " }
        val result = run(SetupStep.LOCAL_CHAT, readyForChat)
        assertEquals(SetupStatus.OK, result.state.statusOf(SetupStep.LOCAL_CHAT))
        assertEquals("Hallo!", result.shownAnswer)
        assertFalse(SetupStateCodec.encode(result.state).contains("Hallo!"))
    }

    @Test
    fun `placeholder and empty answers are not success`() {
        listOf("", "   ", "null", "Leere Antwort").forEach { text ->
            fake.chat = { text }
            val result = run(SetupStep.LOCAL_CHAT, readyForChat)
            assertEquals(text, SetupStatus.FAILED, result.state.statusOf(SetupStep.LOCAL_CHAT))
            assertNull(result.shownAnswer)
        }
    }

    @Test
    fun `chat errors keep only the status code`() {
        fake.chat = { error("HTTP 500: geheimer Servertext") }
        val message = run(SetupStep.LOCAL_CHAT, readyForChat).state.outcomes.getValue(SetupStep.LOCAL_CHAT).message
        assertTrue(message.contains("HTTP 500"))
        assertFalse(message.contains("geheimer"))
    }

    @Test
    fun `the command is copied and the stage does not claim that Ollama runs`() {
        val outcome = run(SetupStep.ALPINE_START).state.outcomes.getValue(SetupStep.ALPINE_START)
        assertEquals(listOf("ollama serve"), fake.copied)
        assertEquals(SetupStatus.OK, outcome.status)
        assertTrue(outcome.message.contains("Ob Ollama wirklich läuft"))
    }

    @Test
    fun `a failed copy is a failure`() {
        fake.copyResult = false
        assertEquals(SetupStatus.FAILED, run(SetupStep.ALPINE_START).state.statusOf(SetupStep.ALPINE_START))
    }

    @Test
    fun `alpine passes only with server and model`() {
        val outcome = run(SetupStep.ALPINE_CHECK).state.outcomes.getValue(SetupStep.ALPINE_CHECK)
        assertEquals(SetupStatus.OK, outcome.status)
        assertTrue(outcome.message.contains("0.5.1"))
        assertTrue(outcome.message.contains(ProbeResult.AUTH_REQUIRED.label))
    }

    @Test
    fun `a missing alpine model asks the user and names the command`() {
        fake.probe = alpineReport("0.5.1", true, ModelState.MISSING, listOf(step(ProbeResult.OK, "v"), step(ProbeResult.OK, "m")))
        val outcome = run(SetupStep.ALPINE_CHECK).state.outcomes.getValue(SetupStep.ALPINE_CHECK)
        assertEquals(SetupStatus.NEEDS_USER, outcome.status)
        assertTrue(outcome.message.contains("ollama pull qwen2.5:0.5b"))
    }

    @Test
    fun `an unreachable alpine server gives the first real cause`() {
        fake.probe = alpineReport(null, false, ModelState.UNKNOWN, listOf(step(ProbeResult.REFUSED, "abgewiesen"), step(ProbeResult.ERROR, "zweite")))
        val outcome = run(SetupStep.ALPINE_CHECK).state.outcomes.getValue(SetupStep.ALPINE_CHECK)
        assertEquals(SetupStatus.FAILED, outcome.status)
        assertEquals("abgewiesen", outcome.message)
    }

    @Test
    fun `a service that only answers the version is not alpine ollama`() {
        fake.probe = alpineReport("0.1.32", false, ModelState.UNKNOWN, listOf(step(ProbeResult.OK, "v"), step(ProbeResult.BAD_RESPONSE, "keine Liste")))
        val outcome = run(SetupStep.ALPINE_CHECK).state.outcomes.getValue(SetupStep.ALPINE_CHECK)
        assertEquals(SetupStatus.FAILED, outcome.status)
        assertEquals("keine Liste", outcome.message)
    }

    @Test
    fun `automate missing needs the user, installed starts nothing`() {
        fake.automate = SetupAutomateFacts(false, null, false)
        assertEquals(SetupStatus.NEEDS_USER, run(SetupStep.AUTOMATE).state.statusOf(SetupStep.AUTOMATE))
        fake.automate = SetupAutomateFacts(true, "1.40", true)
        val outcome = run(SetupStep.AUTOMATE).state.outcomes.getValue(SetupStep.AUTOMATE)
        assertEquals(SetupStatus.OK, outcome.status)
        assertTrue(outcome.message.contains("1.40"))
        assertTrue(outcome.message.contains("Gestartet wurde nichts"))
    }

    @Test
    fun `without a key no request leaves the phone`() {
        assertEquals(SetupStatus.NEEDS_USER, run(SetupStep.CLAUDE).state.statusOf(SetupStep.CLAUDE))
        assertEquals(0, fake.listCalls)
    }

    @Test
    fun `a locked app sends nothing`() {
        fake.keys += AiProvider.OPENAI
        fake.unlocked = false
        assertEquals(SetupStatus.NEEDS_USER, run(SetupStep.CHATGPT).state.statusOf(SetupStep.CHATGPT))
        assertEquals(0, fake.listCalls)
    }

    @Test
    fun `each cloud stage checks its own provider`() {
        val asked = mutableListOf<AiProvider>()
        fake.keys += listOf(AiProvider.ANTHROPIC, AiProvider.OPENAI)
        fake.list = { asked += it; listOf("m") }
        assertEquals(SetupStatus.OK, run(SetupStep.CLAUDE).state.statusOf(SetupStep.CLAUDE))
        assertEquals(SetupStatus.OK, run(SetupStep.CHATGPT).state.statusOf(SetupStep.CHATGPT))
        assertEquals(listOf(AiProvider.ANTHROPIC, AiProvider.OPENAI), asked)
    }

    @Test
    fun `an empty provider list is a failure`() {
        fake.keys += AiProvider.ANTHROPIC
        fake.list = { emptyList() }
        assertEquals(SetupStatus.FAILED, run(SetupStep.CLAUDE).state.statusOf(SetupStep.CLAUDE))
    }

    @Test
    fun `provider errors never carry text that can quote the key`() {
        fake.keys += AiProvider.OPENAI
        fake.list = { error("HTTP 401: Incorrect API key provided: sk-proj-GEHEIM") }
        val state = run(SetupStep.CHATGPT).state
        val message = state.outcomes.getValue(SetupStep.CHATGPT).message
        assertTrue(message.contains("HTTP 401"))
        val report = SetupWizard.formatReport(state, environment, "t") { "z" }
        listOf(message, report, SetupStateCodec.encode(state)).forEach { assertFalse(it.contains("GEHEIM")) }
    }

    @Test
    fun `safe reasons cover network errors and unknown errors without their text`() {
        val refused = java.net.ConnectException("failed").apply { initCause(java.net.ConnectException("Connection refused")) }
        assertTrue(SetupErrors.safeReason("X", refused).contains("abgewiesen"))
        val odd = SetupErrors.safeReason("X", org.json.JSONException("GEHEIM"))
        assertTrue(odd.contains("JSONException"))
        assertFalse(odd.contains("GEHEIM"))
    }

    @Test
    fun `skipping is never success and takes dependents along`() {
        val state = SetupWizard.skip(SetupState(), SetupStep.LOCAL_SERVER, 5)
        listOf(SetupStep.LOCAL_SERVER, SetupStep.LOCAL_MODEL, SetupStep.LOCAL_CHAT).forEach {
            assertEquals(SetupStatus.SKIPPED, state.statusOf(it))
        }
        assertEquals(0, state.doneCount)
    }

    @Test
    fun `report and device stage cannot be skipped`() {
        val state = SetupState()
        assertEquals(state, SetupWizard.skip(state, SetupStep.REPORT, 1))
        assertEquals(state, SetupWizard.skip(state, SetupStep.APP_DEVICE, 1))
    }

    @Test
    fun `repeating a stage clears what builds on it`() {
        val before = ok(SetupStep.LOCAL_SERVER, SetupStep.LOCAL_MODEL, SetupStep.LOCAL_CHAT, SetupStep.AUTOMATE, base = SetupState(chosenModel = "m"))
        fake.scan = LocalScan(ProbeResult.REFUSED, null, emptyList(), "weg")
        val after = run(SetupStep.LOCAL_SERVER, before).state
        assertEquals(SetupStatus.OPEN, after.statusOf(SetupStep.LOCAL_MODEL))
        assertEquals(SetupStatus.OPEN, after.statusOf(SetupStep.LOCAL_CHAT))
        assertEquals(SetupStatus.OK, after.statusOf(SetupStep.AUTOMATE))
        assertEquals("", after.chosenModel)
    }

    @Test
    fun `the report says what worked, what is missing and what was not tested`() {
        val state = run(
            SetupStep.CLAUDE,
            SetupWizard.skip(ok(SetupStep.APP_DEVICE), SetupStep.AUTOMATE, 2)
        ).state
        val text = SetupWizard.formatReport(state, environment, "JETZT") { "ZEIT" }
        assertTrue(text.contains("Build abc1234"))
        assertTrue(text.contains("1 von 10 Etappen erledigt"))
        assertTrue(text.contains("1. App und Gerät prüfen: erledigt (ZEIT)"))
        assertTrue(text.contains("Etappe 2 (Lokale KI finden): noch nicht geprüft."))
        assertTrue(text.contains("Etappe 7 (Automate prüfen): übersprungen."))
        assertTrue(text.contains("Etappe 8 (Claude verbinden): Für Anthropic · Claude fehlt noch ein API-Schlüssel."))
        assertFalse(text.contains("Etappe 10"))
        SetupWizard.NOT_BUILT.forEach { assertTrue(it, text.contains(it)) }
    }

    @Test
    fun `sending the report hands over the text and claims no delivery`() {
        val outcome = run(SetupStep.REPORT).state.outcomes.getValue(SetupStep.REPORT)
        assertTrue(fake.shared!!.startsWith("LARA Einrichtungsbericht · JETZT"))
        assertEquals(SetupStatus.OK, outcome.status)
        assertTrue(outcome.message.contains("kann LARA nicht sehen"))
    }

    @Test
    fun `a share sheet that cannot open is a failure`() {
        fake.shareResult = false
        assertEquals(SetupStatus.FAILED, run(SetupStep.REPORT).state.statusOf(SetupStep.REPORT))
    }

    @Test
    fun `progress survives a restart`() {
        val state = SetupState(
            outcomes = mapOf(
                SetupStep.APP_DEVICE to StepOutcome(SetupStatus.OK, "gut", 7L),
                SetupStep.CLAUDE to StepOutcome(SetupStatus.NEEDS_USER, "Schlüssel", 8L)
            ),
            localModels = listOf("a", "b"),
            chosenModel = "a"
        )
        assertEquals(state, SetupStateCodec.decode(SetupStateCodec.encode(state)))
    }

    @Test
    fun `damaged or foreign data starts fresh and unknown entries are ignored`() {
        listOf(null, "", "kein json", "[1,2]").forEach { assertEquals(SetupState(), SetupStateCodec.decode(it)) }
        val mixed = """{"outcomes":{"GIBT_ES_NICHT":{"status":"OK"},"AUTOMATE":{"status":"WAS","message":"x"},"CLAUDE":{"status":"OK","message":"m","at":3}}}"""
        assertEquals(mapOf(SetupStep.CLAUDE to StepOutcome(SetupStatus.OK, "m", 3L)), SetupStateCodec.decode(mixed).outcomes)
    }

    private fun scan(body: MockResponse): LocalScan {
        val server = MockWebServer()
        server.enqueue(body)
        server.start()
        try {
            val result = LocalModelScan(OkHttpProbeTransport()).scan(server.url("/").toString())
            assertEquals("/v1/models", server.takeRequest().path)
            return result
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `scan reads model names over a real socket`() {
        val result = scan(MockResponse().setBody("""{"data":[{"id":"qwen"},{"id":"  "},{"id":7},{"x":1},{"id":"llama"}]}"""))
        assertEquals(ProbeResult.OK, result.result)
        assertEquals(listOf("qwen", "llama"), result.models)
    }

    @Test
    fun `scan rejects anything that is not a model list`() {
        assertEquals(ProbeResult.BAD_RESPONSE, scan(MockResponse().setBody("<html>")).result)
        assertEquals(ProbeResult.BAD_RESPONSE, scan(MockResponse().setBody("""{"models":[]}""")).result)
        assertEquals(ProbeResult.BAD_RESPONSE, scan(MockResponse().setBody("""{"data":[]}""")).result)
    }

    @Test
    fun `scan reports the status code and drops the server text`() {
        val result = scan(MockResponse().setResponseCode(404).setBody("GEHEIM"))
        assertEquals(ProbeResult.HTTP_ERROR, result.result)
        assertEquals(404, result.httpCode)
        assertFalse(result.message.contains("GEHEIM"))
    }

    @Test
    fun `scan reports a closed port as refused`() {
        val port = ServerSocket(0).use { it.localPort }
        val result = LocalModelScan(OkHttpProbeTransport()).scan("http://127.0.0.1:$port")
        assertEquals(ProbeResult.REFUSED, result.result)
        assertTrue(result.models.isEmpty())
    }
}

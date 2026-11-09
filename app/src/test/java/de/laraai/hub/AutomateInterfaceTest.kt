package de.laraai.hub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AutomateInterfaceTest {
    private val action = AutomateStartInterface.ACTION
    private val settings = FakeSettings()
    private var now = 1_000_000L
    private val gate = AutomateGate(settings) { now }

    private fun request(provider: String?, wanted: String? = null): AutomateRequest =
        (AutomateStartInterface.parse(action, provider, wanted) as AutomateDecision.Accepted).request

    private val accepted = AutomateDecision.Accepted(AutomateRequest(AiProvider.OLLAMA, AutomateAction.CHAT))

    // Receiving

    @Test
    fun `interface names are the documented ones`() {
        assertEquals("de.laraai.hub.action.START_FLOW", AutomateStartInterface.ACTION)
        assertEquals("anbieter", AutomateStartInterface.EXTRA_PROVIDER)
        assertEquals("aktion", AutomateStartInterface.EXTRA_ACTION)
    }

    @Test
    fun `manifest receives the documented action`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val filter = Regex(
            """<intent-filter>\s*<action android:name="de\.laraai\.hub\.action\.START_FLOW" />\s*""" +
                """<category android:name="android\.intent\.category\.DEFAULT" />\s*</intent-filter>"""
        )
        assertTrue(filter.containsMatchIn(manifest))
    }

    @Test
    fun `normal app starts are not Automate requests`() {
        assertEquals(AutomateDecision.NotForAutomate, AutomateStartInterface.parse(null, "ollama", null))
        assertEquals(AutomateDecision.NotForAutomate, AutomateStartInterface.parse("android.intent.action.MAIN", "ollama", null))
    }

    @Test
    fun `the action Automate itself listens to is not ours`() {
        assertEquals(
            AutomateDecision.NotForAutomate,
            AutomateStartInterface.parse("com.llamalab.automate.intent.action.START_FLOW", "ollama", null)
        )
    }

    @Test
    fun `without extras the chat opens with the current provider`() {
        assertEquals(AutomateRequest(null, AutomateAction.CHAT), request(null))
        assertEquals(AutomateRequest(null, AutomateAction.CHAT), request("  ", " "))
    }

    // Provider choice

    @Test
    fun `the four agreed provider names are understood`() {
        assertEquals(AiProvider.OLLAMA, request("ollama").provider)
        assertEquals(AiProvider.ANTHROPIC, request("claude").provider)
        assertEquals(AiProvider.OPENAI, request("chatgpt").provider)
        assertEquals(AiProvider.FREELLMAPI, request("freellmapi").provider)
    }

    @Test
    fun `spelling is tolerated`() {
        assertEquals(AiProvider.OPENAI, request(" ChatGPT ").provider)
    }

    @Test
    fun `anything else is rejected without repeating the input`() {
        for (name in listOf("openai", "anthropic", "gemini", "custom", "http://evil.example", "sk-abc")) {
            val decision = AutomateStartInterface.parse(action, name, null) as AutomateDecision.Rejected
            assertFalse(decision.reason, decision.reason.contains(name))
        }
    }

    @Test
    fun `actions are chat and connection test`() {
        assertEquals(AutomateAction.CHAT, request("ollama", "chat").action)
        assertEquals(AutomateAction.CONNECTION_TEST, request("ollama", "verbindungstest").action)
        assertEquals(AutomateAction.CONNECTION_TEST, request("ollama", " VerbindungsTest ").action)
    }

    @Test
    fun `unknown action is rejected`() {
        val decision = AutomateStartInterface.parse(action, "ollama", "senden") as AutomateDecision.Rejected
        assertFalse(decision.reason.contains("senden"))
    }

    // Confirmation

    private fun needs(provider: String?, current: AiProvider) =
        AutomateStartInterface.needsConfirmation(request(provider), current)

    @Test
    fun `paid providers always need confirmation`() {
        assertTrue(needs("claude", AiProvider.OLLAMA))
        assertTrue(needs("chatgpt", AiProvider.OLLAMA))
        assertTrue(needs("claude", AiProvider.ANTHROPIC))
    }

    @Test
    fun `free providers do not`() {
        assertFalse(needs("ollama", AiProvider.ANTHROPIC))
        assertFalse(needs("freellmapi", AiProvider.OPENAI))
    }

    @Test
    fun `without a provider the current one decides`() {
        assertFalse(needs(null, AiProvider.OLLAMA))
        assertFalse(needs(null, AiProvider.FREELLMAPI))
        AiProvider.entries.filter { it != AiProvider.OLLAMA && it != AiProvider.FREELLMAPI }
            .forEach { assertTrue(it.id, needs(null, it)) }
    }

    @Test
    fun `no request can carry address model prompt or key`() {
        val fields = AutomateRequest::class.java.declaredFields.map { it.name }.filterNot { it.startsWith("$") }.toSet()
        assertEquals(setOf("provider", "action"), fields)
    }

    // Rate limit

    @Test
    fun `one start per minute from Automate`() {
        assertEquals(accepted, gate.screen(accepted))
        now += AutomateGate.MIN_INTERVAL_MS - 1
        assertTrue(gate.screen(accepted) is AutomateDecision.Rejected)
        now += 1
        assertEquals(accepted, gate.screen(accepted))
    }

    @Test
    fun `a blocked start does not extend the wait`() {
        gate.screen(accepted)
        now += AutomateGate.MIN_INTERVAL_MS - 1
        gate.screen(accepted)
        now += 1
        assertEquals(accepted, gate.screen(accepted))
    }

    @Test
    fun `invalid requests do not use up the minute`() {
        val invalid = AutomateDecision.Rejected("x")
        assertEquals(invalid, gate.screen(invalid))
        assertEquals(AutomateDecision.NotForAutomate, gate.screen(AutomateDecision.NotForAutomate))
        assertEquals(accepted, gate.screen(accepted))
    }

    @Test
    fun `the limit survives an app restart`() {
        gate.screen(accepted)
        val afterRestart = AutomateGate(settings) { now }
        assertTrue(afterRestart.screen(accepted) is AutomateDecision.Rejected)
    }

    @Test
    fun `a clock set backwards does not block`() {
        gate.screen(accepted)
        now -= 500_000L
        assertEquals(accepted, gate.screen(accepted))
    }

    @Test
    fun `autostart fires once however often the screen is rebuilt`() {
        assertEquals(AutostartDecision.START, gate.autostart())
        repeat(5) {
            now += AutomateGate.MIN_INTERVAL_MS * 2
            assertEquals(AutostartDecision.ALREADY_DONE, gate.autostart())
        }
    }

    @Test
    fun `autostart after a quick app restart is limited`() {
        assertEquals(AutostartDecision.START, gate.autostart())
        val restarted = AutomateGate(settings) { now }
        assertEquals(AutostartDecision.RATE_LIMITED, restarted.autostart())
        assertEquals(AutostartDecision.ALREADY_DONE, restarted.autostart())
    }

    @Test
    fun `autostart runs again a minute after the last one`() {
        assertEquals(AutostartDecision.START, gate.autostart())
        now += AutomateGate.MIN_INTERVAL_MS
        assertEquals(AutostartDecision.START, AutomateGate(settings) { now }.autostart())
    }

    @Test
    fun `both directions are counted separately`() {
        assertEquals(AutostartDecision.START, gate.autostart())
        assertEquals(accepted, gate.screen(accepted))
    }

    @Test
    fun `settings hold only timestamps`() {
        gate.autostart()
        gate.screen(accepted)
        assertTrue(settings.values.values.all { it.toLongOrNull() != null })
        assertNull(settings.get("unbekannt"))
    }
}

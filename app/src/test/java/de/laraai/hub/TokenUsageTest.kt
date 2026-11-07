package de.laraai.hub

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TokenUsageTest {
    private fun parse(kind: String, json: String) = UsageParser.parse(kind, "p", "m", JSONObject(json), 1L)

    @Test
    fun openAiCompatibleUsage() {
        val u = parse("openai", """{"usage":{"prompt_tokens":12,"completion_tokens":34}}""")
        assertEquals(12, u.inputTokens); assertEquals(34, u.outputTokens); assertEquals("api", u.source); assertEquals(46, u.total)
    }

    @Test
    fun ollamaNativeUsage() {
        val u = parse("ollama", """{"prompt_eval_count":26,"eval_count":298}""")
        assertEquals(26, u.inputTokens); assertEquals(298, u.outputTokens); assertEquals("api", u.source)
    }

    @Test
    fun anthropicUsage() {
        val u = parse("anthropic", """{"usage":{"input_tokens":5,"output_tokens":7}}""")
        assertEquals(5, u.inputTokens); assertEquals(7, u.outputTokens)
    }

    @Test
    fun geminiUsage() {
        val u = parse("gemini", """{"usageMetadata":{"promptTokenCount":3,"candidatesTokenCount":4}}""")
        assertEquals(3, u.inputTokens); assertEquals(4, u.outputTokens)
    }

    @Test
    fun missingUsageIsUnknownNotZero() {
        val u = parse("openai", """{"choices":[]}""")
        assertNull(u.inputTokens); assertNull(u.outputTokens); assertEquals("unknown", u.source); assertNull(u.total)
    }

    @Test
    fun negativeOrNullValuesAreIgnored() {
        assertEquals("unknown", parse("ollama", """{"prompt_eval_count":-1}""").source)
        assertEquals("unknown", parse("openai", """{"usage":{"prompt_tokens":null}}""").source)
    }

    @Test
    fun gaugeThresholds() {
        assertEquals("Normal", ContextGauge.read(690, 1000).status)
        assertEquals("Beobachten", ContextGauge.read(700, 1000).status)
        assertEquals("Übergabe vorbereiten", ContextGauge.read(800, 1000).status)
        assertEquals("Wechsel einleiten", ContextGauge.read(900, 1000).status)
        assertEquals(64.1, ContextGauge.read(82000, 128000).percent!!, 0.01)
        assertEquals(46000, ContextGauge.read(82000, 128000).remaining)
    }

    @Test
    fun gaugeUnknownWithoutWindowOrUsage() {
        assertEquals("unbekannt", ContextGauge.read(null, 1000).status)
        assertEquals("unbekannt", ContextGauge.read(10, null).status)
    }

    @Test
    fun totalsSkipUnknown() {
        val t = UsageLog.totals(listOf(
            TokenUsage("p", "m", 10, 2, "api", 1), TokenUsage("p", "m", null, null, "unknown", 2)))
        assertEquals(UsageLog.Totals(2, 1, 10, 2), t)
    }

    @Test
    fun coreJobsAreParsed() {
        val jobs = CoreClient.parseJobs(JSONObject("""{"jobs":[{"title":"Dateiverwaltung","overall_percent":46.0,
            "state":"aktiv","current_step":{"id":"a","title":"Code"},"next_step":null,"active_model":null}]}"""))
        assertEquals(1, jobs.size)
        assertEquals("Code", jobs[0].currentStep); assertNull(jobs[0].nextStep); assertNull(jobs[0].activeModel)
        assertEquals(46.0, jobs[0].percent, 0.0)
    }
}

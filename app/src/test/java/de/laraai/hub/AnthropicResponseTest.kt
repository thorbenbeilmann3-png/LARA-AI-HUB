package de.laraai.hub

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AnthropicResponseTest {
    private fun chat(json: String) = AnthropicResponse.toChatText(JSONObject(json))

    @Test
    fun normalAnswer() {
        assertEquals("Hallo Thorben!", chat("""{"content":[{"type":"text","text":"Hallo Thorben!"}],"stop_reason":"end_turn"}"""))
    }

    @Test
    fun multipleTextBlocksAreJoined() {
        assertEquals("Teil 1\nTeil 2", chat("""{"content":[{"type":"text","text":"Teil 1"},{"type":"text","text":"Teil 2"}],"stop_reason":"end_turn"}"""))
    }

    @Test
    fun thinkingAndOtherBlocksBeforeTextAreSkipped() {
        val json = """{"content":[
            {"type":"thinking","thinking":"","signature":"abc"},
            {"type":"redacted_thinking","data":"xyz"},
            {"type":"unknown_future_block","text":"darf nicht erscheinen"},
            {"type":"text","text":"Antwort"}],"stop_reason":"end_turn"}"""
        assertEquals("Antwort", chat(json))
    }

    @Test
    fun refusalBecomesDistinctError() {
        try {
            chat("""{"content":[],"stop_reason":"refusal","stop_details":{"type":"refusal","category":"cyber","explanation":"Nicht erlaubt"}}""")
            fail("Ablehnung muss als Fehler gemeldet werden")
        } catch (e: IllegalStateException) {
            assertEquals("Claude hat die Anfrage abgelehnt (Kategorie: cyber): Nicht erlaubt", e.message)
        }
    }

    @Test
    fun refusalWithoutDetails() {
        try {
            chat("""{"content":[],"stop_reason":"refusal","stop_details":null}""")
            fail()
        } catch (e: IllegalStateException) {
            assertEquals("Claude hat die Anfrage abgelehnt.", e.message)
        }
    }

    @Test
    fun truncatedAnswerKeepsTextAndAddsNotice() {
        val text = chat("""{"content":[{"type":"text","text":"Halbe Antw"}],"stop_reason":"max_tokens"}""")
        assertTrue(text.startsWith("Halbe Antw"))
        assertTrue(text.contains("Längenbegrenzung abgeschnitten"))
    }

    @Test
    fun contextWindowExceededIsReported() {
        assertTrue(chat("""{"content":[{"type":"text","text":"x"}],"stop_reason":"model_context_window_exceeded"}""").contains("Kontextfenster"))
    }

    @Test
    fun truncationWithoutTextShowsOnlyNotice() {
        val text = chat("""{"content":[{"type":"thinking","thinking":""}],"stop_reason":"max_tokens"}""")
        assertTrue(text.startsWith("⚠"))
    }

    @Test
    fun emptyAnswerIsMarked() {
        assertEquals("Leere Antwort", chat("""{"content":[],"stop_reason":"end_turn"}"""))
    }
}

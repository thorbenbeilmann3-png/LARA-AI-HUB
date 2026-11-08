package de.laraai.hub

import org.json.JSONObject

/**
 * Interprets an Anthropic Messages API response.
 *
 * - Collects the text of every block with `type == "text"`; `thinking`,
 *   `redacted_thinking` and other block types are skipped (they carry no answer text).
 * - Evaluates `stop_reason` (values per the official API reference: end_turn,
 *   max_tokens, stop_sequence, tool_use, pause_turn, refusal,
 *   model_context_window_exceeded). A refusal can arrive with HTTP 200 and empty
 *   content; `stop_details` then may carry a category and an explanation.
 */
internal object AnthropicResponse {
    sealed class Outcome {
        data class Answer(val text: String, val notice: String?) : Outcome()
        data class Refused(val category: String?, val explanation: String?) : Outcome()
    }

    fun parse(json: JSONObject): Outcome {
        val stopReason = json.optString("stop_reason", "")
        if (stopReason == "refusal") {
            val details = json.optJSONObject("stop_details")
            return Outcome.Refused(
                details?.optStringOrNull("category"),
                details?.optStringOrNull("explanation")
            )
        }
        val content = json.optJSONArray("content")
        val text = buildList {
            if (content != null) {
                for (i in 0 until content.length()) {
                    val block = content.optJSONObject(i) ?: continue
                    if (block.optString("type") != "text") continue
                    val value = block.optString("text", "")
                    if (value.isNotBlank()) add(value)
                }
            }
        }.joinToString("\n")
        val notice = when (stopReason) {
            "max_tokens" -> "Die Antwort wurde wegen der Längenbegrenzung abgeschnitten."
            "model_context_window_exceeded" -> "Die Antwort wurde abgebrochen, weil das Kontextfenster des Modells voll ist."
            "pause_turn" -> "Das Modell hat die Antwort unterbrochen; sie ist möglicherweise unvollständig."
            "tool_use" -> "Das Modell wollte ein Werkzeug verwenden; das unterstützt LARA in diesem Chat noch nicht."
            else -> null
        }
        return Outcome.Answer(text.ifBlank { if (notice == null) "Leere Antwort" else "" }, notice)
    }

    /** Text shown in the chat: answer plus a clearly marked notice, or an error for refusals. */
    fun toChatText(json: JSONObject): String = when (val outcome = parse(json)) {
        is Outcome.Refused -> error(
            "Claude hat die Anfrage abgelehnt" +
                (outcome.category?.let { " (Kategorie: $it)" } ?: "") +
                (outcome.explanation?.let { ": $it" } ?: ".")
        )
        is Outcome.Answer -> listOfNotNull(
            outcome.text.takeIf { it.isNotBlank() },
            outcome.notice?.let { "⚠ $it" }
        ).joinToString("\n\n").ifBlank { "Leere Antwort" }
    }

    private fun JSONObject.optStringOrNull(name: String): String? =
        if (!has(name) || isNull(name)) null else optString(name).takeIf { it.isNotBlank() }
}

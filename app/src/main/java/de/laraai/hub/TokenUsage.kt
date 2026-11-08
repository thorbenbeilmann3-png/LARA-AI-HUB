package de.laraai.hub

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Token counts of one model request. [source] is "api" when the provider reported
 * the numbers, "unknown" when the response contained none. Nothing is estimated.
 */
internal data class TokenUsage(
    val provider: String,
    val model: String,
    val inputTokens: Int?,
    val outputTokens: Int?,
    val source: String,
    val timeMs: Long
) {
    val total: Int? get() = if (inputTokens == null) null else inputTokens + (outputTokens ?: 0)
}

internal object UsageParser {
    /**
     * Reads the usage fields of a chat response.
     * - "openai": usage.prompt_tokens / usage.completion_tokens (OpenAI-compatible servers)
     * - "ollama": prompt_eval_count / eval_count (Ollama /api/chat, per Ollama docs)
     * - "anthropic": usage.input_tokens / usage.output_tokens (Anthropic Messages API)
     * - "gemini": usageMetadata.promptTokenCount / candidatesTokenCount
     * Missing fields give source "unknown" with null counts.
     */
    fun parse(kind: String, provider: String, model: String, json: JSONObject, nowMs: Long): TokenUsage {
        val (input, output) = when (kind) {
            "openai" -> json.optJSONObject("usage").let { it.intOrNull("prompt_tokens") to it.intOrNull("completion_tokens") }
            "ollama" -> json.intOrNull("prompt_eval_count") to json.intOrNull("eval_count")
            "anthropic" -> json.optJSONObject("usage").let { it.intOrNull("input_tokens") to it.intOrNull("output_tokens") }
            "gemini" -> json.optJSONObject("usageMetadata").let { it.intOrNull("promptTokenCount") to it.intOrNull("candidatesTokenCount") }
            else -> null to null
        }
        return if (input == null) {
            TokenUsage(provider, model, null, null, "unknown", nowMs)
        } else {
            TokenUsage(provider, model, input, output, "api", nowMs)
        }
    }

    private fun JSONObject?.intOrNull(name: String): Int? {
        if (this == null || !has(name) || isNull(name)) return null
        val value = optLong(name, -1L)
        return if (value in 0L..Int.MAX_VALUE.toLong()) value.toInt() else null
    }
}

/** Context usage of the latest request against a known window; thresholds like the Core. */
internal object ContextGauge {
    data class Reading(val percent: Double?, val remaining: Int?, val status: String)

    fun read(used: Int?, window: Int?, watch: Double = 70.0, prepare: Double = 80.0, switch: Double = 90.0): Reading {
        if (used == null || window == null || window <= 0) return Reading(null, null, "unbekannt")
        val percent = Math.round(1000.0 * used / window) / 10.0
        val status = when {
            percent >= switch -> "Wechsel einleiten"
            percent >= prepare -> "Übergabe vorbereiten"
            percent >= watch -> "Beobachten"
            else -> "Normal"
        }
        return Reading(percent, maxOf(0, window - used), status)
    }
}

/** Last requests on this device (max [MAX_ENTRIES]); no prompts or answers, only counts. */
internal object UsageLog {
    private const val PREFS = "lara_usage"
    private const val KEY = "entries"
    const val MAX_ENTRIES = 100

    fun add(context: Context, usage: TokenUsage) {
        val entries = load(context).toMutableList().apply { add(usage) }.takeLast(MAX_ENTRIES)
        val array = JSONArray()
        entries.forEach {
            array.put(JSONObject()
                .put("provider", it.provider).put("model", it.model)
                .put("input", it.inputTokens ?: JSONObject.NULL).put("output", it.outputTokens ?: JSONObject.NULL)
                .put("source", it.source).put("time", it.timeMs))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).apply()
    }

    fun load(context: Context): List<TokenUsage> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                TokenUsage(
                    o.optString("provider"), o.optString("model"),
                    if (o.isNull("input")) null else o.optInt("input"),
                    if (o.isNull("output")) null else o.optInt("output"),
                    o.optString("source", "unknown"), o.optLong("time")
                )
            }
        }.getOrDefault(emptyList())
    }

    data class Totals(val requests: Int, val withoutCounts: Int, val input: Int, val output: Int)

    fun totals(entries: List<TokenUsage>): Totals {
        val known = entries.filter { it.source != "unknown" }
        return Totals(entries.size, entries.size - known.size,
            known.sumOf { it.inputTokens ?: 0 }, known.sumOf { it.outputTokens ?: 0 })
    }
}

package de.laraai.hub

/** One message sent to a model. role is "user" or "assistant". */
internal data class ChatTurn(val role: String, val content: String)

/**
 * Builds the conversation context sent with each chat request.
 *
 * Pure Kotlin (no Android or JSON types) so it can be unit-tested on the JVM.
 * Rules:
 * - keeps only the most recent turns, within [maxTurns] and [maxChars]
 *   (the current prompt always counts against the budget and is always kept),
 * - merges consecutive turns of the same role (e.g. a question whose answer failed),
 * - starts with a "user" turn and ends with the current prompt as "user",
 *   which Anthropic and Gemini require (strict user/assistant alternation).
 */
internal object ChatHistory {
    const val DEFAULT_MAX_TURNS = 20
    const val DEFAULT_MAX_CHARS = 8000

    fun build(
        previous: List<ChatTurn>,
        prompt: String,
        maxTurns: Int = DEFAULT_MAX_TURNS,
        maxChars: Int = DEFAULT_MAX_CHARS
    ): List<ChatTurn> {
        val current = ChatTurn("user", prompt)
        val cleaned = previous.filter {
            (it.role == "user" || it.role == "assistant") && it.content.isNotBlank()
        }
        var budget = maxChars - prompt.length
        val kept = ArrayDeque<ChatTurn>()
        for (turn in cleaned.asReversed()) {
            if (kept.size >= maxTurns - 1 || turn.content.length > budget) break
            kept.addFirst(turn)
            budget -= turn.content.length
        }
        val merged = mutableListOf<ChatTurn>()
        for (turn in kept + current) {
            val last = merged.lastOrNull()
            if (last != null && last.role == turn.role) {
                merged[merged.lastIndex] = ChatTurn(last.role, last.content + "\n\n" + turn.content)
            } else {
                merged.add(turn)
            }
        }
        while (merged.isNotEmpty() && merged.first().role != "user") merged.removeAt(0)
        return merged
    }

    /** Maps stored chat lines ("Du" / "LARA / <model>") to model roles. */
    fun fromStoredRole(role: String): String? = when {
        role == "Du" -> "user"
        role.startsWith("LARA") -> "assistant"
        else -> null
    }
}

/** Mirrors res/xml/network_security_config.xml: plain HTTP only to this device. */
internal object CleartextPolicy {
    fun isAllowed(url: String): Boolean {
        val value = url.trim()
        if (!value.startsWith("http://", ignoreCase = true)) return true
        val host = runCatching { java.net.URI(value).host?.lowercase() }.getOrNull() ?: return false
        return host == "127.0.0.1" || host == "localhost"
    }
}

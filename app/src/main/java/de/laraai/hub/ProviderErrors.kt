package de.laraai.hub

/**
 * Turns low-level connection errors into clear, provider-specific German messages
 * (pure Kotlin, unit-tested). The original detail is kept in short form so nothing
 * is hidden.
 */
internal object ProviderErrors {
    private val httpCode = Regex("""^HTTP (\d{3})""")

    fun describe(providerLabel: String, error: Throwable): String {
        val raw = error.message.orEmpty().trim()
        val type = error.javaClass.simpleName
        val code = httpCode.find(raw)?.groupValues?.get(1)?.toIntOrNull()
        val reason = when {
            code == 401 -> "API-Schlüssel ungültig oder abgelaufen. Bitte unter Tools neu speichern."
            code == 403 -> "Zugriff verweigert (Berechtigung, Region oder Abrechnung beim Anbieter prüfen)."
            code == 404 -> "Modell oder Adresse nicht gefunden. Bitte unter Tools ein anderes Modell wählen."
            code == 408 -> "Zeitüberschreitung beim Anbieter."
            code == 429 -> "Kontingent oder Anfragelimit erreicht. Später erneut versuchen oder Limit beim Anbieter prüfen."
            code != null && code in 500..599 -> "Störung beim Anbieter (HTTP $code). Später erneut versuchen."
            code != null && code in 400..499 -> "Anfrage abgelehnt (HTTP $code)."
            type == "UnknownHostException" -> "Adresse nicht auflösbar: keine Internetverbindung oder falsche Adresse."
            type == "SocketTimeoutException" || type == "InterruptedIOException" -> "Zeitüberschreitung: der Server antwortet nicht rechtzeitig."
            type == "ConnectException" -> "Server nicht erreichbar. Läuft er, und stimmt der Port?"
            type == "SSLHandshakeException" || type == "SSLException" -> "Sichere Verbindung (HTTPS) fehlgeschlagen."
            type == "UnknownServiceException" -> "Unverschlüsselte Verbindung blockiert. Für andere Geräte HTTPS verwenden."
            raw.isNotEmpty() -> null // already a readable message from LARA itself
            else -> "Unbekannter Fehler ($type)."
        }
        return if (reason == null) "$providerLabel: $raw"
        else "$providerLabel: $reason" + if (code != null && raw.length > 9) " – Details: ${raw.take(160)}" else ""
    }
}

/** Default model choice for lists from /v1/models that also contain non-chat models. */
internal object ModelChoice {
    private val nonChat = listOf(
        "embedding", "embed", "whisper", "tts", "dall-e", "moderation", "audio",
        "realtime", "image", "transcribe", "search", "davinci", "babbage", "rerank"
    )

    fun isLikelyChatModel(id: String): Boolean {
        val lower = id.lowercase()
        return nonChat.none { lower.contains(it) }
    }

    /** First likely chat model, otherwise the first entry, otherwise "". */
    fun pickDefault(models: List<String>): String =
        models.firstOrNull { isLikelyChatModel(it) } ?: models.firstOrNull().orEmpty()
}

package de.laraai.hub

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import java.net.UnknownServiceException
import java.util.concurrent.TimeUnit

/** What was actually observed for one HTTP check. */
internal enum class ProbeResult(val label: String) {
    OK("OK"),
    AUTH_REQUIRED("Anmeldung verlangt"),
    HTTP_ERROR("HTTP-Fehler"),
    BAD_RESPONSE("Unerwartete Antwort"),
    REFUSED("Abgewiesen"),
    CONNECT_TIMEOUT("Keine Verbindung"),
    READ_TIMEOUT("Keine Antwort"),
    UNKNOWN_HOST("Adresse unbekannt"),
    BLOCKED("Von LARA blockiert"),
    INVALID_ADDRESS("Ungültige Adresse"),
    ERROR("Fehler")
}

internal data class ProbeStep(
    val title: String,
    val url: String,
    val result: ProbeResult,
    val httpCode: Int?,
    val millis: Long,
    val message: String,
    val detail: String? = null
)

internal data class ProbeReport(
    val steps: List<ProbeStep>,
    val ollamaVersion: String?,
    val models: List<String>,
    val expectedModel: String,
    val expectedModelFound: Boolean
) {
    /** True only after a server answered GET /api/version with a valid version to this process. */
    val ollamaReached: Boolean get() = ollamaVersion != null

    val verdict: String
        get() = when {
            ollamaReached && expectedModelFound ->
                "Ollama ist aus diesem App-Prozess erreichbar, das erwartete Modell ist vorhanden."
            ollamaReached -> "Ollama ist aus diesem App-Prozess erreichbar, das erwartete Modell fehlt."
            else -> "Ollama ist aus diesem App-Prozess NICHT nachweislich erreichbar."
        }
}

/** Identifies the process that ran the test. Contains no secrets. */
internal data class ProbeEnvironment(
    val packageName: String,
    val buildSha: String,
    val versionName: String,
    val uid: Int,
    val pid: Int,
    val sdkInt: Int,
    val androidRelease: String,
    val device: String
)

internal data class ProbeHttpResponse(val code: Int, val body: String?)

/** Sends GET without credentials. The body is read only when [readBody] is true. */
internal fun interface ProbeTransport {
    fun get(url: String, readBody: Boolean): ProbeHttpResponse
}

/** Uses OkHttp like the real LARA requests, but with short limits and without redirects. */
internal class OkHttpProbeTransport(
    connectMs: Long = 3_000,
    readMs: Long = 6_000,
    callMs: Long = 10_000
) : ProbeTransport {
    private val client = OkHttpClient.Builder()
        .connectTimeout(connectMs, TimeUnit.MILLISECONDS)
        .readTimeout(readMs, TimeUnit.MILLISECONDS)
        .callTimeout(callMs, TimeUnit.MILLISECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    override fun get(url: String, readBody: Boolean): ProbeHttpResponse {
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/json, text/event-stream")
            .build()
        client.newCall(request).execute().use { response ->
            val body = if (readBody) response.peekBody(MAX_BODY_BYTES).string() else null
            return ProbeHttpResponse(response.code, body)
        }
    }

    private companion object {
        const val MAX_BODY_BYTES = 128L * 1024
    }
}

/**
 * Connection test "LARA -> Ollama / MCP endpoint", run inside the app process.
 *
 * It reports only what this process observed: it sends no credentials and no chat
 * message. A positive result proves reachability of these addresses from this app,
 * nothing about chat, MCP login, Python or the terminal. Every step is bounded by the
 * transport limits, so a frozen server cannot block the test for minutes.
 */
internal class AlpineProbe(
    private val transport: ProbeTransport,
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000 }
) {
    fun run(ollamaBaseUrl: String, mcpUrl: String, expectedModel: String): ProbeReport {
        val base = ollamaBaseUrl.trim().trimEnd('/')
        val wanted = expectedModel.trim()
        var version: String? = null
        var models = emptyList<String>()

        val versionStep = check("Ollama-Version", "$base/api/version", readBody = true) { response ->
            if (response.code != 200) {
                ProbeResult.HTTP_ERROR to httpErrorText(response.code)
            } else {
                val json = parseObject(response.body)
                val value = if (json == null || json.isNull("version")) "" else json.optString("version").trim()
                if (value.isEmpty()) {
                    ProbeResult.BAD_RESPONSE to "Auf dieser Adresse antwortet etwas, aber nicht wie Ollama (keine Versionsangabe)."
                } else {
                    version = value.take(64)
                    ProbeResult.OK to "Ollama antwortet · Version ${value.take(64)}"
                }
            }
        }

        val tagsStep = check("Ollama-Modelle", "$base/api/tags", readBody = true) { response ->
            val list = if (response.code == 200) parseObject(response.body)?.optJSONArray("models") else null
            when {
                response.code != 200 -> ProbeResult.HTTP_ERROR to httpErrorText(response.code)
                list == null -> ProbeResult.BAD_RESPONSE to "Die Antwort enthält keine Modellliste."
                else -> {
                    models = (0 until list.length()).mapNotNull { index ->
                        val item = list.optJSONObject(index)
                        item?.optString("name").orEmpty().ifBlank { item?.optString("model").orEmpty() }
                            .trim().takeIf { it.isNotEmpty() }
                    }.take(MAX_MODELS)
                    val mark = when {
                        wanted.isEmpty() -> ""
                        hasModel(models, wanted) -> " · $wanted gefunden"
                        else -> " · $wanted NICHT gefunden"
                    }
                    ProbeResult.OK to "${models.size} Modell(e)$mark"
                }
            }
        }

        val mcpStep = check("MCP-Endpunkt ohne Anmeldung", mcpUrl.trim(), readBody = false) { response ->
            when (response.code) {
                401 -> ProbeResult.AUTH_REQUIRED to "Ein Dienst antwortet und verlangt Anmeldung (HTTP 401). " +
                    "Das zeigt nur die Erreichbarkeit. Ob es der Alpine-MCP-Dienst ist und ob MCP funktioniert, ist damit nicht geprüft."
                in 200..299 -> ProbeResult.BAD_RESPONSE to "Antwort OHNE Anmeldung (HTTP ${response.code}). " +
                    "Erwartet war HTTP 401. Bitte die Absicherung des Dienstes prüfen."
                else -> ProbeResult.HTTP_ERROR to httpErrorText(response.code)
            }
        }

        return ProbeReport(
            steps = listOf(versionStep, tagsStep, mcpStep),
            ollamaVersion = version,
            models = models,
            expectedModel = wanted,
            expectedModelFound = wanted.isNotEmpty() && hasModel(models, wanted)
        )
    }

    private fun check(
        title: String,
        url: String,
        readBody: Boolean,
        interpret: (ProbeHttpResponse) -> Pair<ProbeResult, String>
    ): ProbeStep {
        addressProblem(url)?.let { (result, message) ->
            val shown = if (result == ProbeResult.BLOCKED) url else "(Adresse nicht übernommen)"
            return ProbeStep(title, shown, result, null, 0, message)
        }
        val started = nowMs()
        return try {
            val response = transport.get(url, readBody)
            val (result, message) = interpret(response)
            ProbeStep(title, url, result, response.code, nowMs() - started, message)
        } catch (e: Exception) {
            val result = classify(e)
            ProbeStep(title, url, result, null, nowMs() - started, explain(result), technicalDetail(e))
        }
    }

    private fun parseObject(body: String?): JSONObject? =
        runCatching { JSONObject(body.orEmpty()) }.getOrNull()

    private fun httpErrorText(code: Int): String =
        "HTTP $code: Etwas antwortet auf dieser Adresse, aber nicht wie erwartet. Adresse und Port prüfen."

    private fun technicalDetail(error: Throwable): String {
        val cause = error.cause?.let { " / ${it.javaClass.simpleName}: ${it.message.orEmpty().take(120)}" }.orEmpty()
        return "${error.javaClass.simpleName}: ${error.message.orEmpty().take(160)}$cause"
    }

    internal companion object {
        const val DEFAULT_OLLAMA_URL = "http://127.0.0.1:11434"
        const val DEFAULT_MCP_URL = "http://127.0.0.1:8765/mcp"
        const val DEFAULT_MODEL = "qwen2.5:0.5b"
        private const val MAX_MODELS = 200

        /**
         * Rejects addresses before any network access. Credentials or parameters in an
         * address could end up in the copied report, and neither endpoint needs them.
         */
        fun addressProblem(url: String): Pair<ProbeResult, String>? {
            val invalid = ProbeResult.INVALID_ADDRESS to "Bitte eine vollständige http://- oder https://-Adresse eingeben."
            if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) return invalid
            val uri = runCatching { URI(url) }.getOrNull() ?: return invalid
            if (uri.host.isNullOrBlank()) return invalid
            if (uri.userInfo != null || uri.query != null || uri.fragment != null) {
                return ProbeResult.INVALID_ADDRESS to "Die Adresse darf keine Zugangsdaten oder Parameter enthalten."
            }
            if (!CleartextPolicy.isAllowed(url)) {
                return ProbeResult.BLOCKED to "Unverschlüsseltes HTTP ist nur zu 127.0.0.1 und localhost erlaubt. Für andere Geräte HTTPS verwenden."
            }
            return null
        }

        /**
         * Exception types are what OkHttp raises on the JVM and on Android. A call timeout
         * ([InterruptedIOException]) cannot fire during connect because connect is limited
         * to a shorter time, so it also means "connected, but no complete answer".
         */
        fun classify(error: Throwable): ProbeResult = when {
            error is UnknownServiceException -> ProbeResult.BLOCKED
            error is UnknownHostException -> ProbeResult.UNKNOWN_HOST
            error is ConnectException -> ProbeResult.REFUSED
            error is SocketTimeoutException && error.message.orEmpty().contains("connect", ignoreCase = true) ->
                ProbeResult.CONNECT_TIMEOUT
            error is InterruptedIOException -> ProbeResult.READ_TIMEOUT
            error is IllegalArgumentException -> ProbeResult.INVALID_ADDRESS
            else -> ProbeResult.ERROR
        }

        fun explain(result: ProbeResult): String = when (result) {
            ProbeResult.REFUSED -> "Verbindung abgewiesen: Auf dieser Adresse lauscht aus Sicht dieser App nichts. " +
                "Läuft der Server noch (z. B. ollama serve), und stimmt der Port?"
            ProbeResult.CONNECT_TIMEOUT -> "Es kam keine Verbindung zustande (Zeitüberschreitung). " +
                "Mögliche Ursache: Netzwerkregel, Firewall- oder VPN-App."
            ProbeResult.READ_TIMEOUT -> "Verbindung steht, aber der Server antwortet nicht rechtzeitig. " +
                "Mögliche Ursache: Die Alpine-App ist im Hintergrund eingefroren oder das Gerät ist überlastet."
            ProbeResult.UNKNOWN_HOST -> "Die Adresse konnte nicht aufgelöst werden."
            ProbeResult.BLOCKED -> "Diese unverschlüsselte Verbindung ist in LARA nicht erlaubt."
            ProbeResult.INVALID_ADDRESS -> "Die Adresse ist ungültig."
            else -> "Unerwarteter Netzwerkfehler."
        }

        /** A missing tag means ":latest", as in Ollama's own model names. */
        fun hasModel(models: List<String>, expected: String): Boolean {
            fun normalize(name: String): String {
                val lower = name.trim().lowercase()
                return if (lower.contains(':')) lower else "$lower:latest"
            }
            val wanted = normalize(expected)
            return models.any { normalize(it) == wanted }
        }

        /** Plain text for copying. Contains no credentials, response bodies or tokens. */
        fun formatReport(report: ProbeReport, environment: ProbeEnvironment, timestamp: String): String = buildString {
            appendLine("LARA Verbindungstest · $timestamp")
            appendLine(
                "App: ${environment.packageName} ${environment.versionName} · Build ${environment.buildSha} · " +
                    "UID ${environment.uid} · PID ${environment.pid}"
            )
            appendLine("Gerät: ${environment.device} · Android ${environment.androidRelease} (API ${environment.sdkInt})")
            appendLine()
            appendLine("Ergebnis: ${report.verdict}")
            appendLine()
            report.steps.forEach { step ->
                appendLine("[${step.result.label}] ${step.title}")
                appendLine("  ${step.url}" + (step.httpCode?.let { " · HTTP $it" } ?: "") + " · ${step.millis} ms")
                appendLine("  ${step.message}")
                step.detail?.let { appendLine("  $it") }
            }
            appendLine()
            append(
                "Hinweis: Es wurden keine Zugangsdaten und keine Chat-Nachricht gesendet. Geprüft ist nur die " +
                    "Erreichbarkeit aus dieser App, nicht Chat, MCP-Anmeldung, Python oder Terminal."
            )
        }
    }
}

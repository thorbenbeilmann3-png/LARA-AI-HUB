package de.laraai.hub

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.Proxy
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
    CONNECT_FAILED("Verbindung verhindert"),
    CONNECT_TIMEOUT("Keine Verbindung"),
    READ_TIMEOUT("Keine Antwort"),
    TIMEOUT("Zeitüberschreitung"),
    UNKNOWN_HOST("Adresse unbekannt"),
    BLOCKED("Von LARA blockiert"),
    INVALID_ADDRESS("Ungültige Adresse"),
    ERROR("Fehler")
}

/** UNKNOWN: the list could not be read, or no model name was entered. */
internal enum class ModelState { FOUND, MISSING, UNKNOWN }

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
    val modelState: ModelState,
    val modelListRead: Boolean
) {
    /** True only when a valid version and a valid model list both reached this process. */
    val ollamaReached: Boolean get() = ollamaVersion != null && modelListRead

    val expectedModelFound: Boolean get() = modelState == ModelState.FOUND

    val verdict: String
        get() = when {
            ollamaVersion != null && !modelListRead ->
                "Ein Dienst antwortet auf /api/version, die Modellliste ließ sich aber nicht lesen. " +
                    "Dass es Ollama ist, ist damit nicht belegt."
            !ollamaReached -> "Ollama ist aus diesem App-Prozess NICHT nachweislich erreichbar."
            modelState == ModelState.FOUND ->
                "Ollama ist aus diesem App-Prozess erreichbar, das erwartete Modell ist vorhanden."
            modelState == ModelState.MISSING ->
                "Ollama ist aus diesem App-Prozess erreichbar, das erwartete Modell fehlt."
            else -> "Ollama ist aus diesem App-Prozess erreichbar. Es wurde kein Modell zum Prüfen angegeben."
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

/**
 * Uses OkHttp like the real LARA requests, but with short limits, without redirects and
 * without a system proxy, so that a positive result means the direct path works.
 */
internal class OkHttpProbeTransport(
    private val connectMs: Long = 3_000,
    private val readMs: Long = 6_000,
    private val callMs: Long = 10_000
) : ProbeTransport {
    // Built on first use, on the background thread: loading the trust store is slow.
    private val client by lazy {
        OkHttpClient.Builder()
            .proxy(Proxy.NO_PROXY)
            .connectTimeout(connectMs, TimeUnit.MILLISECONDS)
            .readTimeout(readMs, TimeUnit.MILLISECONDS)
            .callTimeout(callMs, TimeUnit.MILLISECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

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
        var models: List<String>? = null

        val versionStep = check("Ollama-Version", "$base$VERSION_PATH", VERSION_PATH, readBody = true) { response ->
            if (response.code != 200) {
                ProbeResult.HTTP_ERROR to httpErrorText(response.code)
            } else {
                val value = textField(parseObject(response.body), "version").orEmpty()
                if (VERSION_SHAPE.matches(value)) {
                    version = value.take(64)
                    ProbeResult.OK to "Antwort mit Versionsangabe ${value.take(64)}"
                } else {
                    ProbeResult.BAD_RESPONSE to "Auf dieser Adresse antwortet etwas, aber nicht wie Ollama (keine gültige Versionsangabe)."
                }
            }
        }

        val tagsStep = check("Ollama-Modelle", "$base$TAGS_PATH", TAGS_PATH, readBody = true) { response ->
            val list = if (response.code == 200) parseObject(response.body)?.optJSONArray("models") else null
            when {
                response.code != 200 -> ProbeResult.HTTP_ERROR to httpErrorText(response.code)
                list == null -> ProbeResult.BAD_RESPONSE to "Die Antwort enthält keine Modellliste."
                else -> {
                    val names = (0 until list.length()).mapNotNull { index ->
                        val item = list.optJSONObject(index)
                        textField(item, "name") ?: textField(item, "model")
                    }.take(MAX_MODELS)
                    models = names
                    val mark = when {
                        wanted.isEmpty() -> ""
                        hasModel(names, wanted) -> " · $wanted gefunden"
                        else -> " · $wanted NICHT gefunden"
                    }
                    ProbeResult.OK to "${names.size} Modell(e)$mark"
                }
            }
        }

        val mcpStep = check("MCP-Endpunkt ohne Anmeldung", mcpUrl.trim(), MCP_PATH, readBody = false) { response ->
            when (response.code) {
                401 -> ProbeResult.AUTH_REQUIRED to "Ein Dienst antwortet und verlangt Anmeldung (HTTP 401). " +
                    "Das zeigt nur die Erreichbarkeit. Ob es der Alpine-MCP-Dienst ist und ob MCP funktioniert, ist damit nicht geprüft."
                in 200..299 -> ProbeResult.BAD_RESPONSE to "Antwort OHNE Anmeldung (HTTP ${response.code}). " +
                    "Erwartet war HTTP 401. Bitte die Absicherung des Dienstes prüfen."
                else -> ProbeResult.HTTP_ERROR to httpErrorText(response.code)
            }
        }

        val names = models
        return ProbeReport(
            steps = listOf(versionStep, tagsStep, mcpStep),
            ollamaVersion = version,
            models = names.orEmpty(),
            expectedModel = wanted,
            modelState = when {
                names == null || wanted.isEmpty() -> ModelState.UNKNOWN
                hasModel(names, wanted) -> ModelState.FOUND
                else -> ModelState.MISSING
            },
            modelListRead = names != null
        )
    }

    private fun check(
        title: String,
        url: String,
        expectedPath: String,
        readBody: Boolean,
        interpret: (ProbeHttpResponse) -> Pair<ProbeResult, String>
    ): ProbeStep {
        addressProblem(url, expectedPath)?.let { (result, message) ->
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

    /** Only real JSON strings count. Android's optString would turn null into "null". */
    private fun textField(json: JSONObject?, key: String): String? =
        (json?.opt(key) as? String)?.trim()?.takeIf { it.isNotEmpty() }

    private fun httpErrorText(code: Int): String =
        "HTTP $code: Etwas antwortet auf dieser Adresse, aber nicht wie erwartet. Adresse und Port prüfen."

    /** Class and allow-listed error code only. Exception texts can quote what a server sent. */
    private fun technicalDetail(error: Throwable): String {
        val errno = ERRNO.find(chainText(error))?.value
        return error.javaClass.simpleName + (errno?.let { " ($it)" } ?: "")
    }

    internal companion object {
        const val DEFAULT_OLLAMA_URL = "http://127.0.0.1:11434"
        const val DEFAULT_MCP_URL = "http://127.0.0.1:8765/mcp"
        const val DEFAULT_MODEL = "qwen2.5:0.5b"
        private const val VERSION_PATH = "/api/version"
        private const val TAGS_PATH = "/api/tags"
        private const val MCP_PATH = "/mcp"
        private const val MAX_MODELS = 200
        private val VERSION_SHAPE = Regex("""\d+\.\d+(\.\d+)?([-+.][0-9A-Za-z.+-]*)?""")
        private val ERRNO = Regex("""\b(ECONNREFUSED|ECONNRESET|ECONNABORTED|EACCES|EPERM|ENETUNREACH|EHOSTUNREACH|ETIMEDOUT|EPIPE)\b""")

        /**
         * Rejects addresses before any network access. Only scheme, host, port and the
         * expected path are accepted, so a token cannot be sent or copied into the report
         * through the user name, a parameter or the path.
         */
        fun addressProblem(url: String, expectedPath: String): Pair<ProbeResult, String>? {
            val invalid = ProbeResult.INVALID_ADDRESS to "Bitte eine vollständige http://- oder https://-Adresse eingeben."
            if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) return invalid
            val uri = runCatching { URI(url) }.getOrNull() ?: return invalid
            if (uri.host.isNullOrBlank()) return invalid
            if (uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null || uri.rawPath != expectedPath) {
                return ProbeResult.INVALID_ADDRESS to "Erlaubt sind nur Protokoll, Rechner und Port, bei MCP zusätzlich /mcp. " +
                    "Zugangsdaten gehören nicht in die Adresse."
            }
            if (!CleartextPolicy.isAllowed(url)) {
                return ProbeResult.BLOCKED to "Unverschlüsseltes HTTP ist nur zu 127.0.0.1 und localhost erlaubt. Für andere Geräte HTTPS verwenden."
            }
            return null
        }

        fun ollamaAddressProblem(baseUrl: String): Pair<ProbeResult, String>? =
            addressProblem(baseUrl.trim().trimEnd('/') + VERSION_PATH, VERSION_PATH)

        fun mcpAddressProblem(url: String): Pair<ProbeResult, String>? = addressProblem(url.trim(), MCP_PATH)

        private fun chainText(error: Throwable): String =
            generateSequence(error) { it.cause }.take(6).joinToString(" | ") { it.message.orEmpty() }

        /**
         * Android raises ConnectException for many socket errors, so "refused" is only
         * reported when the error says so. A call timeout can fire at any stage and
         * therefore says nothing about whether a connection existed.
         */
        fun classify(error: Throwable): ProbeResult {
            val text = chainText(error)
            return when {
                error is UnknownServiceException && text.contains("CLEARTEXT", ignoreCase = true) -> ProbeResult.BLOCKED
                error is UnknownHostException -> ProbeResult.UNKNOWN_HOST
                error is ConnectException && (text.contains("ECONNREFUSED") || text.contains("Connection refused", ignoreCase = true)) ->
                    ProbeResult.REFUSED
                error is ConnectException || error is NoRouteToHostException -> ProbeResult.CONNECT_FAILED
                error is SocketTimeoutException && error.message.orEmpty().contains("connect", ignoreCase = true) ->
                    ProbeResult.CONNECT_TIMEOUT
                error is SocketTimeoutException -> ProbeResult.READ_TIMEOUT
                error is InterruptedIOException -> ProbeResult.TIMEOUT
                error is IllegalArgumentException -> ProbeResult.INVALID_ADDRESS
                else -> ProbeResult.ERROR
            }
        }

        fun explain(result: ProbeResult): String = when (result) {
            ProbeResult.REFUSED -> "Verbindung abgewiesen: Auf dieser Adresse lauscht aus Sicht dieser App nichts. " +
                "Läuft der Server noch (z. B. ollama serve), und stimmt der Port?"
            ProbeResult.CONNECT_FAILED -> "Android konnte keine Verbindung aufbauen (keine Route oder keine Berechtigung). " +
                "Mögliche Ursache: Firewall- oder VPN-App, Netzwerkregel."
            ProbeResult.CONNECT_TIMEOUT -> "Es kam keine Verbindung zustande (Zeitüberschreitung beim Verbinden). " +
                "Mögliche Ursache: Netzwerkregel, Firewall- oder VPN-App."
            ProbeResult.READ_TIMEOUT -> "Verbindung steht, aber der Server antwortet nicht rechtzeitig. " +
                "Mögliche Ursache: Die Alpine-App ist im Hintergrund eingefroren oder das Gerät ist überlastet."
            ProbeResult.TIMEOUT -> "Innerhalb der Gesamtfrist kam keine vollständige Antwort. Ob eine Verbindung zustande kam, ist unbekannt."
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

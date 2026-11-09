package de.laraai.hub

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.Proxy
import java.util.concurrent.TimeUnit

internal enum class LocalChatProblem {
    ADDRESS_WRONG, SERVER_OFF, TIMEOUT, MODEL_MISSING, API_MISSING, SERVER_ERROR, BAD_RESPONSE, EMPTY_REPLY, FAILED
}

internal class LocalChatException(val problem: LocalChatProblem, message: String) : Exception(message)

internal data class LocalChatReply(val text: String, val usage: TokenUsage?)

/**
 * Chat with the local model server, for the preview. Uses exactly the configured address,
 * needs no key, sends no prompt anywhere else and gives up after [TIMEOUT_MS] with one
 * specific error per cause. Blocking: call it off the main thread.
 */
internal class LocalChatClient(private val timeoutMs: Long = TIMEOUT_MS) {
    private class HttpResult(val code: Int, val body: String)

    private val client by lazy {
        OkHttpClient.Builder()
            .proxy(Proxy.NO_PROXY)
            .followRedirects(false)
            .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .build()
    }

    fun send(address: String, model: String, api: ApiMode, previous: List<ChatTurn>, prompt: String): LocalChatReply {
        val root = rootOf(address)
        val path = pathOf(api)
        val messages = JSONArray()
        ChatHistory.build(previous, prompt).forEach {
            messages.put(JSONObject().put("role", it.role).put("content", it.content))
        }
        val payload = JSONObject().put("model", model).put("messages", messages).put("stream", false)
        val request = requestFor("$root$path") {
            post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
        }
        val result = execute(request)
        if (result.code !in 200..299) throw httpProblem(result, path, model)
        val json = parse(result.body) ?: throw badResponse(path)
        if (json.has("error")) throw httpProblem(result, path, model)
        val text = replyText(json, api) ?: throw badResponse(path)
        if (text.isBlank()) throw LocalChatException(LocalChatProblem.EMPTY_REPLY, "Leere Antwort: Der Server hat keinen Text geschickt.")
        val format = if (api == ApiMode.OPENAI_COMPATIBLE) "openai" else "ollama"
        return LocalChatReply(text.trim(), UsageParser.parse(format, AiProvider.OLLAMA.id, model, json, System.currentTimeMillis()))
    }

    /** Reads the model list only. A server that does not answer at all is not asked a second time. */
    fun models(address: String): ApiDiscovery {
        val root = rootOf(address)
        val first = execute(requestFor("$root$MODELS_OPENAI") { get() })
        if (first.code in 200..299) {
            parse(first.body)?.optJSONArray("data")?.let { list ->
                return ApiDiscovery(ApiMode.OPENAI_COMPATIBLE, names(list, "id"))
            }
        }
        val second = execute(requestFor("$root$MODELS_NATIVE") { get() })
        if (second.code in 200..299) {
            parse(second.body)?.optJSONArray("models")?.let { list ->
                return ApiDiscovery(ApiMode.OLLAMA_NATIVE, names(list, "name"))
            }
        }
        if (first.code in API_GONE && second.code in API_GONE) {
            throw LocalChatException(
                LocalChatProblem.API_MISSING,
                "Schnittstelle fehlt: Weder $MODELS_OPENAI noch $MODELS_NATIVE gibt es auf diesem Server."
            )
        }
        throw LocalChatException(
            LocalChatProblem.SERVER_ERROR,
            "Serverfehler: HTTP ${first.code} auf $MODELS_OPENAI, HTTP ${second.code} auf $MODELS_NATIVE."
        )
    }

    private fun rootOf(address: String): String = try {
        ApiConnection.rootUrl(address, AiProvider.OLLAMA)
    } catch (e: IllegalArgumentException) {
        throw LocalChatException(LocalChatProblem.ADDRESS_WRONG, "Adresse falsch: ${e.message}")
    }

    private fun requestFor(url: String, configure: Request.Builder.() -> Request.Builder): Request = try {
        Request.Builder().url(url).configure().build()
    } catch (_: IllegalArgumentException) {
        throw LocalChatException(LocalChatProblem.ADDRESS_WRONG, "Adresse falsch: Host oder Port sind ungültig.")
    }

    private fun execute(request: Request): HttpResult = try {
        client.newCall(request).execute().use { HttpResult(it.code, it.peekBody(MAX_BODY_BYTES).string()) }
    } catch (e: IOException) {
        throw connectionProblem(e, "${request.url.host}:${request.url.port}")
    }

    private fun connectionProblem(error: IOException, where: String): LocalChatException = when (AlpineProbe.classify(error)) {
        ProbeResult.REFUSED, ProbeResult.CONNECT_FAILED -> LocalChatException(
            LocalChatProblem.SERVER_OFF,
            "Server aus: Unter $where antwortet kein Dienst. Läuft er, und stimmt der Port?"
        )
        ProbeResult.CONNECT_TIMEOUT, ProbeResult.READ_TIMEOUT, ProbeResult.TIMEOUT -> LocalChatException(
            LocalChatProblem.TIMEOUT,
            "Timeout: keine Antwort (Server eingefroren?). Abbruch nach ${timeoutMs / 1000} Sekunden."
        )
        ProbeResult.UNKNOWN_HOST, ProbeResult.BLOCKED -> LocalChatException(
            LocalChatProblem.ADDRESS_WRONG,
            "Adresse falsch: $where ist so nicht erreichbar."
        )
        else -> LocalChatException(
            LocalChatProblem.FAILED,
            "Verbindung fehlgeschlagen (${error.javaClass.simpleName})."
        )
    }

    private fun httpProblem(result: HttpResult, path: String, model: String): LocalChatException {
        val text = result.body.lowercase()
        return when {
            result.code in MODEL_ANSWER_CODES && text.contains("model") && MODEL_GONE.containsMatchIn(text) ->
                LocalChatException(
                    LocalChatProblem.MODEL_MISSING,
                    "Modell fehlt: Der Server kennt „$model“ nicht. Unter Tools ein vorhandenes Modell wählen."
                )
            result.code in API_GONE -> LocalChatException(
                LocalChatProblem.API_MISSING,
                "Schnittstelle fehlt: $path gibt es auf diesem Server nicht. Bitte die andere Schnittstelle probieren."
            )
            result.code in 200..299 -> badResponse(path)
            else -> LocalChatException(LocalChatProblem.SERVER_ERROR, "Serverfehler: HTTP ${result.code} auf $path.")
        }
    }

    private fun badResponse(path: String) =
        LocalChatException(LocalChatProblem.BAD_RESPONSE, "Antwort nicht lesbar: Auf $path kam kein passendes JSON.")

    private fun parse(body: String): JSONObject? = try {
        JSONObject(body)
    } catch (_: JSONException) {
        null
    }

    private fun replyText(json: JSONObject, api: ApiMode): String? {
        val message = when (api) {
            ApiMode.OPENAI_COMPATIBLE -> json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
            ApiMode.OLLAMA_NATIVE -> json.optJSONObject("message")
        } ?: return null
        return if (message.isNull("content")) "" else message.optString("content")
    }

    private fun names(list: JSONArray, field: String): List<String> =
        (0 until list.length()).mapNotNull { list.optJSONObject(it)?.optString(field)?.takeIf { name -> name.isNotBlank() } }

    companion object {
        /** F4: a frozen server is given up on after this time, also while the answer is still arriving. */
        const val TIMEOUT_MS = 15_000L
        const val DEFAULT_MODEL = "qwen2.5-0.5b-instruct-q4_k_m"
        private const val MAX_BODY_BYTES = 2L * 1024 * 1024
        private const val MODELS_OPENAI = "/v1/models"
        private const val MODELS_NATIVE = "/api/tags"
        private val API_GONE = setOf(404, 405, 501)
        private val MODEL_ANSWER_CODES = setOf(200, 400, 404)
        private val MODEL_GONE = Regex("not found|does not exist|doesn't exist|unknown|no such|not available|not loaded|model_not_found")

        fun pathOf(api: ApiMode): String = when (api) {
            ApiMode.OPENAI_COMPATIBLE -> "/v1/chat/completions"
            ApiMode.OLLAMA_NATIVE -> "/api/chat"
        }
    }
}

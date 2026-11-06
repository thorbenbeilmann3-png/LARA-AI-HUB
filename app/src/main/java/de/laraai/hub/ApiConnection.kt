package de.laraai.hub

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

internal enum class ApiMode {
    OPENAI_COMPATIBLE,
    OLLAMA_NATIVE
}

internal data class ApiDiscovery(
    val mode: ApiMode,
    val models: List<String>
)

/**
 * Supports both Ollama's native HTTP API and OpenAI-compatible endpoints.
 * API keys are loaded only from ApiKeyStore and are never logged.
 */
internal object ApiConnection {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .callTimeout(130, TimeUnit.SECONDS)
        .build()

    suspend fun discover(context: Context, endpoint: String): ApiDiscovery =
        withContext(Dispatchers.IO) {
            val root = rootUrl(endpoint)
            val apiKey = loadApiKey(context, root)

            val openAiResult = runCatching {
                val response = execute(
                    Request.Builder()
                        .url("$root/v1/models")
                        .get()
                        .apply { if (!apiKey.isNullOrBlank()) header("Authorization", "Bearer $apiKey") }
                        .build()
                )
                val data = JSONObject(response).optJSONArray("data") ?: JSONArray()
                val models = (0 until data.length()).mapNotNull { index ->
                    data.optJSONObject(index)?.optString("id")?.takeIf { it.isNotBlank() }
                }
                ApiDiscovery(ApiMode.OPENAI_COMPATIBLE, models)
            }

            if (openAiResult.isSuccess) return@withContext openAiResult.getOrThrow()

            val nativeResult = runCatching {
                val response = execute(
                    Request.Builder()
                        .url("$root/api/tags")
                        .get()
                        .apply { if (!apiKey.isNullOrBlank()) header("Authorization", "Bearer $apiKey") }
                        .build()
                )
                val data = JSONObject(response).optJSONArray("models") ?: JSONArray()
                val models = (0 until data.length()).mapNotNull { index ->
                    data.optJSONObject(index)?.optString("name")?.takeIf { it.isNotBlank() }
                }
                ApiDiscovery(ApiMode.OLLAMA_NATIVE, models)
            }

            if (nativeResult.isSuccess) return@withContext nativeResult.getOrThrow()

            val openAiError = openAiResult.exceptionOrNull()?.message ?: "OpenAI-kompatibler Endpunkt nicht erreichbar"
            val nativeError = nativeResult.exceptionOrNull()?.message ?: "Ollama-Endpunkt nicht erreichbar"
            error("Verbindung fehlgeschlagen. $openAiError; $nativeError")
        }

    suspend fun chat(
        context: Context,
        endpoint: String,
        model: String,
        prompt: String,
        mode: ApiMode
    ): String = withContext(Dispatchers.IO) {
        val root = rootUrl(endpoint)
        val apiKey = loadApiKey(context, root)
        val payload = JSONObject()
            .put("model", model)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
            .put("stream", false)

        val url = when (mode) {
            ApiMode.OPENAI_COMPATIBLE -> "$root/v1/chat/completions"
            ApiMode.OLLAMA_NATIVE -> "$root/api/chat"
        }
        val request = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .apply { if (!apiKey.isNullOrBlank()) header("Authorization", "Bearer $apiKey") }
            .build()

        val responseText = execute(request)
        val json = JSONObject(responseText)
        when (mode) {
            ApiMode.OPENAI_COMPATIBLE -> json.getJSONArray("choices")
                .getJSONObject(0).getJSONObject("message").optString("content", "Leere Antwort")
            ApiMode.OLLAMA_NATIVE -> json.getJSONObject("message").optString("content", "Leere Antwort")
        }
    }

    private fun loadApiKey(context: Context, root: String): String? {
        val key = ApiKeyStore.load(context)
        if (!key.isNullOrBlank() && !root.startsWith("https://", ignoreCase = true)) {
            error("API-Schlüssel werden nur über HTTPS übertragen. Für lokales Ollama das Schlüssel-Feld leer lassen.")
        }
        return key
    }

    private fun rootUrl(endpoint: String): String {
        val value = endpoint.trim().trimEnd('/')
        require(value.startsWith("http://", true) || value.startsWith("https://", true)) {
            "Bitte eine gültige HTTP- oder HTTPS-Adresse eingeben."
        }
        val suffixes = listOf(
            "/v1/chat/completions",
            "/v1/models",
            "/api/chat",
            "/api/tags",
            "/v1"
        )
        return suffixes.firstOrNull { value.endsWith(it, ignoreCase = true) }
            ?.let { value.dropLast(it.length).trimEnd('/') }
            ?.ifBlank { value }
            ?: value
    }

    private fun execute(request: Request): String {
        client.newCall(request).execute().use { response ->
            val responseText = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("HTTP ${response.code}: ${responseText.take(240)}")
            }
            return responseText
        }
    }
}

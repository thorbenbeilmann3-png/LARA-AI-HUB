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
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

internal enum class AiProvider(
    val id: String,
    val label: String,
    val defaultEndpoint: String,
    val requiresApiKey: Boolean
) {
    OLLAMA("ollama", "Ollama · lokal", "http://127.0.0.1:12434/", false),
    OPENAI("openai", "OpenAI", "https://api.openai.com/v1", true),
    ANTHROPIC("anthropic", "Anthropic · Claude", "https://api.anthropic.com", true),
    GEMINI("gemini", "Google · Gemini", "https://generativelanguage.googleapis.com/v1beta", true),
    OPENROUTER("openrouter", "OpenRouter", "https://openrouter.ai/api/v1", true),
    CEREBRAS("cerebras", "Cerebras", "https://api.cerebras.ai/v1", true),
    OPENCLAW("openclaw", "OpenClaw · Gateway", "", true),
    CUSTOM("custom", "Eigener OpenAI-kompatibler Server", "", false);

    companion object {
        fun fromId(id: String): AiProvider =
            entries.firstOrNull { it.id == id } ?: OLLAMA
    }
}

internal enum class ApiMode {
    OPENAI_COMPATIBLE,
    OLLAMA_NATIVE
}

internal data class ApiDiscovery(
    val mode: ApiMode,
    val models: List<String>
)

/**
 * Provider-specific HTTP adapters. Credentials are read only from ApiKeyStore
 * and are never written to logs or returned to the UI.
 */
internal object ApiConnection {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .callTimeout(130, TimeUnit.SECONDS)
        .build()

    // Preserve the original generic endpoint behavior for older app versions.
    suspend fun discover(context: Context, endpoint: String): ApiDiscovery =
        withContext(Dispatchers.IO) {
            val root = rootUrl(endpoint, AiProvider.CUSTOM)
            val apiKey = loadLegacyApiKey(context, root)
            discoverOllama(root, apiKey)
        }

    suspend fun discover(
        context: Context,
        provider: AiProvider,
        endpoint: String
    ): ApiDiscovery = withContext(Dispatchers.IO) {
        val root = rootUrl(endpoint, provider)
        val apiKey = loadProviderApiKey(context, provider, root)

        if (provider == AiProvider.OLLAMA) {
            return@withContext discoverOllama(root, apiKey)
        }

        val request = Request.Builder()
            .url(modelListUrl(root, provider))
            .get()
            .apply { applyProviderHeaders(this, provider, apiKey) }
            .build()
        val responseText = try {
            execute(request)
        } catch (e: Exception) {
            if (provider == AiProvider.OPENCLAW && e.message.orEmpty().contains("HTTP 404")) {
                error(
                    "OpenClaw Chat Completions ist deaktiviert oder nicht erreichbar. " +
                        "Aktiviere in der Gateway-Konfiguration " +
                        "gateway.http.endpoints.chatCompletions.enabled=true."
                )
            }
            throw e
        }
        val response = JSONObject(responseText)
        val models = when (provider) {
            AiProvider.ANTHROPIC -> parseIdModels(response)
            AiProvider.GEMINI -> parseGeminiModels(response)
            else -> parseIdModels(response)
        }
        ApiDiscovery(ApiMode.OPENAI_COMPATIBLE, models)
    }

    // Preserve the original single-endpoint behavior and legacy credential.
    suspend fun chat(
        context: Context,
        endpoint: String,
        model: String,
        prompt: String,
        mode: ApiMode
    ): String = withContext(Dispatchers.IO) {
        val root = rootUrl(endpoint, AiProvider.CUSTOM)
        val apiKey = loadLegacyApiKey(context, root)
        chatOllama(root, model, prompt, mode, apiKey)
    }

    suspend fun chat(
        context: Context,
        provider: AiProvider,
        endpoint: String,
        model: String,
        prompt: String,
        mode: ApiMode,
        conversationId: String? = null,
        history: List<ChatTurn> = emptyList()
    ): String = withContext(Dispatchers.IO) {
        require(model.isNotBlank()) { "Bitte zuerst ein Modell auswählen." }
        require(prompt.isNotBlank()) { "Die Nachricht darf nicht leer sein." }

        if (provider == AiProvider.OLLAMA) {
            return@withContext chatOllama(endpoint, model, prompt, mode, null, history)
        }

        val root = rootUrl(endpoint, provider)
        val apiKey = loadProviderApiKey(context, provider, root)
        val request = when (provider) {
            AiProvider.ANTHROPIC -> anthropicChatRequest(root, apiKey, model, prompt, history)
            AiProvider.GEMINI -> geminiChatRequest(root, apiKey, model, prompt, history)
            AiProvider.OPENAI,
            AiProvider.OPENROUTER,
            AiProvider.CEREBRAS,
            AiProvider.OPENCLAW,
            AiProvider.CUSTOM -> openAiCompatibleChatRequest(
                root, apiKey, model, prompt, provider, conversationId, history
            )
            AiProvider.OLLAMA -> error("Interner Ollama-Routingfehler.")
        }
        val response = JSONObject(execute(request))
        when (provider) {
            AiProvider.ANTHROPIC -> {
                val content = response.optJSONArray("content") ?: JSONArray()
                (0 until content.length()).mapNotNull { index ->
                    content.optJSONObject(index)?.optString("text")?.takeIf { it.isNotBlank() }
                }.joinToString("\n").ifBlank { "Leere Antwort" }
            }
            AiProvider.GEMINI -> {
                val candidates = response.optJSONArray("candidates") ?: JSONArray()
                val parts = candidates.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts") ?: JSONArray()
                (0 until parts.length()).mapNotNull { index ->
                    parts.optJSONObject(index)?.optString("text")?.takeIf { it.isNotBlank() }
                }.joinToString("\n").ifBlank { "Leere Antwort" }
            }
            else -> response.getJSONArray("choices")
                .getJSONObject(0).getJSONObject("message").optString("content", "Leere Antwort")
        }
    }

    private suspend fun discoverOllama(root: String, apiKey: String?): ApiDiscovery {
        val openAiResult = runCatching {
            val response = execute(
                Request.Builder()
                    .url("$root/v1/models")
                    .get()
                    .apply { if (!apiKey.isNullOrBlank()) header("Authorization", "Bearer $apiKey") }
                    .build()
            )
            ApiDiscovery(ApiMode.OPENAI_COMPATIBLE, parseIdModels(JSONObject(response)))
        }
        if (openAiResult.isSuccess) return openAiResult.getOrThrow()

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
        if (nativeResult.isSuccess) return nativeResult.getOrThrow()

        val openAiError = openAiResult.exceptionOrNull()?.message
            ?: "OpenAI-kompatibler Endpunkt nicht erreichbar"
        val nativeError = nativeResult.exceptionOrNull()?.message
            ?: "Ollama-Endpunkt nicht erreichbar"
        error("Verbindung fehlgeschlagen. $openAiError; $nativeError")
    }

    private fun chatOllama(
        endpoint: String,
        model: String,
        prompt: String,
        mode: ApiMode,
        apiKey: String?,
        history: List<ChatTurn> = emptyList()
    ): String {
        val root = rootUrl(endpoint, AiProvider.OLLAMA)
        val payload = JSONObject()
            .put("model", model)
            .put("messages", roleContentMessages(ChatHistory.build(history, prompt)))
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
        val json = JSONObject(execute(request))
        return when (mode) {
            ApiMode.OPENAI_COMPATIBLE -> json.getJSONArray("choices")
                .getJSONObject(0).getJSONObject("message").optString("content", "Leere Antwort")
            ApiMode.OLLAMA_NATIVE -> json.getJSONObject("message").optString("content", "Leere Antwort")
        }
    }

    private fun openAiCompatibleChatRequest(
        root: String,
        apiKey: String?,
        model: String,
        prompt: String,
        provider: AiProvider,
        conversationId: String?,
        history: List<ChatTurn> = emptyList()
    ): Request {
        // OpenClaw keeps the conversation server-side (user = conv:<id>); sending the
        // local history as well would duplicate it.
        val turns = if (provider == AiProvider.OPENCLAW) emptyList() else history
        val payload = JSONObject()
            .put("model", model)
            .put("messages", roleContentMessages(ChatHistory.build(turns, prompt)))
            .put("stream", false)
        if (provider == AiProvider.OPENCLAW && !conversationId.isNullOrBlank()) {
            payload.put("user", "conv:$conversationId")
        }
        return Request.Builder()
            .url("$root/v1/chat/completions")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .apply { if (!apiKey.isNullOrBlank()) header("Authorization", "Bearer $apiKey") }
            .build()
    }

    private fun anthropicChatRequest(
        root: String,
        apiKey: String?,
        model: String,
        prompt: String,
        history: List<ChatTurn> = emptyList()
    ): Request {
        val payload = JSONObject()
            .put("model", model)
            .put("max_tokens", 1024)
            .put("messages", roleContentMessages(ChatHistory.build(history, prompt)))
        return Request.Builder()
            .url("$root/v1/messages")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .apply { applyProviderHeaders(this, AiProvider.ANTHROPIC, apiKey) }
            .build()
    }

    private fun geminiChatRequest(
        root: String,
        apiKey: String?,
        model: String,
        prompt: String,
        history: List<ChatTurn> = emptyList()
    ): Request {
        val safeModel = URLEncoder.encode(model.removePrefix("models/"), "UTF-8")
            .replace("+", "%20")
        val contents = JSONArray()
        ChatHistory.build(history, prompt).forEach { turn ->
            contents.put(
                JSONObject()
                    .put("role", if (turn.role == "assistant") "model" else "user")
                    .put("parts", JSONArray().put(JSONObject().put("text", turn.content)))
            )
        }
        val payload = JSONObject().put("contents", contents)
        return Request.Builder()
            .url("$root/v1beta/models/$safeModel:generateContent")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .apply { applyProviderHeaders(this, AiProvider.GEMINI, apiKey) }
            .build()
    }

    private fun roleContentMessages(turns: List<ChatTurn>): JSONArray {
        val messages = JSONArray()
        turns.forEach { messages.put(JSONObject().put("role", it.role).put("content", it.content)) }
        return messages
    }

    private fun modelListUrl(root: String, provider: AiProvider): String =
        if (provider == AiProvider.GEMINI) "$root/v1beta/models" else "$root/v1/models"

    private fun applyProviderHeaders(
        builder: Request.Builder,
        provider: AiProvider,
        apiKey: String?
    ) {
        when (provider) {
            AiProvider.ANTHROPIC -> {
                builder.header("Authorization", "Bearer $apiKey")
                    .header("anthropic-version", "2026-10-21")
            }
            AiProvider.GEMINI -> builder.header("x-goog-api-key", apiKey.orEmpty())
            AiProvider.OPENAI,
            AiProvider.OPENROUTER,
            AiProvider.CEREBRAS,
            AiProvider.OPENCLAW,
            AiProvider.CUSTOM -> if (!apiKey.isNullOrBlank()) {
                builder.header("Authorization", "Bearer $apiKey")
            }
            AiProvider.OLLAMA -> Unit
        }
    }

    private fun parseIdModels(response: JSONObject): List<String> {
        val data = response.optJSONArray("data") ?: JSONArray()
        return (0 until data.length()).mapNotNull { index ->
            data.optJSONObject(index)?.optString("id")?.takeIf { it.isNotBlank() }
        }
    }

    private fun parseGeminiModels(response: JSONObject): List<String> {
        val data = response.optJSONArray("models") ?: JSONArray()
        return (0 until data.length()).mapNotNull { index ->
            val item = data.optJSONObject(index) ?: return@mapNotNull null
            val methods = item.optJSONArray("supportedGenerationMethods")
            if (methods != null && (0 until methods.length()).none {
                    methods.optString(it) == "generateContent"
                }) return@mapNotNull null
            item.optString("name")
                .removePrefix("models/")
                .takeIf { it.isNotBlank() }
        }
    }

    private fun loadLegacyApiKey(context: Context, root: String): String? {
        val key = ApiKeyStore.load(context)
        if (!key.isNullOrBlank() && !root.startsWith("https://", ignoreCase = true)) {
            error("API-Schlüssel werden ausschließlich über HTTPS übertragen. Für lokales Ollama das Schlüssel-Feld leer lassen.")
        }
        return key
    }

    private fun loadProviderApiKey(
        context: Context,
        provider: AiProvider,
        root: String
    ): String? {
        if (!provider.requiresApiKey && provider != AiProvider.CUSTOM) return null
        val key = ApiKeyStore.load(context, provider.id)
            ?: if (provider == AiProvider.CUSTOM) ApiKeyStore.load(context) else null
        if (provider.requiresApiKey && key.isNullOrBlank()) {
            val credentialName = if (provider == AiProvider.OPENCLAW) "Gateway Token" else "API-Schlüssel"
            error("Für ${provider.label} ist noch kein $credentialName gespeichert.")
        }
        if (
            !key.isNullOrBlank() &&
            !root.startsWith("https://", ignoreCase = true) &&
            !(provider == AiProvider.OPENCLAW && isPrivateGatewayEndpoint(root))
        ) {
            error("Zugangsdaten werden nur über HTTPS übertragen. Für OpenClaw ist HTTP ausschließlich im privaten LAN oder auf localhost erlaubt.")
        }
        return key
    }

    private fun isPrivateGatewayEndpoint(endpoint: String): Boolean {
        val host = runCatching { java.net.URI(endpoint).host?.lowercase() }.getOrNull() ?: return false
        if (host == "localhost" || host == "127.0.0.1" || host == "::1" || host.endsWith(".local")) {
            return true
        }
        if (host.startsWith("10.") || host.startsWith("192.168.")) return true
        val parts = host.split(".")
        if (parts.size == 4 && parts[0] == "172") {
            val second = parts[1].toIntOrNull()
            if (second != null && second in 16..31) return true
        }
        return host.startsWith("fc") || host.startsWith("fd") || host.startsWith("fe80:")
    }

    /**
     * Mirrors res/xml/network_security_config.xml: plain HTTP is only allowed to this
     * device (loopback). Gives a clear message instead of OkHttp's generic
     * "CLEARTEXT communication ... not permitted" error.
     */
    internal fun isCleartextAllowed(url: String): Boolean = CleartextPolicy.isAllowed(url)

    private fun requireCleartextAllowed(url: String) {
        require(isCleartextAllowed(url)) {
            "Unverschlüsseltes HTTP ist nur für Dienste auf diesem Handy erlaubt " +
                "(z. B. http://127.0.0.1:12434). Für andere Geräte bitte HTTPS verwenden."
        }
    }

    private fun rootUrl(endpoint: String, provider: AiProvider): String {
        val value = endpoint.trim().trimEnd('/')
        require(value.startsWith("http://", true) || value.startsWith("https://", true)) {
            "Bitte eine gültige HTTP- oder HTTPS-Adresse eingeben."
        }
        requireCleartextAllowed(value)
        val suffixes = listOf(
            "/v1/chat/completions",
            "/v1beta/models",
            "/v1/models",
            "/api/chat",
            "/api/tags",
            "/v1beta",
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

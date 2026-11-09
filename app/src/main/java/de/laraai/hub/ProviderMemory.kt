package de.laraai.hub

import android.content.Context

/** What LARA suggests again for one provider. Never holds credentials. */
internal data class RememberedInputs(
    val address: String? = null,
    val model: String? = null,
    val mcp: String? = null
)

/** Plain app settings. Nothing secret belongs here, that is what ApiKeyStore is for. */
internal interface PlainSettings {
    fun get(key: String): String?
    fun put(key: String, value: String)
}

internal class SharedPreferencesSettings(context: Context, name: String) : PlainSettings {
    private val prefs = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)
    override fun get(key: String): String? = prefs.getString(key, null)
    override fun put(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }
}

/**
 * Remembers the last used inputs per provider. Anything that looks like a credential
 * typed into the wrong field is dropped, and the previous value stays.
 */
internal class ProviderMemory(private val settings: PlainSettings) {
    fun load(providerId: String) = RememberedInputs(
        address = settings.get("$providerId.address"),
        model = settings.get("$providerId.model"),
        mcp = settings.get("$providerId.mcp")
    )

    fun remember(providerId: String, address: String? = null, model: String? = null, mcp: String? = null) {
        cleanAddress(address)?.let { settings.put("$providerId.address", it) }
        cleanModel(model)?.let { settings.put("$providerId.model", it) }
        cleanAddress(mcp)?.let { settings.put("$providerId.mcp", it) }
    }

    companion object {
        private const val MAX_ADDRESS = 200
        private const val MAX_MODEL = 120
        private const val MAX_PATH_SEGMENTS = 4
        private const val MAX_SEGMENT = 24
        private val MODEL_CHARS = Regex("[A-Za-z0-9._:/@+-]+")
        private val LONG_OPAQUE_RUN = Regex("[A-Za-z0-9]{32,}")
        private val SECRET_PREFIXES = listOf(
            "sk-", "sk_", "ghp_", "gho_", "ghs_", "ghu_", "github_pat_", "xoxb-", "xoxp-",
            "AIza", "gsk_", "csk-", "hf_", "freellmapi-", "eyJ", "ya29."
        )

        fun shared(context: Context) = ProviderMemory(SharedPreferencesSettings(context, "lara_provider_memory"))

        /** Pattern based, so it catches the usual formats but not every conceivable secret. */
        fun looksLikeSecret(value: String): Boolean =
            SECRET_PREFIXES.any { value.startsWith(it) } ||
                value.startsWith("bearer", ignoreCase = true) ||
                LONG_OPAQUE_RUN.containsMatchIn(value)

        fun cleanAddress(value: String?): String? {
            val text = value?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_ADDRESS } ?: return null
            val uri = runCatching { java.net.URI(text) }.getOrNull() ?: return null
            if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.host.isNullOrEmpty()) return null
            if (uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null) return null
            val segments = uri.rawPath.orEmpty().split('/').filter { it.isNotEmpty() }
            if (segments.size > MAX_PATH_SEGMENTS) return null
            if (segments.any { it.length > MAX_SEGMENT || looksLikeSecret(it) }) return null
            return text
        }

        fun cleanModel(value: String?): String? {
            val text = value?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_MODEL } ?: return null
            if (!MODEL_CHARS.matches(text) || looksLikeSecret(text)) return null
            return text
        }
    }
}

/** Provider-specific hints shown under the address field. */
internal object ProviderNotes {
    fun forProvider(provider: AiProvider): String? = when (provider) {
        AiProvider.FREELLMAPI ->
            "FreeLLMAPI auf einem anderen Gerät ist erst mit HTTPS nutzbar, zum Beispiel über einen " +
                "Reverse-Proxy mit Zertifikat auf dem FreeLLMAPI-Server. Bis dahin blockiert LARA " +
                "http://-Adressen im Heimnetz, auch die Standard-Adresse. HTTP ist nur zu 127.0.0.1 " +
                "und localhost erlaubt. Das Zertifikat muss von einer öffentlichen Stelle stammen, " +
                "selbst signierte nimmt LARA nicht an."
        else -> null
    }
}

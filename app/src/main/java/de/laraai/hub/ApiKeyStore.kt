package de.laraai.hub

import android.content.Context
import android.util.Base64
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores provider credentials encrypted with AES-GCM.
 *
 * The AES key is protected by Android Keystore. Only ciphertext and the
 * per-encryption IV are written to SharedPreferences. Provider IDs are used
 * as authenticated additional data so ciphertext cannot be silently moved
 * between provider entries.
 *
 * The original no-provider methods remain available for compatibility with
 * the existing single-key UI and its previously stored credential.
 */
internal object ApiKeyStore {
    private const val ANDROID_KEY_STORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "lara_api_key_aes_v1"
    private const val PREFS_NAME = "lara_secure_api"

    // Legacy keys are intentionally retained so existing installations keep
    // access to the credential they already saved.
    private const val LEGACY_PROVIDER_ID = "legacy"
    private const val LEGACY_CIPHERTEXT_KEY = "api_key_ciphertext"
    private const val LEGACY_IV_KEY = "api_key_iv"

    private const val PROVIDER_KEY_PREFIX = "credential."
    private const val CIPHERTEXT_SUFFIX = ".ciphertext"
    private const val IV_SUFFIX = ".iv"

    fun hasSaved(context: Context): Boolean = hasSaved(context, LEGACY_PROVIDER_ID)

    fun hasSaved(context: Context, providerId: String): Boolean {
        val keys = if (providerId == LEGACY_PROVIDER_ID) {
            LEGACY_CIPHERTEXT_KEY to LEGACY_IV_KEY
        } else {
            credentialKeys(providerId)
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.contains(keys.first) && prefs.contains(keys.second)
    }

    fun save(context: Context, apiKey: String) =
        saveInternal(context, LEGACY_PROVIDER_ID, apiKey, useLegacyKeys = true)

    /**
     * Save a key for one provider, for example "openai", "anthropic",
     * "gemini", "openrouter", "cerebras" or "openclaw-gateway".
     */
    fun save(context: Context, providerId: String, apiKey: String) {
        saveInternal(context, providerId, apiKey, useLegacyKeys = false)
    }

    private fun saveInternal(
        context: Context,
        providerId: String,
        apiKey: String,
        useLegacyKeys: Boolean
    ) {
        require(apiKey.isNotBlank()) { "Bitte einen API-Schlüssel eingeben." }
        validateProviderId(providerId)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        if (!useLegacyKeys) {
            cipher.updateAAD(providerId.toByteArray(Charsets.UTF_8))
        }

        val ciphertext = cipher.doFinal(apiKey.toByteArray(Charsets.UTF_8))
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val encrypted = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        val keys = if (useLegacyKeys) {
            LEGACY_CIPHERTEXT_KEY to LEGACY_IV_KEY
        } else {
            credentialKeys(providerId)
        }

        val saved = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(keys.first, encrypted)
            .putString(keys.second, iv)
            .commit()
        check(saved) { "Der API-Schlüssel konnte nicht gespeichert werden." }
    }

    fun load(context: Context): String? = loadInternal(
        context,
        LEGACY_PROVIDER_ID,
        useLegacyKeys = true
    )

    fun load(context: Context, providerId: String): String? =
        loadInternal(context, providerId, useLegacyKeys = false)

    private fun loadInternal(
        context: Context,
        providerId: String,
        useLegacyKeys: Boolean
    ): String? {
        validateProviderId(providerId)
        val keys = if (useLegacyKeys) {
            LEGACY_CIPHERTEXT_KEY to LEGACY_IV_KEY
        } else {
            credentialKeys(providerId)
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val ciphertext = prefs.getString(keys.first, null) ?: return null
        val iv = prefs.getString(keys.second, null)
            ?: error("Der gespeicherte API-Schlüssel ist unvollständig. Bitte erneut speichern.")

        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                getExistingSecretKey(),
                GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
            )
            if (!useLegacyKeys) {
                cipher.updateAAD(providerId.toByteArray(Charsets.UTF_8))
            }
            String(
                cipher.doFinal(Base64.decode(ciphertext, Base64.NO_WRAP)),
                Charsets.UTF_8
            )
        } catch (_: Exception) {
            error("Der gespeicherte API-Schlüssel ist nicht mehr lesbar. Bitte erneut speichern.")
        }
    }

    /** Remove one provider credential without affecting other providers. */
    fun clear(context: Context) = clear(context, LEGACY_PROVIDER_ID)

    fun clear(context: Context, providerId: String) {
        validateProviderId(providerId)
        val keys = if (providerId == LEGACY_PROVIDER_ID) {
            LEGACY_CIPHERTEXT_KEY to LEGACY_IV_KEY
        } else {
            credentialKeys(providerId)
        }
        val cleared = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(keys.first)
            .remove(keys.second)
            .commit()
        check(cleared) { "Der gespeicherte API-Schlüssel konnte nicht entfernt werden." }
        // Do not delete the shared Keystore key here: other provider entries
        // may still depend on it.
    }

    /** Remove all provider credentials and then delete the shared Keystore key. */
    fun clearAll(context: Context) {
        val cleared = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        check(cleared) { "Gespeicherte API-Schlüssel konnten nicht vollständig entfernt werden." }

        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) {
            keyStore.deleteEntry(KEY_ALIAS)
        }
    }

    private fun credentialKeys(providerId: String): Pair<String, String> {
        validateProviderId(providerId)
        require(providerId != LEGACY_PROVIDER_ID) {
            "Die Anbieter-ID 'legacy' ist für den internen Altbestand reserviert."
        }
        val prefix = "$PROVIDER_KEY_PREFIX$providerId"
        return "$prefix$CIPHERTEXT_SUFFIX" to "$prefix$IV_SUFFIX"
    }

    private fun validateProviderId(providerId: String) {
        require(providerId.matches(Regex("[a-z0-9][a-z0-9._-]{0,63}"))) {
            "Ungültige Anbieter-ID."
        }
    }

    private fun getExistingSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        return keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            ?: error("Der sichere Geräteschlüssel ist nicht verfügbar.")
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEY_STORE
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }
}

package de.laraai.hub

import android.content.Context
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties

/**
 * Stores an API key encrypted with an AES key protected by Android Keystore.
 * Only the ciphertext and the GCM IV are written to SharedPreferences.
 */
internal object ApiKeyStore {
    private const val ANDROID_KEY_STORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "lara_api_key_aes_v1"
    private const val PREFS_NAME = "lara_secure_api"
    private const val CIPHERTEXT_KEY = "api_key_ciphertext"
    private const val IV_KEY = "api_key_iv"

    fun hasSaved(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.contains(CIPHERTEXT_KEY) && prefs.contains(IV_KEY)
    }

    fun save(context: Context, apiKey: String) {
        require(apiKey.isNotBlank()) { "Bitte einen API-Schlüssel eingeben." }

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val ciphertext = cipher.doFinal(apiKey.toByteArray(Charsets.UTF_8))
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val encrypted = Base64.encodeToString(ciphertext, Base64.NO_WRAP)

        val saved = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(CIPHERTEXT_KEY, encrypted)
            .putString(IV_KEY, iv)
            .commit()
        check(saved) { "Der API-Schlüssel konnte nicht gespeichert werden." }
    }

    fun load(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val ciphertext = prefs.getString(CIPHERTEXT_KEY, null) ?: return null
        val iv = prefs.getString(IV_KEY, null)
            ?: error("Der gespeicherte API-Schlüssel ist unvollständig. Bitte erneut speichern.")

        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                getExistingSecretKey(),
                GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
            )
            String(
                cipher.doFinal(Base64.decode(ciphertext, Base64.NO_WRAP)),
                Charsets.UTF_8
            )
        } catch (_: Exception) {
            error("Der gespeicherte API-Schlüssel ist nicht mehr lesbar. Bitte erneut speichern.")
        }
    }

    fun clear(context: Context) {
        val cleared = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        check(cleared) { "Der gespeicherte API-Schlüssel konnte nicht entfernt werden." }

        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) {
            keyStore.deleteEntry(KEY_ALIAS)
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

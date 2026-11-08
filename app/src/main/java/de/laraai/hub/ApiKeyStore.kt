package de.laraai.hub

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.UserNotAuthenticatedException
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores provider credentials encrypted with AES-GCM in Android Keystore.
 *
 * Version 2 (current): the AES key requires user authentication (biometric or
 * device PIN/pattern/password) within the last [AUTH_VALIDITY_SECONDS]. LARA never
 * sees biometric data; Android only reports success. After [unlock] the decrypted
 * credentials live only in memory ([SessionVault]) until the app locks again.
 *
 * Version 1 (legacy): key without user authentication. Entries are migrated to
 * version 2 on the first successful unlock; the v1 key and data are deleted only
 * after every entry was re-encrypted and written successfully.
 *
 * Provider IDs are authenticated additional data so ciphertext cannot be moved
 * between provider entries. The legacy single-key entry keeps its ID "legacy".
 */
internal object ApiKeyStore {
    private const val ANDROID_KEY_STORE = "AndroidKeyStore"
    private const val KEY_ALIAS_V1 = "lara_api_key_aes_v1"
    private const val KEY_ALIAS_V2 = "lara_api_key_aes_v2_auth"
    private const val PREFS_V1 = "lara_secure_api"
    private const val PREFS_V2 = "lara_secure_api_v2"
    const val AUTH_VALIDITY_SECONDS = 300

    private const val LEGACY_PROVIDER_ID = "legacy"
    private const val LEGACY_CIPHERTEXT_KEY = "api_key_ciphertext"
    private const val LEGACY_IV_KEY = "api_key_iv"
    private const val PROVIDER_KEY_PREFIX = "credential."
    private const val CIPHERTEXT_SUFFIX = ".ciphertext"
    private const val IV_SUFFIX = ".iv"

    /** Result of [unlock], shown to the user. */
    data class UnlockResult(val loaded: Int, val migrated: Int, val message: String?)

    // ---------------------------------------------------------------- public API

    fun hasSaved(context: Context): Boolean = hasSaved(context, LEGACY_PROVIDER_ID)

    fun hasSaved(context: Context, providerId: String): Boolean {
        val keys = keysFor(providerId)
        return listOf(PREFS_V2, PREFS_V1).any { name ->
            val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
            prefs.contains(keys.first) && prefs.contains(keys.second)
        }
    }

    fun save(context: Context, apiKey: String) = save(context, LEGACY_PROVIDER_ID, apiKey)

    /**
     * Save a key for one provider. Must be called right after a successful
     * authentication (the UI asks again before saving); otherwise Android rejects
     * the key use and this throws [IllegalStateException] with a clear message.
     */
    fun save(context: Context, providerId: String, apiKey: String) {
        require(apiKey.isNotBlank()) { "Bitte einen API-Schlüssel eingeben." }
        val keys = keysFor(providerId)
        val (ciphertext, iv) = encrypt(providerId, apiKey)
        val saved = context.getSharedPreferences(PREFS_V2, Context.MODE_PRIVATE)
            .edit()
            .putString(keys.first, ciphertext)
            .putString(keys.second, iv)
            .commit()
        check(saved) { "Der API-Schlüssel konnte nicht gespeichert werden." }
        // A newer v2 entry supersedes an older v1 entry for the same provider.
        context.getSharedPreferences(PREFS_V1, Context.MODE_PRIVATE)
            .edit().remove(keys.first).remove(keys.second).commit()
        SessionVault.put(providerId, apiKey)
    }

    fun load(context: Context): String? = load(context, LEGACY_PROVIDER_ID)

    /** Returns the decrypted key from the unlocked session, or null if none is saved. */
    fun load(context: Context, providerId: String): String? {
        keysFor(providerId)
        if (!hasSaved(context, providerId)) return null
        check(SessionVault.isUnlocked) { "LARA ist gesperrt. Bitte zuerst entsperren." }
        return SessionVault.get(providerId)
            ?: error("Der gespeicherte API-Schlüssel ist nicht mehr lesbar. Bitte erneut speichern.")
    }

    fun clear(context: Context) = clear(context, LEGACY_PROVIDER_ID)

    /** Remove one provider credential without affecting other providers. */
    fun clear(context: Context, providerId: String) {
        val keys = keysFor(providerId)
        for (name in listOf(PREFS_V2, PREFS_V1)) {
            val cleared = context.getSharedPreferences(name, Context.MODE_PRIVATE)
                .edit().remove(keys.first).remove(keys.second).commit()
            check(cleared) { "Der gespeicherte API-Schlüssel konnte nicht entfernt werden." }
        }
        SessionVault.remove(providerId)
    }

    /** Remove all provider credentials and both Keystore keys. */
    fun clearAll(context: Context) {
        for (name in listOf(PREFS_V2, PREFS_V1)) {
            val cleared = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
            check(cleared) { "Gespeicherte API-Schlüssel konnten nicht vollständig entfernt werden." }
        }
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        listOf(KEY_ALIAS_V1, KEY_ALIAS_V2).forEach { if (keyStore.containsAlias(it)) keyStore.deleteEntry(it) }
        SessionVault.lock()
    }

    /**
     * Call only right after the system authentication prompt succeeded.
     * Migrates v1 entries, decrypts all v2 entries into [SessionVault] and unlocks it.
     */
    fun unlock(context: Context): UnlockResult {
        val values = mutableMapOf<String, String>()
        var invalidated = false
        val v2 = context.getSharedPreferences(PREFS_V2, Context.MODE_PRIVATE)
        for (providerId in storedProviderIds(v2)) {
            try {
                decrypt(v2, providerId, KEY_ALIAS_V2)?.let { values[providerId] = it }
            } catch (e: KeyPermanentlyInvalidatedException) {
                invalidated = true
                break
            }
        }
        if (invalidated) {
            // Android invalidated the key (e.g. a new fingerprint was enrolled).
            // The ciphertext can never be decrypted again; remove it.
            v2.edit().clear().commit()
            deleteKey(KEY_ALIAS_V2)
            values.clear()
        }
        val migration = migrateV1(context, values)
        SessionVault.unlock(values)
        val message = listOfNotNull(
            if (invalidated) "Die Geräte-Anmeldung wurde geändert (z. B. neuer Fingerabdruck). " +
                "Aus Sicherheitsgründen sind die gespeicherten API-Schlüssel ungültig und müssen neu eingegeben werden." else null,
            if (migration.migrated > 0) "${migration.migrated} vorhandene(r) Schlüssel wurde(n) auf die geschützte Speicherung umgestellt." else null,
            if (migration.failed.isNotEmpty()) "Nicht lesbar und daher nicht übernommen: ${migration.failed.joinToString()}. " +
                "Bitte diese Schlüssel neu eingeben." else null
        ).joinToString(" ").ifBlank { null }
        return UnlockResult(values.size, migration.migrated, message)
    }

    // ---------------------------------------------------------------- internals

    /**
     * Re-encrypts every readable v1 entry with the auth-bound v2 key. Readable entries
     * are moved one by one (v1 copy removed only after the v2 write succeeded).
     * Unreadable entries stay in v1 untouched and are reported; the v1 key is deleted
     * only when no v1 entry is left.
     */
    private fun migrateV1(context: Context, values: MutableMap<String, String>): MigrationResult {
        val v1 = context.getSharedPreferences(PREFS_V1, Context.MODE_PRIVATE)
        val ids = storedProviderIds(v1)
        if (ids.isEmpty()) return MigrationResult(0, emptyList())
        var migrated = 0
        val failed = mutableListOf<String>()
        for (providerId in ids) {
            val value = try {
                decrypt(v1, providerId, KEY_ALIAS_V1)
            } catch (_: Exception) {
                null
            }
            if (value == null) {
                failed.add(providerId)
                continue
            }
            val keys = keysFor(providerId)
            val (ciphertext, iv) = encrypt(providerId, value)
            val written = context.getSharedPreferences(PREFS_V2, Context.MODE_PRIVATE).edit()
                .putString(keys.first, ciphertext).putString(keys.second, iv).commit()
            if (!written) {
                failed.add(providerId)
                continue
            }
            v1.edit().remove(keys.first).remove(keys.second).commit()
            values[providerId] = value
            migrated++
        }
        if (storedProviderIds(v1).isEmpty()) deleteKey(KEY_ALIAS_V1)
        return MigrationResult(migrated, failed)
    }

    private data class MigrationResult(val migrated: Int, val failed: List<String>)

    private fun storedProviderIds(prefs: android.content.SharedPreferences): List<String> {
        val ids = mutableListOf<String>()
        if (prefs.contains(LEGACY_CIPHERTEXT_KEY)) ids.add(LEGACY_PROVIDER_ID)
        prefs.all.keys
            .filter { it.startsWith(PROVIDER_KEY_PREFIX) && it.endsWith(CIPHERTEXT_SUFFIX) }
            .mapTo(ids) { it.removePrefix(PROVIDER_KEY_PREFIX).removeSuffix(CIPHERTEXT_SUFFIX) }
        return ids
    }

    private fun encrypt(providerId: String, value: String): Pair<String, String> {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        try {
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateV2Key())
        } catch (_: UserNotAuthenticatedException) {
            error("Bitte LARA erneut entsperren und den Vorgang wiederholen.")
        }
        if (providerId != LEGACY_PROVIDER_ID) cipher.updateAAD(providerId.toByteArray(Charsets.UTF_8))
        val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(ciphertext, Base64.NO_WRAP) to Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
    }

    /** Returns null when the entry is incomplete or unreadable; rethrows key invalidation. */
    private fun decrypt(prefs: android.content.SharedPreferences, providerId: String, alias: String): String? {
        val keys = keysFor(providerId)
        val ciphertext = prefs.getString(keys.first, null) ?: return null
        val iv = prefs.getString(keys.second, null) ?: return null
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        val key = keyStore.getKey(alias, null) as? SecretKey ?: return null
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            if (providerId != LEGACY_PROVIDER_ID) cipher.updateAAD(providerId.toByteArray(Charsets.UTF_8))
            String(cipher.doFinal(Base64.decode(ciphertext, Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (e: KeyPermanentlyInvalidatedException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private fun getOrCreateV2Key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS_V2, null) as? SecretKey)?.let { return it }
        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS_V2,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(
                AUTH_VALIDITY_SECONDS,
                KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
            )
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(AUTH_VALIDITY_SECONDS)
        }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        try {
            generator.init(builder.build())
            return generator.generateKey()
        } catch (e: Exception) {
            error("Für geschützte API-Schlüssel muss auf dem Gerät eine Displaysperre (PIN, Muster oder Passwort) eingerichtet sein.")
        }
    }

    private fun deleteKey(alias: String) {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
    }

    private fun keysFor(providerId: String): Pair<String, String> {
        require(providerId.matches(Regex("[a-z0-9][a-z0-9._-]{0,63}"))) { "Ungültige Anbieter-ID." }
        if (providerId == LEGACY_PROVIDER_ID) return LEGACY_CIPHERTEXT_KEY to LEGACY_IV_KEY
        val prefix = "$PROVIDER_KEY_PREFIX$providerId"
        return "$prefix$CIPHERTEXT_SUFFIX" to "$prefix$IV_SUFFIX"
    }
}

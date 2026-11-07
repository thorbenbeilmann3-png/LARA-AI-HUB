package de.laraai.hub

import android.content.Context
import org.json.JSONObject

/** Result of the last connection test for one provider. */
internal data class ProviderCheck(
    val providerId: String,
    val ok: Boolean,
    val message: String,
    val checkedAtMs: Long,
    val lastSuccessMs: Long?
)

/** Pure rules for showing provider availability (unit-tested). */
internal object ProviderAvailability {
    /** A provider counts as available only after a successful test within [maxAgeMs]. */
    const val DEFAULT_MAX_AGE_MS = 24 * 60 * 60 * 1000L

    enum class State { VERFUEGBAR, FEHLER, VERALTET, UNGEPRUEFT }

    fun state(check: ProviderCheck?, nowMs: Long, maxAgeMs: Long = DEFAULT_MAX_AGE_MS): State = when {
        check == null -> State.UNGEPRUEFT
        !check.ok -> State.FEHLER
        nowMs - check.checkedAtMs > maxAgeMs -> State.VERALTET
        else -> State.VERFUEGBAR
    }

    fun label(state: State): String = when (state) {
        State.VERFUEGBAR -> "verfügbar"
        State.FEHLER -> "nicht erreichbar"
        State.VERALTET -> "lange nicht geprüft"
        State.UNGEPRUEFT -> "noch nicht geprüft"
    }

    /** Merge a new result: keep the last success time when the new test fails. */
    fun merge(previous: ProviderCheck?, providerId: String, ok: Boolean, message: String, nowMs: Long): ProviderCheck =
        ProviderCheck(providerId, ok, message, nowMs, if (ok) nowMs else previous?.lastSuccessMs)
}

/** Stores the last test result per provider in SharedPreferences (no secrets). */
internal object ProviderStatusStore {
    private const val PREFS = "lara_provider_status"

    fun load(context: Context, providerId: String): ProviderCheck? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(providerId, null) ?: return null
        return runCatching {
            val o = JSONObject(raw)
            ProviderCheck(providerId, o.getBoolean("ok"), o.optString("message"), o.getLong("checked"),
                if (o.isNull("success")) null else o.getLong("success"))
        }.getOrNull()
    }

    fun save(context: Context, check: ProviderCheck) {
        val o = JSONObject().put("ok", check.ok).put("message", check.message.take(300))
            .put("checked", check.checkedAtMs).put("success", check.lastSuccessMs ?: JSONObject.NULL)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(check.providerId, o.toString()).apply()
    }
}

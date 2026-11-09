package de.laraai.hub

import android.content.Context

internal enum class AutomateAction(val wire: String) {
    CHAT("chat"),
    CONNECTION_TEST("verbindungstest")
}

/** What Automate asked for. Carries no address, model, prompt or key by design. */
internal data class AutomateRequest(val provider: AiProvider?, val action: AutomateAction)

internal sealed interface AutomateDecision {
    data object NotForAutomate : AutomateDecision
    data class Rejected(val reason: String) : AutomateDecision
    data class Accepted(val request: AutomateRequest) : AutomateDecision
}

/**
 * LARA's own start interface for Automate, see docs/AUTOMATE-SCHNITTSTELLE.md.
 * Any app on the phone can send this intent, so it can only choose a provider and open
 * a screen. Other extras are ignored and nothing is sent to a provider from here.
 */
internal object AutomateStartInterface {
    const val ACTION = "de.laraai.hub.action.START_FLOW"
    const val EXTRA_PROVIDER = "anbieter"
    const val EXTRA_ACTION = "aktion"

    private val PROVIDERS = mapOf(
        "ollama" to AiProvider.OLLAMA,
        "claude" to AiProvider.ANTHROPIC,
        "chatgpt" to AiProvider.OPENAI,
        "freellmapi" to AiProvider.FREELLMAPI
    )

    fun parse(intentAction: String?, providerExtra: String?, actionExtra: String?): AutomateDecision {
        if (intentAction != ACTION) return AutomateDecision.NotForAutomate
        val providerName = normalized(providerExtra)
        val provider = if (providerName == null) null else PROVIDERS[providerName]
            ?: return AutomateDecision.Rejected(
                "Automate: unbekannter Anbieter. Erlaubt sind ollama, claude, chatgpt und freellmapi."
            )
        val actionName = normalized(actionExtra)
        val action = if (actionName == null) AutomateAction.CHAT else AutomateAction.entries.firstOrNull { it.wire == actionName }
            ?: return AutomateDecision.Rejected("Automate: unbekannte Aktion. Erlaubt sind chat und verbindungstest.")
        return AutomateDecision.Accepted(AutomateRequest(provider, action))
    }

    /** Cost protection also applies when Automate names no provider and the current one is paid. */
    fun needsConfirmation(request: AutomateRequest, current: AiProvider): Boolean =
        !(request.provider ?: current).isFreeToUse

    private fun normalized(value: String?): String? =
        value?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
}

internal enum class AutostartDecision { START, ALREADY_DONE, RATE_LIMITED }

/**
 * Limits automatic starts in both directions to one per minute, across app restarts.
 * The buttons a person presses in LARA never come through here.
 */
internal class AutomateGate(
    private val settings: PlainSettings,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private var autostartDone = false

    /**
     * At most once per app process, so a rebuilt screen never fires it. Not wired up at the
     * moment: LARA stopped starting flows on 09.11.2026 because the action is undocumented.
     */
    @Synchronized
    fun autostart(): AutostartDecision {
        if (autostartDone) return AutostartDecision.ALREADY_DONE
        autostartDone = true
        return if (consume(KEY_OUTBOUND)) AutostartDecision.START else AutostartDecision.RATE_LIMITED
    }

    /** Only accepted requests use up the minute. */
    @Synchronized
    fun screen(decision: AutomateDecision): AutomateDecision = when {
        decision !is AutomateDecision.Accepted -> decision
        consume(KEY_INBOUND) -> decision
        else -> AutomateDecision.Rejected("Automate-Start abgewiesen: höchstens ein Start pro Minute.")
    }

    private fun consume(key: String): Boolean {
        val now = clock()
        val last = settings.get(key)?.toLongOrNull()
        // A clock set backwards must not block starts until that time comes round again.
        if (last != null && now >= last && now - last < MIN_INTERVAL_MS) return false
        settings.put(key, now.toString())
        return true
    }

    companion object {
        const val MIN_INTERVAL_MS = 60_000L
        private const val KEY_OUTBOUND = "last_outbound_ms"
        private const val KEY_INBOUND = "last_inbound_ms"

        @Volatile
        private var instance: AutomateGate? = null

        fun shared(context: Context): AutomateGate = instance ?: synchronized(this) {
            instance ?: AutomateGate(SharedPreferencesSettings(context, "lara_automate")).also { instance = it }
        }
    }
}

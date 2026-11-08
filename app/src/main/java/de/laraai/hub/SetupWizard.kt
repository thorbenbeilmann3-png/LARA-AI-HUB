package de.laraai.hub

import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

/** Only OK counts as success. SKIPPED means that nobody checked the step. */
internal enum class SetupStatus(val label: String) {
    OPEN("offen"),
    OK("erledigt"),
    FAILED("fehlgeschlagen"),
    NEEDS_USER("braucht dich"),
    SKIPPED("übersprungen")
}

internal enum class SetupStep(
    val number: Int,
    val title: String,
    val explanation: String,
    val skippable: Boolean = true,
    val requires: SetupStep? = null,
    val provider: AiProvider? = null
) {
    APP_DEVICE(
        1, "App und Gerät prüfen",
        "LARA liest Android-Version, Gerät und Build aus und schaut, ob eine Displaysperre eingerichtet ist.",
        skippable = false
    ),
    LOCAL_SERVER(
        2, "Lokale KI finden",
        "LARA sucht die KI auf diesem Handy unter 127.0.0.1:12434 und fragt, welche Modelle sie hat."
    ),
    LOCAL_MODEL(
        3, "Modell speichern",
        "LARA speichert das gefundene Modell als Standard für den Chat und stellt den Chat dafür auf die lokale KI.",
        requires = LOCAL_SERVER
    ),
    LOCAL_CHAT(
        4, "Test-Nachricht senden",
        "LARA schickt „Sag nur: Hallo“ an die lokale KI und zeigt die echte Antwort. Das kann bis zu zwei Minuten dauern.",
        requires = LOCAL_MODEL
    ),
    ALPINE_START(
        5, "Ollama in Alpine starten",
        "LARA kopiert den Befehl „ollama serve“. Öffne danach Alpine, füge ihn dort ein, lass das Fenster offen und komm hierher zurück."
    ),
    ALPINE_CHECK(
        6, "Ollama in Alpine prüfen",
        "LARA prüft, ob Ollama unter 127.0.0.1:11434 antwortet und ob das Modell qwen2.5:0.5b da ist."
    ),
    AUTOMATE(
        7, "Automate prüfen",
        "LARA schaut nur nach, ob Automate installiert ist. Ein Flow wird dabei nicht gestartet."
    ),
    CLAUDE(
        8, "Claude verbinden",
        "LARA prüft deinen API-Schlüssel und liest dafür nur die Modellliste von Claude. Es wird kein Text gesendet.",
        provider = AiProvider.ANTHROPIC
    ),
    CHATGPT(
        9, "ChatGPT verbinden",
        "LARA prüft deinen API-Schlüssel und liest dafür nur die Modellliste von OpenAI. Es wird kein Text gesendet.",
        provider = AiProvider.OPENAI
    ),
    REPORT(
        10, "Bericht senden",
        "LARA fasst zusammen, was klappt und was noch fehlt. Du wählst im Teilen-Menü, wohin der Bericht geht.",
        skippable = false
    );

    /** Steps that build on this one, directly or through another step. */
    fun dependents(): List<SetupStep> =
        entries.filter { step -> generateSequence(step.requires) { it.requires }.any { it == this } }
}

internal data class StepOutcome(val status: SetupStatus, val message: String, val atMs: Long)

internal data class SetupState(
    val outcomes: Map<SetupStep, StepOutcome> = emptyMap(),
    val localModels: List<String> = emptyList(),
    val chosenModel: String = ""
) {
    fun statusOf(step: SetupStep): SetupStatus = outcomes[step]?.status ?: SetupStatus.OPEN

    val doneCount: Int get() = SetupStep.entries.count { statusOf(it) == SetupStatus.OK }

    /** The step "Weiter" acts on: the first one that neither worked nor was skipped. */
    val next: SetupStep?
        get() = SetupStep.entries.firstOrNull {
            statusOf(it) != SetupStatus.OK && statusOf(it) != SetupStatus.SKIPPED
        }
}

/** [shownAnswer] is real model text for the screen. It is never stored or reported. */
internal data class SetupRun(val state: SetupState, val shownAnswer: String? = null)

internal data class SetupDeviceFacts(val summary: String, val deviceLock: Boolean)

internal data class SetupAutomateFacts(val installed: Boolean, val version: String?, val flowSaved: Boolean)

internal data class LocalScan(
    val result: ProbeResult,
    val httpCode: Int?,
    val models: List<String>,
    val message: String
)

/** Everything a step does outside its own logic, so the rules can be tested on the JVM. */
internal interface SetupActions {
    fun environment(): ProbeEnvironment
    fun deviceFacts(): SetupDeviceFacts
    suspend fun scanLocalServer(): LocalScan
    fun saveLocalModel(model: String): Boolean
    suspend fun chatLocal(model: String): String
    fun copyAlpineCommand(command: String): Boolean
    suspend fun probeAlpine(): ProbeReport
    fun automateFacts(): SetupAutomateFacts
    fun hasKey(provider: AiProvider): Boolean
    fun isUnlocked(): Boolean
    suspend fun listProviderModels(provider: AiProvider): List<String>
    fun shareReport(text: String): Boolean
}

/** Asks the local server for its models. Keeps no server text except model names. */
internal class LocalModelScan(private val transport: ProbeTransport) {
    fun scan(baseUrl: String): LocalScan = try {
        val response = transport.get(baseUrl.trimEnd('/') + "/v1/models", true)
        val list = if (response.code == 200) {
            runCatching { JSONObject(response.body.orEmpty()).optJSONArray("data") }.getOrNull()
        } else {
            null
        }
        val names = list?.let { data ->
            (0 until data.length()).mapNotNull { index ->
                (data.optJSONObject(index)?.opt("id") as? String)?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_NAME)
            }.take(MAX_MODELS)
        }
        when {
            response.code != 200 -> LocalScan(
                ProbeResult.HTTP_ERROR, response.code, emptyList(),
                "Auf dieser Adresse antwortet etwas, aber ohne Modellliste (HTTP ${response.code})."
            )
            names == null -> LocalScan(ProbeResult.BAD_RESPONSE, 200, emptyList(), "Die Antwort enthält keine Modellliste.")
            names.isEmpty() -> LocalScan(ProbeResult.BAD_RESPONSE, 200, emptyList(), "Der Server antwortet, meldet aber kein Modell.")
            else -> LocalScan(ProbeResult.OK, 200, names, "${names.size} Modell(e) gefunden.")
        }
    } catch (e: Exception) {
        val result = AlpineProbe.classify(e)
        LocalScan(result, null, emptyList(), AlpineProbe.explain(result))
    }

    private companion object {
        const val MAX_MODELS = 50
        const val MAX_NAME = 200
    }
}

internal object SetupErrors {
    private val HTTP_CODE = Regex("""\bHTTP (\d{3})\b""")

    /**
     * Built from the status code and error type only. Provider error texts can quote
     * parts of an API key, so they never reach the screen, the store or the report.
     */
    fun safeReason(label: String, error: Throwable): String {
        val code = HTTP_CODE.find(error.message.orEmpty())?.groupValues?.get(1)?.toIntOrNull()
        return when {
            code == 401 -> "$label lehnt den Schlüssel ab (HTTP 401). Bitte einen gültigen Schlüssel speichern."
            code == 403 -> "$label verweigert den Zugriff (HTTP 403). Berechtigung, Region oder Abrechnung prüfen."
            code == 404 -> "$label: Adresse oder Modell nicht gefunden (HTTP 404)."
            code == 429 -> "$label: Limit oder Guthaben erreicht (HTTP 429)."
            code != null && code in 500..599 -> "$label: Störung beim Anbieter (HTTP $code). Später noch einmal versuchen."
            code != null -> "$label hat die Anfrage abgelehnt (HTTP $code)."
            else -> when (val result = AlpineProbe.classify(error)) {
                ProbeResult.ERROR -> "$label: Unerwartete Antwort oder interner Fehler (${error.javaClass.simpleName})."
                else -> "$label: ${AlpineProbe.explain(result)}"
            }
        }
    }
}

internal object SetupWizard {
    const val LOCAL_BASE_URL = "http://127.0.0.1:12434"
    const val LOCAL_PROMPT = "Sag nur: Hallo"
    const val KNOWN_LOCAL_MODEL = "qwen2.5-0.5b-instruct-q4_k_m"
    const val ALPINE_COMMAND = "ollama serve"
    const val AUTOMATE_PACKAGE = "com.llamalab.automate"
    const val AUTOMATE_STORE_URL = "https://play.google.com/store/apps/details?id=$AUTOMATE_PACKAGE"

    /** Fixed text about what LARA cannot do yet, so the report never suggests more. */
    val NOT_BUILT = listOf(
        "Claude und ChatGPT: Geprüft wird nur der Zugang, ein echter Chat ist noch nicht getestet.",
        "Automate: Ein echter Testflow wurde noch nicht gestartet.",
        "Alpine-App: Ihr Name ist noch unbekannt, deshalb kann LARA sie nicht selbst öffnen.",
        "Installationen in Alpine kann LARA noch nicht selbst auslösen.",
        "MCP: Anmeldung und Werkzeuge fehlen in LARA noch.",
        "Python und Terminal fehlen in LARA noch."
    )

    fun chooseLocalModel(models: List<String>): String =
        models.firstOrNull { it == KNOWN_LOCAL_MODEL } ?: ModelChoice.pickDefault(models)

    /** The chat code turns missing content into a placeholder or the text "null". */
    fun isRealAnswer(text: String): Boolean {
        val value = text.trim()
        return value.isNotEmpty() && value != "Leere Antwort" && !value.equals("null", ignoreCase = true)
    }

    /** A later step cannot stay valid when the step it builds on is done again. */
    fun clearDependents(state: SetupState, step: SetupStep): SetupState {
        val cleared = step.dependents()
        return state.copy(
            outcomes = state.outcomes - cleared.toSet(),
            chosenModel = if (SetupStep.LOCAL_MODEL in cleared) "" else state.chosenModel
        )
    }

    fun skip(state: SetupState, step: SetupStep, nowMs: Long): SetupState {
        if (!step.skippable) return state
        val dependents = step.dependents().associateWith {
            StepOutcome(SetupStatus.SKIPPED, "Übersprungen, weil Etappe ${step.number} fehlt.", nowMs)
        }
        return state.copy(
            outcomes = state.outcomes + dependents + (step to StepOutcome(SetupStatus.SKIPPED, "Übersprungen.", nowMs))
        )
    }

    fun missingItems(state: SetupState): List<String> =
        SetupStep.entries.filter { it != SetupStep.REPORT }.mapNotNull { step ->
            val head = "Etappe ${step.number} (${step.title})"
            when (state.statusOf(step)) {
                SetupStatus.OK -> null
                SetupStatus.OPEN -> "$head: noch nicht geprüft."
                SetupStatus.SKIPPED -> "$head: übersprungen."
                else -> "$head: ${state.outcomes[step]?.message}"
            }
        }

    fun formatReport(
        state: SetupState,
        environment: ProbeEnvironment,
        timestamp: String,
        timeLabel: (Long) -> String
    ): String = buildString {
        appendLine("LARA Einrichtungsbericht · $timestamp")
        appendLine("App: ${environment.packageName} ${environment.versionName} · Build ${environment.buildSha}")
        appendLine("Gerät: ${environment.device} · Android ${environment.androidRelease} (API ${environment.sdkInt})")
        appendLine()
        appendLine("Stand: ${state.doneCount} von ${SetupStep.entries.size} Etappen erledigt")
        appendLine()
        SetupStep.entries.forEach { step ->
            val outcome = state.outcomes[step]
            appendLine("${step.number}. ${step.title}: ${state.statusOf(step).label}" + (outcome?.let { " (${timeLabel(it.atMs)})" } ?: ""))
            outcome?.let { appendLine("   ${it.message}") }
        }
        val missing = missingItems(state)
        if (missing.isNotEmpty()) {
            appendLine()
            appendLine("Was noch gebraucht wird:")
            missing.forEach { appendLine("- $it") }
        }
        appendLine()
        appendLine("Noch nicht in LARA gebaut oder getestet:")
        NOT_BUILT.forEach { appendLine("- $it") }
        appendLine()
        append("Hinweis: Dieser Bericht enthält keine Schlüssel und keine Chat-Inhalte.")
    }
}

/** Runs one step. Blocking work happens inside [SetupActions]. */
internal class SetupRunner(
    private val actions: SetupActions,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    private val timestamp: () -> String = { "" },
    private val timeLabel: (Long) -> String = { it.toString() }
) {
    suspend fun run(step: SetupStep, state: SetupState): SetupRun {
        step.requires?.let { needed ->
            if (state.statusOf(needed) != SetupStatus.OK) {
                return done(state, step, SetupStatus.NEEDS_USER, "Zuerst muss Etappe ${needed.number} (${needed.title}) klappen.")
            }
        }
        val base = SetupWizard.clearDependents(state, step)
        return when (step) {
            SetupStep.APP_DEVICE -> checkDevice(base, step)
            SetupStep.LOCAL_SERVER -> findLocalServer(base, step)
            SetupStep.LOCAL_MODEL -> saveModel(base, step)
            SetupStep.LOCAL_CHAT -> sendTestMessage(base, step)
            SetupStep.ALPINE_START -> copyCommand(base, step)
            SetupStep.ALPINE_CHECK -> checkAlpine(base, step)
            SetupStep.AUTOMATE -> checkAutomate(base, step)
            SetupStep.CLAUDE, SetupStep.CHATGPT -> checkProvider(base, step, requireNotNull(step.provider))
            SetupStep.REPORT -> sendReport(base, step)
        }
    }

    private fun done(state: SetupState, step: SetupStep, status: SetupStatus, message: String): SetupRun =
        SetupRun(state.copy(outcomes = state.outcomes + (step to StepOutcome(status, message, nowMs()))))

    private fun checkDevice(state: SetupState, step: SetupStep): SetupRun {
        val facts = actions.deviceFacts()
        val lock = if (facts.deviceLock) "Displaysperre ist eingerichtet." else "Keine Displaysperre: API-Schlüssel lassen sich so nicht speichern."
        return done(state, step, SetupStatus.OK, "${facts.summary}. $lock")
    }

    private suspend fun findLocalServer(state: SetupState, step: SetupStep): SetupRun {
        val scan = actions.scanLocalServer()
        return if (scan.result == ProbeResult.OK && scan.models.isNotEmpty()) {
            done(state.copy(localModels = scan.models), step, SetupStatus.OK, scan.message)
        } else {
            done(state.copy(localModels = emptyList()), step, SetupStatus.FAILED, scan.message)
        }
    }

    private fun saveModel(state: SetupState, step: SetupStep): SetupRun {
        val chosen = SetupWizard.chooseLocalModel(state.localModels)
        return when {
            chosen.isBlank() -> done(state, step, SetupStatus.FAILED, "Etappe 2 hat kein Modell geliefert.")
            !actions.saveLocalModel(chosen) -> done(state, step, SetupStatus.FAILED, "Das Modell konnte nicht gespeichert werden.")
            else -> done(state.copy(chosenModel = chosen), step, SetupStatus.OK, "Für den Chat gespeichert: $chosen")
        }
    }

    private suspend fun sendTestMessage(state: SetupState, step: SetupStep): SetupRun {
        if (state.chosenModel.isBlank()) return done(state, step, SetupStatus.FAILED, "Etappe 3 hat kein Modell gespeichert.")
        return try {
            val answer = actions.chatLocal(state.chosenModel)
            if (SetupWizard.isRealAnswer(answer)) {
                done(state, step, SetupStatus.OK, "Die lokale KI hat geantwortet.").copy(shownAnswer = answer.trim().take(MAX_ANSWER))
            } else {
                done(state, step, SetupStatus.FAILED, "Die KI hat keine verwertbare Antwort geschickt.")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            done(state, step, SetupStatus.FAILED, SetupErrors.safeReason("Lokale KI", e))
        }
    }

    private fun copyCommand(state: SetupState, step: SetupStep): SetupRun =
        if (actions.copyAlpineCommand(SetupWizard.ALPINE_COMMAND)) {
            done(state, step, SetupStatus.OK, "Befehl „${SetupWizard.ALPINE_COMMAND}“ kopiert. Ob Ollama wirklich läuft, zeigt erst die nächste Etappe.")
        } else {
            done(state, step, SetupStatus.FAILED, "Der Befehl konnte nicht kopiert werden.")
        }

    private suspend fun checkAlpine(state: SetupState, step: SetupStep): SetupRun {
        val report = actions.probeAlpine()
        return when {
            report.ollamaReached && report.expectedModelFound -> {
                val mcp = report.steps.lastOrNull()?.result?.label ?: "nicht geprüft"
                done(state, step, SetupStatus.OK, "Ollama in Alpine antwortet (Version ${report.ollamaVersion}), Modell ${report.expectedModel} ist da. MCP-Endpunkt: $mcp.")
            }
            report.ollamaReached -> done(
                state, step, SetupStatus.NEEDS_USER,
                "Ollama läuft, aber das Modell ${report.expectedModel} fehlt. In Alpine eingeben: ollama pull ${report.expectedModel}"
            )
            else -> done(
                state, step, SetupStatus.FAILED,
                report.steps.firstOrNull { it.result != ProbeResult.OK }?.message ?: report.verdict
            )
        }
    }

    private fun checkAutomate(state: SetupState, step: SetupStep): SetupRun {
        val facts = actions.automateFacts()
        if (!facts.installed) return done(state, step, SetupStatus.NEEDS_USER, "Automate ist nicht installiert.")
        val flow = if (facts.flowSaved) "Eine Flow-Adresse ist gespeichert." else "Eine Flow-Adresse fehlt noch."
        return done(state, step, SetupStatus.OK, "Automate ist installiert (Version ${facts.version ?: "unbekannt"}). $flow Gestartet wurde nichts.")
    }

    private suspend fun checkProvider(state: SetupState, step: SetupStep, provider: AiProvider): SetupRun {
        if (!actions.hasKey(provider)) return done(state, step, SetupStatus.NEEDS_USER, "Für ${provider.label} fehlt noch ein API-Schlüssel.")
        if (!actions.isUnlocked()) return done(state, step, SetupStatus.NEEDS_USER, "LARA ist gesperrt. Bitte entsperren und noch einmal versuchen.")
        return try {
            val count = actions.listProviderModels(provider).size
            if (count > 0) {
                done(state, step, SetupStatus.OK, "${provider.label} ist erreichbar, $count Modelle sichtbar. Ein Text wurde nicht gesendet.")
            } else {
                done(state, step, SetupStatus.FAILED, "${provider.label} hat keine Modelle gemeldet.")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            done(state, step, SetupStatus.FAILED, SetupErrors.safeReason(provider.label, e))
        }
    }

    private fun sendReport(state: SetupState, step: SetupStep): SetupRun {
        val text = SetupWizard.formatReport(state, actions.environment(), timestamp(), timeLabel)
        return if (actions.shareReport(text)) {
            done(state, step, SetupStatus.OK, "Teilen-Menü geöffnet. Ob der Bericht ankommt, kann LARA nicht sehen.")
        } else {
            done(state, step, SetupStatus.FAILED, "Das Teilen-Menü ließ sich nicht öffnen. Bitte den Bericht kopieren.")
        }
    }

    private companion object {
        const val MAX_ANSWER = 2_000
    }
}

/** Status lines and model names only. Tolerates old or damaged data by starting fresh. */
internal object SetupStateCodec {
    private const val MAX_MESSAGE = 400

    fun encode(state: SetupState): String {
        val outcomes = JSONObject()
        state.outcomes.forEach { (step, outcome) ->
            outcomes.put(
                step.name,
                JSONObject()
                    .put("status", outcome.status.name)
                    .put("message", outcome.message.take(MAX_MESSAGE))
                    .put("at", outcome.atMs)
            )
        }
        val models = JSONArray()
        state.localModels.forEach { models.put(it) }
        return JSONObject()
            .put("outcomes", outcomes)
            .put("models", models)
            .put("chosen", state.chosenModel)
            .toString()
    }

    fun decode(raw: String?): SetupState {
        if (raw.isNullOrBlank()) return SetupState()
        return runCatching {
            val root = JSONObject(raw)
            val saved = root.optJSONObject("outcomes") ?: JSONObject()
            val outcomes = SetupStep.entries.mapNotNull { step ->
                val item = saved.optJSONObject(step.name) ?: return@mapNotNull null
                val status = SetupStatus.entries.firstOrNull { it.name == item.optString("status") } ?: return@mapNotNull null
                step to StepOutcome(status, item.optString("message"), item.optLong("at"))
            }.toMap()
            val list = root.optJSONArray("models") ?: JSONArray()
            SetupState(
                outcomes = outcomes,
                localModels = (0 until list.length()).map { list.optString(it) }.filter { it.isNotBlank() },
                chosenModel = root.optString("chosen")
            )
        }.getOrDefault(SetupState())
    }
}

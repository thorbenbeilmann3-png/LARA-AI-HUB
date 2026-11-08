package de.laraai.hub

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.biometric.BiometricManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val CLAUDE_KEYS_URL = "https://platform.claude.com"
private const val OPENAI_KEYS_URL = "https://platform.openai.com/api-keys"

/** Progress survives leaving the app. Contains status lines only, no keys. */
internal object SetupStore {
    private const val PREFS = "lara_setup_wizard"
    private const val KEY = "state"

    fun load(context: Context): SetupState =
        SetupStateCodec.decode(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null))

    fun save(context: Context, state: SetupState) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, SetupStateCodec.encode(state)).apply()
    }
}

private class AndroidSetupActions(
    private val context: Context,
    private val automateFlowSaved: () -> Boolean,
    private val onLocalModelSaved: (String, String) -> Unit
) : SetupActions {
    private val transport = OkHttpProbeTransport()
    private val probe = AlpineProbe(transport)
    private val scanner = LocalModelScan(transport)

    override fun environment(): ProbeEnvironment = probeEnvironment(context)

    override fun deviceFacts(): SetupDeviceFacts {
        val env = environment()
        val lock = BiometricManager.from(context).canAuthenticate(laraAuthenticators()) == BiometricManager.BIOMETRIC_SUCCESS
        return SetupDeviceFacts("${env.device}, Android ${env.androidRelease} (API ${env.sdkInt}), Build ${env.buildSha}", lock)
    }

    override suspend fun scanLocalServer(): LocalScan =
        withContext(Dispatchers.IO) { scanner.scan(SetupWizard.LOCAL_BASE_URL) }

    /** Runs on the main thread, because it also updates the app's visible settings. */
    override fun saveLocalModel(model: String): Boolean {
        onLocalModelSaved(AiProvider.OLLAMA.defaultEndpoint, model)
        val prefs = context.getSharedPreferences("lara_hub_config", Context.MODE_PRIVATE)
        return prefs.getString("model", null) == model && prefs.getString("provider_id", null) == AiProvider.OLLAMA.id
    }

    // The same call the chat page uses, so success here means the chat path works.
    override suspend fun chatLocal(model: String): String = withContext(Dispatchers.IO) {
        ApiConnection.chat(
            context,
            AiProvider.OLLAMA,
            AiProvider.OLLAMA.defaultEndpoint,
            model,
            SetupWizard.LOCAL_PROMPT,
            ApiMode.OPENAI_COMPATIBLE
        )
    }

    override fun copyAlpineCommand(command: String): Boolean {
        copyText(context, command)
        return true
    }

    override suspend fun probeAlpine(): ProbeReport = withContext(Dispatchers.IO) {
        probe.run(AlpineProbe.DEFAULT_OLLAMA_URL, AlpineProbe.DEFAULT_MCP_URL, AlpineProbe.DEFAULT_MODEL)
    }

    override fun automateFacts(): SetupAutomateFacts {
        val packages = context.packageManager
        val installed = packages.getLaunchIntentForPackage(SetupWizard.AUTOMATE_PACKAGE) != null
        val version = if (installed) {
            runCatching { packages.getPackageInfo(SetupWizard.AUTOMATE_PACKAGE, 0).versionName }.getOrNull()
        } else {
            null
        }
        return SetupAutomateFacts(installed, version, automateFlowSaved())
    }

    override fun hasKey(provider: AiProvider): Boolean = ApiKeyStore.hasSaved(context, provider.id)

    override fun isUnlocked(): Boolean = SessionVault.isUnlocked

    override suspend fun listProviderModels(provider: AiProvider): List<String> = withContext(Dispatchers.IO) {
        ApiConnection.discover(context, provider, provider.defaultEndpoint).models
    }

    override fun shareReport(text: String): Boolean = try {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "LARA Einrichtungsbericht")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(send, "Bericht senden an"))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

private fun setupTimestamp(): String = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.GERMANY).format(Date())

private fun setupTimeLabel(ms: Long): String = SimpleDateFormat("dd.MM. HH:mm", Locale.GERMANY).format(Date(ms))

private fun statusColor(status: SetupStatus): Color = when (status) {
    SetupStatus.OK -> LaraColors.Ok
    SetupStatus.FAILED -> LaraColors.Error
    SetupStatus.NEEDS_USER -> LaraColors.Warn
    SetupStatus.OPEN, SetupStatus.SKIPPED -> LaraColors.TextMuted
}

private fun mainButtonLabel(step: SetupStep, status: SetupStatus, running: Boolean): String = when {
    running -> "Läuft …"
    step == SetupStep.REPORT -> "Bericht senden"
    status == SetupStatus.FAILED || status == SetupStatus.NEEDS_USER -> "Nochmal versuchen"
    status == SetupStatus.OK -> "Nochmal prüfen"
    else -> "Weiter"
}

private fun openLink(context: Context, url: String): Boolean = try {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    true
} catch (_: ActivityNotFoundException) {
    false
}

/**
 * One button per stage. "Weiter" moves on only after the stage really worked; otherwise
 * the reason stays on screen. Skipped stages are shown and reported as skipped.
 */
@Composable
internal fun SetupWizardPage(
    context: Context,
    auth: AuthRequester,
    automateFlowSaved: Boolean,
    onLocalModelSaved: (String, String) -> Unit
) {
    val flowSaved by rememberUpdatedState(automateFlowSaved)
    val saveModel by rememberUpdatedState(onLocalModelSaved)
    val runner = remember {
        SetupRunner(
            actions = AndroidSetupActions(context, { flowSaved }, { url, chosen -> saveModel(url, chosen) }),
            timestamp = ::setupTimestamp,
            timeLabel = ::setupTimeLabel
        )
    }
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(SetupStore.load(context)) }
    var selected by remember { mutableStateOf<SetupStep?>(null) }
    var running by remember { mutableStateOf(false) }
    var answer by remember { mutableStateOf<String?>(null) }
    var keyInput by remember { mutableStateOf("") }
    var keyNotice by remember { mutableStateOf<String?>(null) }
    var copied by remember { mutableStateOf(false) }
    val current = selected ?: state.next ?: SetupStep.REPORT
    val status = state.statusOf(current)

    fun store(next: SetupState) {
        state = next
        SetupStore.save(context, next)
    }

    fun select(step: SetupStep?) {
        selected = step
        keyInput = ""
        keyNotice = null
    }

    fun runStep(step: SetupStep) {
        if (running) return
        running = true
        answer = null
        copied = false
        scope.launch {
            try {
                val result = runner.run(step, state)
                store(result.state)
                answer = result.shownAnswer
                select(if (result.state.statusOf(step) == SetupStatus.OK) null else step)
            } finally {
                running = false
            }
        }
    }

    Text("Einrichtung", style = MaterialTheme.typography.titleLarge, color = LaraColors.Gold)
    Text("${state.doneCount} von ${SetupStep.entries.size} Etappen erledigt", style = MaterialTheme.typography.bodyMedium)

    GlassCard(Modifier.fillMaxWidth(), accent = LaraColors.Gold) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Etappe ${current.number}: ${current.title}", style = MaterialTheme.typography.titleMedium, color = LaraColors.Gold)
            Text(current.explanation, style = MaterialTheme.typography.bodyMedium)
            state.outcomes[current]?.let { Text(it.message, style = MaterialTheme.typography.bodyMedium, color = statusColor(it.status)) }

            if (current == SetupStep.AUTOMATE && status == SetupStatus.NEEDS_USER) {
                OutlinedButton(
                    onClick = { openLink(context, SetupWizard.AUTOMATE_STORE_URL) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Automate im Play Store öffnen") }
            }

            current.provider?.let { provider ->
                val hasKey = remember(provider, keyNotice) { ApiKeyStore.hasSaved(context, provider.id) }
                if (!hasKey || status == SetupStatus.FAILED) {
                    Text(
                        "Den Schlüssel bekommst du beim Anbieter. Er bleibt verschlüsselt auf diesem Handy und wird nur an den Anbieter gesendet. " +
                            "Die Nutzung kann dort Geld kosten, diese Prüfung sendet aber keinen Text.",
                        style = MaterialTheme.typography.bodySmall,
                        color = LaraColors.TextMuted
                    )
                    OutlinedButton(
                        onClick = {
                            val url = if (provider == AiProvider.ANTHROPIC) CLAUDE_KEYS_URL else OPENAI_KEYS_URL
                            if (!openLink(context, url)) keyNotice = "Die Seite ließ sich nicht öffnen."
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Schlüssel-Seite öffnen") }
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        label = { Text("API-Schlüssel einfügen") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedButton(
                        onClick = {
                            auth.request(
                                "API-Schlüssel speichern bestätigen",
                                onSuccess = {
                                    keyNotice = try {
                                        ApiKeyStore.save(context, provider.id, keyInput.trim())
                                        keyInput = ""
                                        "Schlüssel verschlüsselt gespeichert. Jetzt noch einmal prüfen."
                                    } catch (e: Exception) {
                                        e.message ?: "Speichern fehlgeschlagen."
                                    }
                                },
                                onFailure = { keyNotice = it }
                            )
                        },
                        enabled = keyInput.isNotBlank() && !running,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Schlüssel sicher speichern") }
                }
                keyNotice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = LaraColors.Cyan) }
            }

            Button(onClick = { runStep(current) }, enabled = !running, modifier = Modifier.fillMaxWidth()) {
                Text(mainButtonLabel(current, status, running))
            }
            if (current.skippable) {
                TextButton(
                    onClick = {
                        store(SetupWizard.skip(state, current, System.currentTimeMillis()))
                        answer = null
                        select(null)
                    },
                    enabled = !running,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Überspringen") }
            }
        }
    }

    answer?.let { text ->
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Echte Antwort der lokalen KI", style = MaterialTheme.typography.labelLarge, color = LaraColors.Cyan)
                Text(text)
            }
        }
    }

    SetupStep.entries.forEach { step ->
        val stepStatus = state.statusOf(step)
        Row(
            Modifier.fillMaxWidth().clickable(enabled = !running) { select(step) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${step.number}. ${step.title}",
                    fontWeight = if (step == current) FontWeight.SemiBold else FontWeight.Normal
                )
                state.outcomes[step]?.let {
                    Text(it.message, style = MaterialTheme.typography.bodySmall, color = LaraColors.TextMuted)
                }
            }
            Text(stepStatus.label, style = MaterialTheme.typography.labelMedium, color = statusColor(stepStatus))
        }
    }

    OutlinedButton(
        onClick = {
            copyText(context, SetupWizard.formatReport(state, probeEnvironment(context), setupTimestamp(), ::setupTimeLabel))
            copied = true
        },
        modifier = Modifier.fillMaxWidth()
    ) { Text(if (copied) "Bericht kopiert" else "Bericht kopieren") }
    TextButton(
        onClick = {
            store(SetupState())
            answer = null
            select(null)
        },
        enabled = !running,
        modifier = Modifier.fillMaxWidth()
    ) { Text("Von vorn beginnen") }
}

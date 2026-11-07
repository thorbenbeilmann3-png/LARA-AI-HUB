package de.laraai.hub

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private const val CONFIG = "lara_hub_config"

/**
 * "KI- und Auftragsmonitor": token usage of recent chat requests (as reported by the
 * provider, otherwise "unbekannt"), context gauge against a user-entered window, and
 * the job list of the LARA Core when it is running.
 */
@Composable
internal fun MonitorPage(context: Context, auth: AuthRequester, usage: List<TokenUsage>, currentModel: String) {
    val prefs = remember { context.getSharedPreferences(CONFIG, Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    val last = usage.lastOrNull()
    val modelForGauge = last?.model ?: currentModel
    var windowInput by remember(modelForGauge) {
        mutableStateOf(prefs.getInt("context_window.$modelForGauge", 0).takeIf { it > 0 }?.toString() ?: "")
    }
    var coreUrl by remember { mutableStateOf(prefs.getString("core_url", CoreClient.DEFAULT_URL) ?: CoreClient.DEFAULT_URL) }
    var coreTokenInput by remember { mutableStateOf("") }
    var coreStatus by remember { mutableStateOf("Noch nicht abgefragt.") }
    var jobs by remember { mutableStateOf(emptyList<CoreClient.JobSummary>()) }

    Text("KI- und Auftragsmonitor", style = MaterialTheme.typography.titleLarge, color = LaraColors.Gold)

    ProviderStatusCard(context)

    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Letzte Anfrage", style = MaterialTheme.typography.titleMedium)
            if (last == null) {
                Text("Noch keine Anfrage in diesem Gerät gespeichert.")
            } else {
                Text("Modell: ${last.model} · ${last.provider}")
                Text("Eingabe: ${last.inputTokens ?: "unbekannt"} · Ausgabe: ${last.outputTokens ?: "unbekannt"} Tokens")
                Text(if (last.source == "api") "Quelle: vom Anbieter gemeldet" else "Quelle: Server liefert keine Tokenzahlen")
            }
            val totals = UsageLog.totals(usage)
            Text("Gespeichert: ${totals.requests} Anfragen · ${totals.input} Eingabe- / ${totals.output} Ausgabe-Tokens" +
                if (totals.withoutCounts > 0) " · ${totals.withoutCounts} ohne Zahlen" else "")
        }
    }

    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Kontext · $modelForGauge", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = windowInput,
                onValueChange = { v -> windowInput = v.filter { it.isDigit() }.take(9) },
                label = { Text("Kontextfenster in Tokens (Nutzerangabe)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedButton(onClick = {
                val value = windowInput.toIntOrNull()
                prefs.edit().apply {
                    if (value != null && value > 0) putInt("context_window.$modelForGauge", value)
                    else remove("context_window.$modelForGauge")
                }.apply()
            }, modifier = Modifier.fillMaxWidth()) { Text("Kontextfenster speichern") }
            val reading = ContextGauge.read(last?.takeIf { it.model == modelForGauge }?.total, windowInput.toIntOrNull())
            if (reading.percent != null) {
                LinearProgressIndicator(progress = { (reading.percent / 100.0).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                Text("Auslastung ${reading.percent} % · verbleibend ${reading.remaining} Tokens · Status: ${reading.status}")
            } else {
                Text("Auslastung: unbekannt (Kontextfenster oder Tokenzahl fehlt)")
            }
            Text("Hinweis: Kontextfenster und API-Guthaben sind verschiedene Dinge. Das Guthaben ist über normale API-Schlüssel nicht abrufbar.",
                style = MaterialTheme.typography.bodySmall)
        }
    }

    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Aufträge (LARA Core)", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(value = coreUrl, onValueChange = { coreUrl = it.trim() }, label = { Text("Core-Adresse") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = coreTokenInput, onValueChange = { coreTokenInput = it },
                label = { Text(if (ApiKeyStore.hasSaved(context, CoreClient.CREDENTIAL_ID)) "Core-Token (gespeichert)" else "Core-Token") },
                visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = {
                auth.request("Core-Token speichern bestätigen", onSuccess = {
                    coreStatus = try {
                        ApiKeyStore.save(context, CoreClient.CREDENTIAL_ID, coreTokenInput)
                        coreTokenInput = ""
                        "Core-Token verschlüsselt gespeichert."
                    } catch (e: Exception) { e.message ?: "Speichern fehlgeschlagen." }
                }, onFailure = { coreStatus = it })
            }, enabled = coreTokenInput.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Core-Token sicher speichern") }
            Button(onClick = {
                prefs.edit().putString("core_url", coreUrl).apply()
                coreStatus = "Frage Core ab …"
                scope.launch {
                    coreStatus = try {
                        val token = ApiKeyStore.load(context, CoreClient.CREDENTIAL_ID)
                            ?: error("Bitte zuerst den Core-Token speichern.")
                        jobs = CoreClient.listJobs(coreUrl, token)
                        "${jobs.size} Auftrag/Aufträge geladen."
                    } catch (e: Exception) {
                        "Core nicht erreichbar: ${e.message ?: e.javaClass.simpleName}"
                    }
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Aufträge laden") }
            Text(coreStatus, style = MaterialTheme.typography.bodySmall)
            jobs.forEach { job ->
                Text(job.title, style = MaterialTheme.typography.titleSmall)
                LinearProgressIndicator(progress = { (job.percent / 100.0).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                Text("${job.percent} % · ${job.state}" +
                    (job.currentStep?.let { " · aktuell: $it" } ?: "") +
                    (job.nextStep?.let { " · nächster Schritt: $it" } ?: "") +
                    (job.activeModel?.let { " · Modell: $it" } ?: ""), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/**
 * Connection test per provider (fetches only the model list, no chat request).
 * A provider is shown as available only after a successful test within 24 h.
 */
@Composable
private fun ProviderStatusCard(context: Context) {
    val prefs = remember { context.getSharedPreferences(CONFIG, Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    var checks by remember {
        mutableStateOf(AiProvider.entries.associate { it.id to ProviderStatusStore.load(context, it.id) })
    }
    var running by remember { mutableStateOf<String?>(null) }

    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Anbieter-Status", style = MaterialTheme.typography.titleMedium)
            Text("„Testen“ ruft nur die Modellliste ab (kein Chat). Verfügbar heißt: erfolgreich geprüft in den letzten 24 Stunden.",
                style = MaterialTheme.typography.bodySmall, color = LaraColors.TextMuted)
            AiProvider.entries.forEach { provider ->
                val check = checks[provider.id]
                val state = ProviderAvailability.state(check, System.currentTimeMillis())
                val color = when (state) {
                    ProviderAvailability.State.VERFUEGBAR -> LaraColors.Ok
                    ProviderAvailability.State.FEHLER -> LaraColors.Error
                    else -> LaraColors.TextMuted
                }
                Column(Modifier.fillMaxWidth()) {
                    androidx.compose.foundation.layout.Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Text(provider.label, style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(
                            enabled = running == null,
                            onClick = {
                                val saved = prefs.getString("endpoint", "") ?: ""
                                val endpoint = if (prefs.getString("provider_id", "") == provider.id && saved.isNotBlank()) saved
                                    else provider.defaultEndpoint
                                running = provider.id
                                scope.launch {
                                    val now = System.currentTimeMillis()
                                    val result = if (endpoint.isBlank()) {
                                        ProviderAvailability.merge(check, provider.id, false, "Adresse fehlt – unter Tools eintragen.", now)
                                    } else {
                                        try {
                                            val d = ApiConnection.discover(context, provider, endpoint)
                                            ProviderAvailability.merge(check, provider.id, true, "${d.models.size} Modelle gefunden", now)
                                        } catch (e: Exception) {
                                            ProviderAvailability.merge(check, provider.id, false, ProviderErrors.describe(provider.label, e), now)
                                        }
                                    }
                                    ProviderStatusStore.save(context, result)
                                    checks = checks + (provider.id to result)
                                    running = null
                                }
                            }
                        ) { Text(if (running == provider.id) "Prüfe …" else "Testen") }
                    }
                    Text(ProviderAvailability.label(state) + (check?.let { " · " + it.message } ?: ""),
                        color = color, style = MaterialTheme.typography.bodySmall)
                    check?.lastSuccessMs?.let {
                        Text("Zuletzt erfolgreich: " + java.text.SimpleDateFormat("dd.MM. HH:mm", java.util.Locale.GERMANY).format(java.util.Date(it)),
                            color = LaraColors.TextMuted, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}


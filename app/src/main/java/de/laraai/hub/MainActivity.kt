package de.laraai.hub

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LaraHubApp() }
    }
}

private data class ChatLine(val role: String, val text: String, val time: String)

private data class LaraConfig(
    val providerId: String,
    val endpoint: String,
    val model: String,
    val automateFlowUri: String,
    val autoStartAutomate: Boolean
)

private fun loadConfig(context: Context): LaraConfig {
    val prefs = context.getSharedPreferences("lara_hub_config", Context.MODE_PRIVATE)
    val defaultEndpoint = "http://10.179.130.230:12434/"
    val endpoint = prefs.getString("endpoint", defaultEndpoint) ?: defaultEndpoint
    val inferredProvider = if (
        endpoint.startsWith("https://", ignoreCase = true) && ApiKeyStore.hasSaved(context)
    ) "custom" else "ollama"
    return LaraConfig(
        providerId = prefs.getString("provider_id", inferredProvider) ?: inferredProvider,
        endpoint = endpoint,
        model = prefs.getString("model", "") ?: "",
        automateFlowUri = prefs.getString("automate_flow_uri", "") ?: "",
        autoStartAutomate = prefs.getBoolean("auto_start_automate", false)
    )
}

private fun saveConfig(
    context: Context,
    providerId: String,
    endpoint: String,
    model: String,
    flowUri: String,
    autoStartAutomate: Boolean
) {
    context.getSharedPreferences("lara_hub_config", Context.MODE_PRIVATE).edit()
        .putString("provider_id", providerId)
        .putString("endpoint", endpoint.trim())
        .putString("model", model.trim())
        .putString("automate_flow_uri", flowUri.trim())
        .putBoolean("auto_start_automate", autoStartAutomate)
        .apply()
}

private fun loadMessages(context: Context): List<ChatLine> {
    val raw = context.getSharedPreferences("lara_hub", Context.MODE_PRIVATE)
        .getString("mailbox", "[]") ?: "[]"
    return try {
        val array = JSONArray(raw)
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            ChatLine(item.optString("role"), item.optString("text"), item.optString("time"))
        }
    } catch (_: Exception) {
        emptyList()
    }
}

private fun saveMessages(context: Context, messages: List<ChatLine>) {
    val array = JSONArray()
    messages.takeLast(500).forEach { entry ->
        array.put(
            JSONObject()
                .put("role", entry.role)
                .put("text", entry.text)
                .put("time", entry.time)
        )
    }
    context.getSharedPreferences("lara_hub", Context.MODE_PRIVATE)
        .edit().putString("mailbox", array.toString()).apply()
}

private fun nowLabel(): String =
    SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(Date())

private fun copyText(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("LARA", text))
}

private fun openAutomate(context: Context, flowUri: String): String {
    return try {
        if (flowUri.isNotBlank()) {
            require(flowUri.startsWith("content://com.llamalab.automate.provider/flows/")) {
                "Die Flow-Adresse muss aus dem Flow-Beginnungsblock von Automate stammen."
            }
            val intent = Intent("com.llamalab.automate.intent.action.START_FLOW").apply {
                setPackage("com.llamalab.automate")
                data = Uri.parse(flowUri)
            }
            try {
                context.startActivity(intent)
                "Automate-Flow wurde an Android übergeben. Der Startstatus muss in Automate geprüft werden."
            } catch (_: Exception) {
                val launchIntent = context.packageManager.getLaunchIntentForPackage("com.llamalab.automate")
                    ?: throw IllegalStateException("Automate ist installiert, aber nicht startbar.")
                context.startActivity(launchIntent)
                "Automate geöffnet. Android hat den direkten Flow-Aufruf nicht angenommen; bitte den Flow dort manuell starten."
            }
        } else {
            val launchIntent = context.packageManager.getLaunchIntentForPackage("com.llamalab.automate")
                ?: error("Automate ist auf diesem Gerät nicht startbar.")
            context.startActivity(launchIntent)
            "Automate geöffnet. Trage anschließend die Flow-Adresse in LARA ein."
        }
    } catch (e: Exception) {
        "Automate konnte nicht gestartet werden: ${e.message ?: "unbekannter Fehler"}"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LaraHubApp() {
    val context = LocalContext.current
    val initialConfig = remember { loadConfig(context) }
    var selectedProvider by remember { mutableStateOf(AiProvider.fromId(initialConfig.providerId)) }
    var providerMenuExpanded by remember { mutableStateOf(false) }
    var endpoint by remember { mutableStateOf(initialConfig.endpoint) }
    var model by remember { mutableStateOf(initialConfig.model) }
    var automateFlowUri by remember { mutableStateOf(initialConfig.automateFlowUri) }
    var autoStartAutomate by remember { mutableStateOf(initialConfig.autoStartAutomate) }
    var apiKeyInput by remember { mutableStateOf("") }
    val initialKeySaved = ApiKeyStore.hasSaved(context, selectedProvider.id) ||
        (selectedProvider == AiProvider.CUSTOM && ApiKeyStore.hasSaved(context))
    var hasSavedApiKey by remember { mutableStateOf(initialKeySaved) }
    var apiKeyStatus by remember {
        mutableStateOf(
            when {
                initialKeySaved -> "Zugangsdaten sind verschlüsselt auf diesem Gerät gespeichert."
                selectedProvider.requiresApiKey -> "Für diesen Anbieter muss ein API-Schlüssel gespeichert werden."
                selectedProvider == AiProvider.CUSTOM -> "Ein API-Schlüssel ist optional; falls gesetzt, wird er verschlüsselt gespeichert."
                else -> "Ollama benötigt normalerweise keinen API-Schlüssel."
            }
        )
    }
    var prompt by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Noch nicht verbunden – Verbindung zuerst testen.") }
    var selectedTab by remember { mutableIntStateOf(0) }
    var apiMode by remember { mutableStateOf(ApiMode.OPENAI_COMPATIBLE) }
    var availableModels by remember { mutableStateOf(emptyList<String>()) }
    var showModelPicker by remember { mutableStateOf(false) }
    val lines = remember { mutableStateListOf<ChatLine>().apply { addAll(loadMessages(context)) } }
    val scope = rememberCoroutineScope()

    fun addMessage(role: String, text: String) {
        lines.add(ChatLine(role, text, nowLabel()))
        saveMessages(context, lines)
    }

    fun persistConfig() {
        saveConfig(context, selectedProvider.id, endpoint, model, automateFlowUri, autoStartAutomate)
    }

    fun refreshCredentialStatus(provider: AiProvider) {
        val saved = ApiKeyStore.hasSaved(context, provider.id) ||
            (provider == AiProvider.CUSTOM && ApiKeyStore.hasSaved(context))
        hasSavedApiKey = saved
        apiKeyStatus = when {
            saved -> "Zugangsdaten sind verschlüsselt auf diesem Gerät gespeichert."
            provider.requiresApiKey -> "Für diesen Anbieter muss ein API-Schlüssel gespeichert werden."
            provider == AiProvider.CUSTOM -> "Ein API-Schlüssel ist optional; falls gesetzt, wird er verschlüsselt gespeichert."
            else -> "Ollama benötigt normalerweise keinen API-Schlüssel."
        }
    }

    fun checkConnection() {
        if (checking || busy) return
        checking = true
        status = "Prüfe ${selectedProvider.label} und suche verfügbare Modelle …"
        scope.launch {
            try {
                val result = ApiConnection.discover(context, selectedProvider, endpoint)
                apiMode = result.mode
                availableModels = result.models
                if (model.isBlank() || (result.models.isNotEmpty() && model !in result.models)) {
                    model = result.models.firstOrNull().orEmpty()
                }
                persistConfig()
                val apiLabel = if (selectedProvider == AiProvider.OLLAMA) {
                    if (result.mode == ApiMode.OLLAMA_NATIVE) "Ollama API" else "Ollama · OpenAI-kompatibel"
                } else selectedProvider.label
                status = "Verbunden: $apiLabel · ${result.models.size} Modell(e) gefunden"
            } catch (e: Exception) {
                status = "Verbindung fehlgeschlagen: ${e.message ?: "Unbekannter Fehler"}"
            } finally {
                checking = false
            }
        }
    }

    LaunchedEffect(Unit) {
        if (autoStartAutomate && automateFlowUri.isNotBlank()) {
            status = openAutomate(context, automateFlowUri)
        }
    }

    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("LARA AI HUB")
                            Text("Lokale und externe KI-Zentrale", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                )
            },
            bottomBar = {
                NavigationBar {
                    listOf("Chat", "Postfach", "ARENA", "PC").forEachIndexed { index, label ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = { Text(listOf("✦", "✉", "⚔", "⌘")[index]) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        ) { padding ->
            val pageModifier = if (selectedTab == 3) Modifier.verticalScroll(rememberScrollState()) else Modifier
            Column(
                Modifier.fillMaxSize().padding(padding).padding(16.dp).then(pageModifier),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (selectedTab) {
                    0 -> {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("KI-Chat", style = MaterialTheme.typography.titleLarge)
                            TextButton(onClick = { checkConnection() }, enabled = !checking && !busy) {
                                Text(if (checking) "Prüfe …" else "Verbindung testen")
                            }
                        }
                        Text(status, style = MaterialTheme.typography.bodySmall)
                        LazyColumn(
                            Modifier.weight(1f).fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(lines.takeLast(80)) { entry ->
                                Card {
                                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("${entry.role} · ${entry.time}", style = MaterialTheme.typography.labelSmall)
                                            TextButton(onClick = { copyText(context, entry.text) }) { Text("Kopieren") }
                                        }
                                        Text(entry.text)
                                    }
                                }
                            }
                        }
                        OutlinedTextField(
                            value = prompt,
                            onValueChange = { prompt = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Nachricht an die KI") },
                            minLines = 2
                        )
                        Button(
                            onClick = {
                                val question = prompt.trim()
                                if (question.isNotEmpty() && !busy) {
                                    prompt = ""
                                    addMessage("Du", question)
                                    busy = true
                                    status = "Verbinde und sende Anfrage …"
                                    scope.launch {
                                        try {
                                            val discovery = ApiConnection.discover(context, selectedProvider, endpoint)
                                            apiMode = discovery.mode
                                            if (availableModels != discovery.models) availableModels = discovery.models
                                            val selectedModel = model.ifBlank { discovery.models.firstOrNull().orEmpty() }
                                            if (selectedModel.isBlank()) error("Der Server meldet keine Modelle. Bitte Modellnamen prüfen.")
                                            if (model.isBlank()) model = selectedModel
                                            val answer = ApiConnection.chat(context, selectedProvider, endpoint, selectedModel, question, discovery.mode)
                                            persistConfig()
                                            addMessage("LARA / $selectedModel", answer)
                                            val apiLabel = if (selectedProvider == AiProvider.OLLAMA) "Ollama" else selectedProvider.label
                                            status = "Antwort erhalten · $apiLabel"
                                        } catch (e: Exception) {
                                            status = "Fehler: ${e.message ?: "Verbindung fehlgeschlagen"}"
                                        } finally {
                                            busy = false
                                        }
                                    }
                                }
                            },
                            enabled = !busy && !checking,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (busy) "Bitte warten …" else "Senden")
                        }
                    }

                    1 -> {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("KI-Postfach", style = MaterialTheme.typography.titleLarge)
                            TextButton(onClick = { lines.clear(); saveMessages(context, lines) }) { Text("Leeren") }
                        }
                        Text("Ein- und Ausgang · lokal auf diesem Handy gespeichert")
                        if (lines.isEmpty()) {
                            Text("Noch keine Nachrichten. Gesendete Fragen und KI-Antworten erscheinen hier.")
                        } else {
                            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(lines.asReversed()) { entry ->
                                    Card {
                                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text(entry.role, style = MaterialTheme.typography.labelLarge)
                                                TextButton(onClick = { copyText(context, entry.text) }) { Text("Kopieren") }
                                            }
                                            Text(entry.time, style = MaterialTheme.typography.labelSmall)
                                            Spacer(Modifier.height(4.dp))
                                            Text(entry.text)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    2 -> {
                        Text("ARENA", style = MaterialTheme.typography.titleLarge)
                        Text("KI-Wettkampf · bis zu 30 Teilnehmerplätze")
                        Card {
                            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Wettkampfablauf", style = MaterialTheme.typography.titleMedium)
                                Text("1. Aufgabe festlegen")
                                Text("2. Verfügbare Modelle anonym gegeneinander antreten lassen")
                                Text("3. Antworten vergleichen und bewerten")
                                Text("4. Qualifikation und Finale ausführen")
                                Text("5. Gewinner, Antwortqualität und Kosten dokumentieren")
                            }
                        }
                        Text("Status: Die Arena-Oberfläche ist vorbereitet. Modellverwaltung, Mehrfachaufrufe und Wertung sind noch nicht implementiert.")
                    }

                    3 -> {
                        Text("Verbindungen & Geräte", style = MaterialTheme.typography.titleLarge)
                        Text("KI-Anbieter auswählen", style = MaterialTheme.typography.titleMedium)
                        Box {
                            OutlinedButton(
                                onClick = { providerMenuExpanded = true },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(selectedProvider.label) }
                            DropdownMenu(
                                expanded = providerMenuExpanded,
                                onDismissRequest = { providerMenuExpanded = false }
                            ) {
                                AiProvider.entries.forEach { provider ->
                                    DropdownMenuItem(
                                        text = { Text(provider.label) },
                                        onClick = {
                                            providerMenuExpanded = false
                                            if (provider != selectedProvider) {
                                                selectedProvider = provider
                                                endpoint = provider.defaultEndpoint
                                                model = ""
                                                apiKeyInput = ""
                                                availableModels = emptyList()
                                                refreshCredentialStatus(provider)
                                                saveConfig(
                                                    context,
                                                    provider.id,
                                                    endpoint,
                                                    model,
                                                    automateFlowUri,
                                                    autoStartAutomate
                                                )
                                                status = "${provider.label} ausgewählt. Bitte Verbindung testen."
                                            }
                                        }
                                    )
                                }
                            }
                        }
                        OutlinedTextField(
                            value = endpoint,
                            onValueChange = { endpoint = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("API- / Server-Adresse") },
                            placeholder = {
                                Text(selectedProvider.defaultEndpoint.ifBlank { "https://dein-server.example/v1" })
                            },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = model,
                            onValueChange = { model = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Modellname") },
                            singleLine = true
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { checkConnection() }, enabled = !checking && !busy) {
                                Text(if (checking) "Prüfe …" else "Verbinden / Modelle laden")
                            }
                            if (availableModels.isNotEmpty()) {
                                OutlinedButton(onClick = { showModelPicker = true }) { Text("Modell wählen") }
                            }
                        }
                        Text(status, style = MaterialTheme.typography.bodySmall)
                        Divider()
                        if (selectedProvider.requiresApiKey || selectedProvider == AiProvider.CUSTOM) {
                            Text("Zugangsdaten · ${selectedProvider.label}", style = MaterialTheme.typography.titleMedium)
                            OutlinedTextField(
                                value = apiKeyInput,
                                onValueChange = { apiKeyInput = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("API-Schlüssel eingeben") },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true
                            )
                            Text(apiKeyStatus, style = MaterialTheme.typography.bodySmall)
                            Button(
                                onClick = {
                                    try {
                                        ApiKeyStore.save(context, selectedProvider.id, apiKeyInput)
                                        if (selectedProvider == AiProvider.CUSTOM && ApiKeyStore.hasSaved(context)) {
                                            ApiKeyStore.clear(context)
                                        }
                                        apiKeyInput = ""
                                        refreshCredentialStatus(selectedProvider)
                                    } catch (e: Exception) {
                                        apiKeyStatus = e.message ?: "Speichern fehlgeschlagen."
                                    }
                                },
                                enabled = apiKeyInput.isNotBlank(),
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Schlüssel sicher speichern") }
                            TextButton(
                                onClick = {
                                    try {
                                        ApiKeyStore.clear(context, selectedProvider.id)
                                        if (selectedProvider == AiProvider.CUSTOM && ApiKeyStore.hasSaved(context)) {
                                            ApiKeyStore.clear(context)
                                        }
                                        apiKeyInput = ""
                                        refreshCredentialStatus(selectedProvider)
                                    } catch (e: Exception) {
                                        apiKeyStatus = e.message ?: "Entfernen fehlgeschlagen."
                                    }
                                },
                                enabled = hasSavedApiKey,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Gespeicherten Schlüssel entfernen") }
                        } else {
                            Text("Ollama läuft lokal und benötigt normalerweise keinen API-Schlüssel.")
                        }
                        Text(
                            "Sicherheit: AES-GCM mit Geräteschlüssel im Android Keystore. " +
                                "Schlüssel bleiben lokal gespeichert und werden nur über HTTPS übertragen. " +
                                "Für die lokale Ollama-Verbindung bleibt das Schlüsselfeld leer.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Divider()
                        Text("Automate von LlamaLab", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(
                            value = automateFlowUri,
                            onValueChange = { automateFlowUri = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Flow-Adresse aus Automate (optional)") },
                            placeholder = { Text("content://com.llamalab.automate.provider/flows/…") },
                            singleLine = true
                        )
                        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Checkbox(
                                checked = autoStartAutomate,
                                onCheckedChange = { autoStartAutomate = it; persistConfig() }
                            )
                            Text("Automate-Flow beim Start von LARA automatisch ausführen")
                        }
                        OutlinedButton(
                            onClick = {
                                persistConfig()
                                status = openAutomate(context, automateFlowUri)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (automateFlowUri.isBlank()) "Automate öffnen" else "Automate-Flow jetzt starten") }
                        Text(
                            "Der Flow muss in Automate selbst angelegt sein: Flow-Beginn → App starten (Ollama-App) → HTTP-Anfrage an den Server prüfen. " +
                                "Die Flow-Adresse ist installationsgebunden und wird hier lokal gespeichert.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Button(onClick = { persistConfig(); status = "Verbindungseinstellungen gespeichert." }, modifier = Modifier.fillMaxWidth()) {
                            Text("Einstellungen speichern")
                        }
                    }
                }
            }
        }
    }

    if (showModelPicker) {
        AlertDialog(
            onDismissRequest = { showModelPicker = false },
            title = { Text("Verfügbares Modell auswählen") },
            text = {
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(availableModels) { item ->
                        TextButton(
                            onClick = {
                                model = item
                                persistConfig()
                                showModelPicker = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(item) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showModelPicker = false }) { Text("Schließen") }
            }
        )
    }
}

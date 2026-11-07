package de.laraai.hub

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.ui.graphics.Color
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
import java.util.UUID

/**
 * Asks Android for biometric or device-credential authentication. LARA only
 * receives success or failure; biometric data never leaves the system.
 */
internal fun interface AuthRequester {
    fun request(reason: String, onSuccess: () -> Unit, onFailure: (String) -> Unit)
}

class MainActivity : FragmentActivity() {
    private var locked by mutableStateOf(!SessionVault.isUnlocked)
    private var lockMessage by mutableStateOf<String?>(null)
    private var authAvailable by mutableStateOf(true)
    private var backgroundSince: Long? = null
    private lateinit var biometricPrompt: BiometricPrompt
    private var pendingSuccess: (() -> Unit)? = null
    private var pendingFailure: ((String) -> Unit)? = null

    private val authenticators: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BIOMETRIC_STRONG or DEVICE_CREDENTIAL
        } else {
            // BIOMETRIC_STRONG | DEVICE_CREDENTIAL is not supported on Android 9/10.
            BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        }

    private val requester = AuthRequester { reason, onSuccess, onFailure ->
        val availability = BiometricManager.from(this).canAuthenticate(authenticators)
        if (availability != BiometricManager.BIOMETRIC_SUCCESS) {
            onFailure("Keine Geräte-Anmeldung verfügbar. Bitte in den Android-Einstellungen eine Displaysperre einrichten.")
            return@AuthRequester
        }
        pendingSuccess = onSuccess
        pendingFailure = onFailure
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("LARA entsperren")
            .setSubtitle(reason)
            .setAllowedAuthenticators(authenticators)
            .build()
        biometricPrompt.authenticate(info)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        biometricPrompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val action = pendingSuccess
                    pendingSuccess = null
                    pendingFailure = null
                    action?.invoke()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    val action = pendingFailure
                    pendingSuccess = null
                    pendingFailure = null
                    action?.invoke(errString.toString())
                }
                // onAuthenticationFailed (one wrong finger) keeps the prompt open.
            }
        )
        authAvailable = BiometricManager.from(this).canAuthenticate(authenticators) ==
            BiometricManager.BIOMETRIC_SUCCESS
        setContent { LaraTheme {
            if (locked) {
                LockScreen(
                    message = lockMessage,
                    authAvailable = authAvailable,
                    onUnlock = ::unlockSession,
                    onContinueWithoutKeys = {
                        lockMessage = null
                        locked = false
                    }
                )
            } else {
                LaraHubApp(requester, startupNotice = lockMessage)
            }
        } }
    }

    private fun unlockSession() {
        requester.request(
            "Fingerabdruck, Gesicht oder Geräte-PIN",
            onSuccess = {
                lockMessage = try {
                    ApiKeyStore.unlock(this).message
                } catch (e: Exception) {
                    e.message ?: "Gespeicherte Schlüssel konnten nicht geladen werden."
                }
                locked = false
            },
            onFailure = { lockMessage = it }
        )
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) backgroundSince = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        if (LockPolicy.shouldLockOnResume(backgroundSince, SystemClock.elapsedRealtime())) {
            SessionVault.lock()
            lockMessage = null
            locked = true
        }
        backgroundSince = null
        // Ask once when the app becomes visible and is locked; never while a prompt is open.
        if (locked && authAvailable && pendingSuccess == null) unlockSession()
    }
}

@Composable
private fun LockScreen(
    message: String?,
    authAvailable: Boolean,
    onUnlock: () -> Unit,
    onContinueWithoutKeys: () -> Unit
) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, androidx.compose.ui.Alignment.CenterVertically)
        ) {
            Text("LARA ist gesperrt", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Entsperre mit Fingerabdruck, Gesicht oder Geräte-PIN. " +
                    "LARA speichert keine biometrischen Daten; die Prüfung übernimmt Android.",
                style = MaterialTheme.typography.bodyMedium
            )
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (authAvailable) {
                Button(onClick = onUnlock, modifier = Modifier.fillMaxWidth()) { Text("Entsperren") }
            } else {
                Text(
                    "Auf diesem Gerät ist keine Displaysperre eingerichtet. Ohne sie können " +
                        "API-Schlüssel nicht geschützt gespeichert oder verwendet werden.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedButton(onClick = onContinueWithoutKeys, modifier = Modifier.fillMaxWidth()) {
                    Text("Ohne API-Schlüssel fortfahren (nur lokales Modell)")
                }
            }
        }
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
    val defaultEndpoint = "http://127.0.0.1:12434/"
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
private fun LaraHubApp(auth: AuthRequester, startupNotice: String?) {
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
                selectedProvider == AiProvider.OPENCLAW -> "Für das OpenClaw-Gateway wird ein Gateway Token benötigt."
                selectedProvider.requiresApiKey -> "Für diesen Anbieter muss ein API-Schlüssel gespeichert werden."
                selectedProvider == AiProvider.CUSTOM -> "Ein API-Schlüssel ist optional; falls gesetzt, wird er verschlüsselt gespeichert."
                else -> "Ollama benötigt normalerweise keinen API-Schlüssel."
            }
        )
    }
    var prompt by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(startupNotice ?: "Noch nicht verbunden – Verbindung zuerst testen.") }
    // Content indices: 9 Home, 0 Chat, 2 Arena, 4 Monitor/Aufträge, 1 Postfach, 3 Tools.
    var selectedTab by remember { mutableIntStateOf(9) }
    var usageEntries by remember { mutableStateOf(UsageLog.load(context)) }
    var apiMode by remember { mutableStateOf(ApiMode.OPENAI_COMPATIBLE) }
    var availableModels by remember { mutableStateOf(emptyList<String>()) }
    var showModelPicker by remember { mutableStateOf(false) }
    val lines = remember { mutableStateListOf<ChatLine>().apply { addAll(loadMessages(context)) } }
    val scope = rememberCoroutineScope()
    val openClawConversationId = remember {
        val prefs = context.getSharedPreferences("lara_hub_config", Context.MODE_PRIVATE)
        prefs.getString("openclaw_conversation_id", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("openclaw_conversation_id", it).apply()
        }
    }

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
            provider == AiProvider.OPENCLAW -> "Für das OpenClaw-Gateway wird ein Gateway Token benötigt."
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
                val itemLabel = if (selectedProvider == AiProvider.OPENCLAW) "Agent(en)" else "Modell(e)"
                status = "Verbunden: $apiLabel · ${result.models.size} $itemLabel gefunden"
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

    run {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                if (selectedTab != 9) {
                    TopAppBar(
                        title = {
                            Column {
                                Text("LARA AI HUB", color = LaraColors.Gold)
                                Text("Lokale und externe KI-Zentrale", style = MaterialTheme.typography.labelSmall, color = LaraColors.TextMuted)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                    )
                }
            },
            bottomBar = {
                NavigationBar(containerColor = Color(0xF20B1330)) {
                    // label, icon, content index, which content indices count as "selected"
                    listOf(
                        Triple("Home", Icons.Outlined.Home, listOf(9)),
                        Triple("Chat", Icons.Outlined.ChatBubbleOutline, listOf(0)),
                        Triple("Arena", Icons.Outlined.EmojiEvents, listOf(2)),
                        Triple("Projekte", Icons.Outlined.Folder, listOf(4, 1)),
                        Triple("Tools", Icons.Outlined.Build, listOf(3))
                    ).forEach { (label, icon, targets) ->
                        NavigationBarItem(
                            selected = selectedTab in targets,
                            onClick = { selectedTab = targets.first() },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = LaraColors.Gold,
                                selectedTextColor = LaraColors.Gold,
                                indicatorColor = LaraColors.Gold.copy(alpha = 0.15f),
                                unselectedIconColor = LaraColors.Cyan,
                                unselectedTextColor = LaraColors.TextMuted
                            )
                        )
                    }
                }
            }
        ) { padding ->
            val pageModifier = if (selectedTab >= 3) Modifier.verticalScroll(rememberScrollState()) else Modifier
            Column(
                Modifier.fillMaxSize().padding(padding).padding(16.dp).then(pageModifier),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (selectedTab == 4 || selectedTab == 1) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = selectedTab == 4, onClick = { selectedTab = 4 }, label = { Text("Aufträge & Monitor") })
                        FilterChip(selected = selectedTab == 1, onClick = { selectedTab = 1 }, label = { Text("Postfach") })
                    }
                }
                when (selectedTab) {
                    9 -> HomePage(
                        context = context,
                        providerLabel = selectedProvider.label,
                        model = model,
                        connectionStatus = status,
                        chat = lines.map { HomeChatLine(it.role, it.text, it.time) },
                        usage = usageEntries,
                        onOpenTab = { target -> selectedTab = target },
                        onQuickPrompt = { text -> prompt = text; selectedTab = 0 },
                        onTestConnection = { selectedTab = 0; checkConnection() }
                    )

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
                                    // Earlier turns of this chat, sent as context (bounded in ChatHistory).
                                    val history = lines.mapNotNull { line ->
                                        ChatHistory.fromStoredRole(line.role)?.let { ChatTurn(it, line.text) }
                                    }
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
                                            val answer = ApiConnection.chat(
                                                context,
                                                selectedProvider,
                                                endpoint,
                                                selectedModel,
                                                question,
                                                discovery.mode,
                                                if (selectedProvider == AiProvider.OPENCLAW) openClawConversationId else null,
                                                history
                                            )
                                            persistConfig()
                                            addMessage("LARA / $selectedModel", answer)
                                            ApiConnection.lastUsage?.let {
                                                UsageLog.add(context, it)
                                                usageEntries = UsageLog.load(context)
                                            }
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

                    4 -> MonitorPage(context, auth, usageEntries, model)

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
                                Text(
                                    selectedProvider.defaultEndpoint.ifBlank {
                                        if (selectedProvider == AiProvider.OPENCLAW) {
                                            "http://<Gateway-IP>:18789/v1"
                                        } else {
                                            "https://dein-server.example/v1"
                                        }
                                    }
                                )
                            },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = model,
                            onValueChange = { model = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = {
                                Text(if (selectedProvider == AiProvider.OPENCLAW) "OpenClaw-Agent" else "Modellname")
                            },
                            singleLine = true
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { checkConnection() }, enabled = !checking && !busy) {
                                Text(if (checking) "Prüfe …" else "Verbinden / Modelle laden")
                            }
                            if (availableModels.isNotEmpty()) {
                                OutlinedButton(onClick = { showModelPicker = true }) {
                                    Text(if (selectedProvider == AiProvider.OPENCLAW) "Agent wählen" else "Modell wählen")
                                }
                            }
                        }
                        Text(status, style = MaterialTheme.typography.bodySmall)
                        Divider()
                        if (selectedProvider.requiresApiKey || selectedProvider == AiProvider.CUSTOM) {
                            Text(
                                if (selectedProvider == AiProvider.OPENCLAW) "Gateway Token · OpenClaw"
                                else "API-Schlüssel · ${selectedProvider.label}",
                                style = MaterialTheme.typography.titleMedium
                            )
                            OutlinedTextField(
                                value = apiKeyInput,
                                onValueChange = { apiKeyInput = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = {
                                    Text(if (selectedProvider == AiProvider.OPENCLAW) "Gateway Token eingeben" else "API-Schlüssel eingeben")
                                },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true
                            )
                            Text(apiKeyStatus, style = MaterialTheme.typography.bodySmall)
                            Button(
                                onClick = {
                                    // Changing credentials is sensitive: always re-authenticate.
                                    auth.request("API-Schlüssel speichern bestätigen", onSuccess = {
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
                                    }, onFailure = { apiKeyStatus = it })
                                },
                                enabled = apiKeyInput.isNotBlank(),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                            Text(if (selectedProvider == AiProvider.OPENCLAW) "Gateway Token sicher speichern" else "API-Schlüssel sicher speichern")
                        }
                            TextButton(
                                onClick = {
                                    auth.request("API-Schlüssel entfernen bestätigen", onSuccess = {
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
                                    }, onFailure = { apiKeyStatus = it })
                                },
                                enabled = hasSavedApiKey,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (selectedProvider == AiProvider.OPENCLAW) "Gateway Token entfernen" else "Gespeicherten Schlüssel entfernen")
                            }
                        } else {
                            Text("Ollama läuft lokal und benötigt normalerweise keinen API-Schlüssel.")
                        }
                        Text(
                            "Sicherheit: AES-GCM mit Geräteschlüssel im Android Keystore. " +
                                "Zugangsdaten bleiben lokal gespeichert. Anbieter-Schlüssel werden nur über HTTPS übertragen; " +
                                "OpenClaw darf HTTP nur im privaten LAN oder auf localhost nutzen.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (selectedProvider == AiProvider.OPENCLAW) {
                            Text(
                                "Achtung: Ein OpenClaw-Gateway-Token kann Administrator-, Freigabe- und Schreibrechte besitzen. " +
                                    "Nur mit deinem eigenen Gateway verbinden; den Endpunkt niemals öffentlich freigeben.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                "Die OpenClaw-HTTP-Schnittstelle ist standardmäßig deaktiviert. " +
                                    "Aktiviere gateway.http.endpoints.chatCompletions.enabled=true in deiner Gateway-Konfiguration. " +
                                    "Die vollständige Einrichtung steht in docs/OPENCLAW_SETUP.md.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
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

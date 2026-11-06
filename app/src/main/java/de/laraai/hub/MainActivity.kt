package de.laraai.hub

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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

private fun loadMessages(context: Context): List<ChatLine> {
    val raw = context.getSharedPreferences("lara_hub", Context.MODE_PRIVATE).getString("mailbox", "[]") ?: "[]"
    return try {
        val array = JSONArray(raw)
        (0 until array.length()).map { i ->
            val item = array.getJSONObject(i)
            ChatLine(item.optString("role"), item.optString("text"), item.optString("time"))
        }
    } catch (_: Exception) { emptyList() }
}

private fun saveMessages(context: Context, messages: List<ChatLine>) {
    val array = JSONArray()
    messages.takeLast(500).forEach { entry ->
        array.put(JSONObject().put("role", entry.role).put("text", entry.text).put("time", entry.time))
    }
    context.getSharedPreferences("lara_hub", Context.MODE_PRIVATE).edit().putString("mailbox", array.toString()).apply()
}

private fun nowLabel(): String = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(Date())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LaraHubApp() {
    val context = LocalContext.current
    var endpoint by remember { mutableStateOf("http://10.179.130.230:12434/v1/chat/completions") }
    var model by remember { mutableStateOf("qwen2.5-0.5b-instruct-q4_k_m") }
    var apiKeyInput by remember { mutableStateOf("") }
    var hasSavedApiKey by remember { mutableStateOf(ApiKeyStore.hasSaved(context)) }
    var apiKeyStatus by remember {
        mutableStateOf(
            if (ApiKeyStore.hasSaved(context)) "API-Schlüssel ist auf diesem Gerät verschlüsselt gespeichert."
            else "Kein API-Schlüssel gespeichert."
        )
    }
    var prompt by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Bereit – Verbindung wird beim Senden geprüft") }
    var selectedTab by remember { mutableStateOf(0) }
    val lines = remember { mutableStateListOf<ChatLine>().apply { addAll(loadMessages(context)) } }
    val scope = rememberCoroutineScope()

    fun addMessage(role: String, text: String) {
        lines.add(ChatLine(role, text, nowLabel()))
        saveMessages(context, lines)
    }

    MaterialTheme {
        Scaffold(
            topBar = { TopAppBar(title = {
                Column {
                    Text("LARA AI HUB")
                    Text("Dein KI-Kontrollzentrum", style = MaterialTheme.typography.labelSmall)
                }
            }) },
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
            Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (selectedTab) {
                    0 -> {
                        Text(status, style = MaterialTheme.typography.bodySmall)
                        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(lines.takeLast(80)) { entry ->
                                Card { Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                    Text("${entry.role} · ${entry.time}", style = MaterialTheme.typography.labelSmall)
                                    Text(entry.text)
                                } }
                            }
                        }
                        OutlinedTextField(prompt, { prompt = it }, Modifier.fillMaxWidth(), label = { Text("Nachricht an die KI") }, minLines = 2)
                        Button(onClick = {
                            val question = prompt.trim()
                            if (question.isNotEmpty() && !busy) {
                                prompt = ""
                                addMessage("Du", question)
                                busy = true
                                status = "Anfrage wird gesendet …"
                                scope.launch {
                                    try {
                                        val answer = callChatApi(context, endpoint, model, question)
                                        addMessage("LARA / $model", answer)
                                        status = "Antwort erhalten"
                                    } catch (e: Exception) {
                                        status = "Fehler: ${e.message ?: "Verbindung fehlgeschlagen"}"
                                    } finally { busy = false }
                                }
                            }
                        }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                            Text(if (busy) "Bitte warten …" else "Senden")
                        }
                    }
                    1 -> {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("KI-Postfach", style = MaterialTheme.typography.headlineSmall)
                            TextButton(onClick = { lines.clear(); saveMessages(context, lines) }) { Text("Leeren") }
                        }
                        Text("Gespeicherter Ein- und Ausgang · lokal auf diesem Handy")
                        if (lines.isEmpty()) {
                            Text("Noch keine Nachrichten. Starte einen Chat – gesendete Fragen und KI-Antworten erscheinen hier.")
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(lines.asReversed()) { entry ->
                                    Card { Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                        Text(entry.role, style = MaterialTheme.typography.labelLarge)
                                        Text(entry.time, style = MaterialTheme.typography.labelSmall)
                                        Spacer(Modifier.height(4.dp))
                                        Text(entry.text)
                                    } }
                                }
                            }
                        }
                    }
                    2 -> {
                        Text("ARENA", style = MaterialTheme.typography.headlineSmall)
                        Text("Wettkampfverwaltung folgt als eigener Ausbauschritt. Ein Match startet später nur mit mindestens zwei tatsächlich verfügbaren Modellen.")
                    }
                    3 -> {
                        Text("Verbindung", style = MaterialTheme.typography.headlineSmall)
                        OutlinedTextField(endpoint, { endpoint = it }, Modifier.fillMaxWidth(), label = { Text("API-Endpunkt") })
                        OutlinedTextField(model, { model = it }, Modifier.fillMaxWidth(), label = { Text("Modellname") })
                        Divider()
                        Text("API-Schlüssel", style = MaterialTheme.typography.titleMedium)
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
                                    ApiKeyStore.save(context, apiKeyInput)
                                    apiKeyInput = ""
                                    hasSavedApiKey = true
                                    apiKeyStatus = "API-Schlüssel sicher gespeichert. Er wird beim nächsten Start wiederverwendet."
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
                                    ApiKeyStore.clear(context)
                                    apiKeyInput = ""
                                    hasSavedApiKey = false
                                    apiKeyStatus = "Gespeicherter API-Schlüssel wurde entfernt."
                                } catch (e: Exception) {
                                    apiKeyStatus = e.message ?: "Entfernen fehlgeschlagen."
                                }
                            },
                            enabled = hasSavedApiKey,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Gespeicherten Schlüssel entfernen") }
                        Text(
                            "Der Schlüssel wird mit AES-GCM verschlüsselt; der Geräteschlüssel bleibt im Android Keystore. " +
                                "Er wird niemals im Quellcode oder Nachrichtenarchiv abgelegt. " +
                                "Für lokales Ollama normalerweise leer lassen. Ein gespeicherter Schlüssel wird nur über HTTPS übertragen.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

private suspend fun callChatApi(
    context: Context,
    endpoint: String,
    model: String,
    prompt: String
): String = withContext(Dispatchers.IO) {
    val apiKey = ApiKeyStore.load(context)
    val targetUrl = endpoint.trim()
    if (!apiKey.isNullOrEmpty() && !targetUrl.startsWith("https://", ignoreCase = true)) {
        error("API-Schlüssel werden nur über HTTPS übertragen. Für lokales Ollama das Schlüssel-Feld bitte leer lassen.")
    }

    val client = OkHttpClient()
    val payload = JSONObject().put("model", model)
        .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
        .put("stream", false)
    val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
    val requestBuilder = Request.Builder().url(targetUrl).post(body)
    if (!apiKey.isNullOrEmpty()) {
        requestBuilder.header("Authorization", "Bearer $apiKey")
    }
    client.newCall(requestBuilder.build()).execute().use { response ->
        val responseText = response.body?.string().orEmpty()
        if (!response.isSuccessful) error("HTTP ${response.code}: ${responseText.take(300)}")
        JSONObject(responseText).getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message").optString("content", "Leere Antwort")
    }
}

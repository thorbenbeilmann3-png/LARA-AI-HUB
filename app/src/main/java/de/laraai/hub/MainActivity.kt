package de.laraai.hub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LaraHubApp() }
    }
}

private data class ChatLine(val role: String, val text: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LaraHubApp() {
    var endpoint by remember { mutableStateOf("http://127.0.0.1:12434/v1/chat/completions") }
    var model by remember { mutableStateOf("qwen2.5-0.5b-instruct-q4_k_m") }
    var prompt by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Bereit – API noch nicht geprüft") }
    var selectedTab by remember { mutableStateOf(0) }
    val lines = remember { mutableStateListOf<ChatLine>() }
    val scope = rememberCoroutineScope()

    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("LARA AI HUB")
                            Text("Dein KI-Kontrollzentrum", style = MaterialTheme.typography.labelSmall)
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
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (selectedTab) {
                    0 -> {
                        Text(status, style = MaterialTheme.typography.bodySmall)
                        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(lines) { line ->
                                Card {
                                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                        Text(line.role, style = MaterialTheme.typography.labelSmall)
                                        Text(line.text)
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
                                    lines.add(ChatLine("Du", question))
                                    busy = true
                                    status = "Anfrage wird gesendet …"
                                    scope.launch {
                                        try {
                                            val answer = callChatApi(endpoint, model, question)
                                            lines.add(ChatLine("LARA / KI", answer))
                                            status = "Antwort erhalten"
                                        } catch (e: Exception) {
                                            status = "Fehler: ${e.message ?: "Verbindung fehlgeschlagen"}"
                                        } finally {
                                            busy = false
                                        }
                                    }
                                }
                            },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (busy) "Bitte warten …" else "Senden") }
                    }
                    1 -> {
                        Text("KI-Postfach", style = MaterialTheme.typography.headlineSmall)
                        Text("Eingang, Ausgang, Weiterleitung und dauerhafte Speicherung folgen in einem nächsten Ausbauschritt.")
                    }
                    2 -> {
                        Text("ARENA", style = MaterialTheme.typography.headlineSmall)
                        Text("Verbindung zur bestehenden ARENA-Installation und Wettkampfverwaltung folgen. Ein Match startet später nur mit mindestens zwei verfügbaren Modellen.")
                    }
                    3 -> {
                        Text("Verbindung", style = MaterialTheme.typography.headlineSmall)
                        OutlinedTextField(endpoint, { endpoint = it }, modifier = Modifier.fillMaxWidth(), label = { Text("API-Endpunkt") })
                        OutlinedTextField(model, { model = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Modellname") })
                        Text("Für einen Server auf dem PC muss dessen erreichbare LAN-Adresse statt 127.0.0.1 eingetragen werden.")
                        Text("Sicherheit: Dieser Entwicklungsstand verwendet noch keine verschlüsselte Remote-Verbindung oder API-Schlüsselverwaltung.")
                    }
                }
            }
        }
    }
}

private suspend fun callChatApi(endpoint: String, model: String, prompt: String): String =
    withContext(Dispatchers.IO) {
        val client = OkHttpClient()
        val payload = JSONObject()
            .put("model", model)
            .put("messages", org.json.JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
            .put("stream", false)
        val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url(endpoint.trim()).post(body).build()
        client.newCall(request).execute().use { response ->
            val responseText = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("HTTP ${response.code}: ${responseText.take(300)}")
            val json = JSONObject(responseText)
            json.getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").optString("content", "Leere Antwort")
        }
    }

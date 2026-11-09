package de.laraai.hub

import android.content.Context
import android.os.Build
import android.os.Process
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Its own entry: Ollama in Alpine (11434) is not the local server on 12434 used for chat.
private const val MEMORY_ID = "ollama_alpine"

internal fun probeEnvironment(context: Context) = ProbeEnvironment(
    packageName = context.packageName,
    buildSha = BuildConfig.GIT_SHA,
    versionName = BuildConfig.VERSION_NAME,
    uid = Process.myUid(),
    pid = Process.myPid(),
    sdkInt = Build.VERSION.SDK_INT,
    androidRelease = Build.VERSION.RELEASE ?: "unbekannt",
    device = "${Build.MANUFACTURER} ${Build.MODEL}"
)

private fun colorFor(result: ProbeResult): Color = when (result) {
    ProbeResult.OK -> LaraColors.Ok
    ProbeResult.AUTH_REQUIRED -> LaraColors.Cyan
    ProbeResult.READ_TIMEOUT, ProbeResult.CONNECT_TIMEOUT, ProbeResult.TIMEOUT -> LaraColors.Warn
    else -> LaraColors.Error
}

/** Runs the connection test inside this app process and shows only what was observed. */
@Composable
internal fun AlpineProbeCard(context: Context) {
    val memory = remember { ProviderMemory.shared(context) }
    val remembered = remember { memory.load(MEMORY_ID) }
    val probe = remember { AlpineProbe(OkHttpProbeTransport()) }
    val scope = rememberCoroutineScope()
    var ollamaUrl by remember { mutableStateOf(remembered.address ?: AlpineProbe.DEFAULT_OLLAMA_URL) }
    var mcpUrl by remember { mutableStateOf(remembered.mcp ?: AlpineProbe.DEFAULT_MCP_URL) }
    var expectedModel by remember { mutableStateOf(remembered.model ?: AlpineProbe.DEFAULT_MODEL) }
    var running by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<ProbeReport?>(null) }
    var reportTime by remember { mutableStateOf("") }
    var copied by remember { mutableStateOf(false) }

    fun runTest() {
        if (running) return
        running = true
        copied = false
        report = null
        // Only clean addresses are kept: a mistyped token must not end up in plain-text settings.
        memory.remember(
            MEMORY_ID,
            address = ollamaUrl.takeIf { AlpineProbe.ollamaAddressProblem(it) == null },
            model = expectedModel,
            mcp = mcpUrl.takeIf { AlpineProbe.mcpAddressProblem(it) == null }
        )
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) { probe.run(ollamaUrl, mcpUrl, expectedModel) }
                report = result
                reportTime = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.GERMANY).format(Date())
            } finally {
                running = false
            }
        }
    }

    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Alpine / Ollama – Verbindungstest", style = MaterialTheme.typography.titleMedium)
            Text(
                "Prüft aus dieser App heraus, ob Ollama und der MCP-Endpunkt erreichbar sind. " +
                    "Es werden keine Zugangsdaten und keine Chat-Nachricht gesendet.",
                style = MaterialTheme.typography.bodySmall,
                color = LaraColors.TextMuted
            )
            OutlinedTextField(
                value = ollamaUrl,
                onValueChange = { ollamaUrl = it },
                label = { Text("Ollama-Adresse") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = mcpUrl,
                onValueChange = { mcpUrl = it },
                label = { Text("MCP-Adresse") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = expectedModel,
                onValueChange = { expectedModel = it },
                label = { Text("Erwartetes Modell") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = { runTest() }, enabled = !running, modifier = Modifier.fillMaxWidth()) {
                Text(if (running) "Prüfe …" else "Verbindung jetzt testen")
            }
            report?.let { current ->
                Text(
                    current.verdict,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (current.ollamaReached) LaraColors.Ok else LaraColors.Error
                )
                current.steps.forEach { step ->
                    Text(
                        "${step.title}: ${step.result.label}" + (step.httpCode?.let { " (HTTP $it)" } ?: "") + " · ${step.millis} ms",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorFor(step.result)
                    )
                    Text(step.message, style = MaterialTheme.typography.bodySmall)
                    step.detail?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = LaraColors.TextMuted)
                    }
                }
                OutlinedButton(
                    onClick = {
                        copyText(context, AlpineProbe.formatReport(current, probeEnvironment(context), reportTime))
                        copied = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (copied) "Bericht kopiert" else "Bericht kopieren")
                }
            }
        }
    }
}

package de.laraai.hub

import android.content.Context
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Content indices used by MainActivity (kept stable for existing tab bodies). */
internal const val TAB_HOME = 9
internal const val TAB_CHAT = 0
internal const val TAB_POSTFACH = 1
internal const val TAB_ARENA = 2
internal const val TAB_TOOLS = 3
internal const val TAB_MONITOR = 4
internal const val TAB_SETUP = 5

/** One chat line as shown on the home screen (role label + text). */
internal data class HomeChatLine(val role: String, val text: String, val time: String)

/**
 * Home dashboard in the style of the LARA design mockup. Every value shown is real
 * app state (selected model, Core reachability, last chat lines, recorded usage);
 * nothing is invented. Unknown states are shown as such.
 */
@Composable
internal fun HomePage(
    context: Context,
    providerLabel: String,
    model: String,
    connectionStatus: String,
    chat: List<HomeChatLine>,
    usage: List<TokenUsage>,
    onOpenTab: (Int) -> Unit,
    onQuickPrompt: (String) -> Unit,
    onTestConnection: () -> Unit
) {
    val prefs = remember { context.getSharedPreferences("lara_hub_config", Context.MODE_PRIVATE) }
    var coreAlive by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) {
        coreAlive = CoreClient.isAlive(prefs.getString("core_url", CoreClient.DEFAULT_URL) ?: CoreClient.DEFAULT_URL)
    }

    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = LaraColors.Gold, fontWeight = FontWeight.Bold)) { append("LARA ") }
            withStyle(SpanStyle(color = LaraColors.Cyan, fontWeight = FontWeight.Bold)) { append("AI HUB") }
        },
        fontSize = 30.sp,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center
    )

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CoreOrb(coreAlive, Modifier.weight(1f))
        GlassCard(Modifier.weight(1f).clickable { onOpenTab(TAB_TOOLS) }) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("AKTIVES MODELL", color = LaraColors.TextMuted, style = MaterialTheme.typography.labelMedium)
                Text(
                    model.ifBlank { "Noch keins gewählt" },
                    color = LaraColors.Cyan,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(providerLabel, color = LaraColors.TextMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    val readiness = when {
        model.isBlank() -> "Bitte unter Tools ein Modell wählen"
        connectionStatus.startsWith("Verbunden") || connectionStatus.startsWith("Antwort erhalten") -> "Bereit für deinen Auftrag"
        else -> "Bereit – Verbindung noch nicht geprüft"
    }
    Text(readiness, color = LaraColors.Text, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    Text(
        connectionStatus,
        color = if (connectionStatus.startsWith("Verbindung fehlgeschlagen") || connectionStatus.startsWith("Fehler")) LaraColors.Error else LaraColors.TextMuted,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth(),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center
    )

    val setup = remember { SetupStore.load(context) }
    GlassCard(Modifier.fillMaxWidth().clickable { onOpenTab(TAB_SETUP) }) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CardTitle(null, "EINRICHTUNG")
            Text(
                "${setup.doneCount} von ${SetupStep.entries.size} Etappen erledigt. Tippen zum Fortsetzen.",
                color = LaraColors.TextMuted,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    GlassCard(Modifier.fillMaxWidth().clickable { onOpenTab(TAB_CHAT) }, accent = LaraColors.Gold) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CardTitle(Icons.Outlined.Sync, "LETZTE UNTERHALTUNG")
            if (chat.isEmpty()) {
                Text("Noch keine Nachrichten. Tippe hier, um zu chatten.", color = LaraColors.TextMuted)
            } else {
                chat.takeLast(2).forEach { line ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (line.role == "Du") Icons.Outlined.Person else Icons.Outlined.ChatBubbleOutline,
                            contentDescription = null,
                            tint = LaraColors.Gold,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("${line.role.substringBefore(" /")}: ${line.text}", maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = LaraColors.Text, modifier = Modifier.weight(1f))
                        Text(line.time, color = LaraColors.TextMuted, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassCard(Modifier.weight(1f).clickable { onOpenTab(TAB_MONITOR) }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CardTitle(Icons.Outlined.Timeline, "AKTIVITÄT")
                val recent = usage.takeLast(2).reversed()
                if (recent.isEmpty()) {
                    Text("Noch keine Anfragen erfasst.", color = LaraColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                }
                recent.forEach { u ->
                    Text(u.model, color = LaraColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        (u.total?.let { "$it Tokens" } ?: "Tokens unbekannt") + " · " + timeLabel(u.timeMs),
                        color = LaraColors.TextMuted, style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
        GlassCard(Modifier.weight(1f)) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CardTitle(null, "SCHNELL")
                QuickTool(Icons.Outlined.Description, "Zusammenfassen") { onQuickPrompt("Fasse bitte kurz zusammen:\n") }
                QuickTool(Icons.Outlined.Code, "Code prüfen") { onQuickPrompt("Prüfe bitte diesen Code auf Fehler:\n") }
                QuickTool(Icons.Outlined.Sync, "Verbindung testen") { onTestConnection() }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun CardTitle(icon: ImageVector?, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = LaraColors.Text, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(title, color = LaraColors.Text, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun QuickTool(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = LaraColors.Gold, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = LaraColors.Text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Glowing "AI CORE" ring. Shows the real Core state: aktiv / nicht erreichbar / prüfe. */
@Composable
private fun CoreOrb(alive: Boolean?, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "orb")
    val angle by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "angle")
    val pulse by transition.animateFloat(0.75f, 1f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "pulse")
    val ringColor = when (alive) {
        true -> LaraColors.Cyan
        false -> LaraColors.TextMuted
        null -> LaraColors.CyanDim
    }
    Box(modifier.height(170.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(160.dp)) {
            val r = size.minDimension / 2f
            drawCircle(
                Brush.radialGradient(listOf(ringColor.copy(alpha = 0.35f * pulse), Color.Transparent), center, r),
                radius = r
            )
            rotate(angle) {
                drawArc(
                    Brush.sweepGradient(listOf(ringColor, LaraColors.Gold, ringColor.copy(alpha = 0.2f), ringColor)),
                    startAngle = 0f, sweepAngle = 300f, useCenter = false,
                    topLeft = Offset(r * 0.25f, r * 0.25f),
                    size = androidx.compose.ui.geometry.Size(r * 1.5f, r * 1.5f),
                    style = Stroke(width = 5.dp.toPx())
                )
            }
            drawCircle(LaraColors.Gold.copy(alpha = 0.6f), radius = r * 0.62f, style = Stroke(width = 1.5.dp.toPx()))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("AI CORE", color = LaraColors.Text, fontWeight = FontWeight.Bold)
            Text(
                when (alive) {
                    true -> "Aktiv"
                    false -> "Nicht erreichbar"
                    null -> "Prüfe …"
                },
                color = when (alive) {
                    true -> LaraColors.Ok
                    false -> LaraColors.Warn
                    null -> LaraColors.TextMuted
                },
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

private fun timeLabel(ms: Long): String =
    if (ms <= 0) "" else SimpleDateFormat("dd.MM. HH:mm", Locale.GERMANY).format(Date(ms))

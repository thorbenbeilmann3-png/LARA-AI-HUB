package de.laraai.hub

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** LARA design tokens: deep navy background, cyan glow, gold accents. */
internal object LaraColors {
    val Background = Color(0xFF060A18)
    val BackgroundTop = Color(0xFF0B1330)
    val Surface = Color(0xFF0E1632)
    val Cyan = Color(0xFF3BE3F0)
    val CyanDim = Color(0xFF1B8FA0)
    val Gold = Color(0xFFF2C14E)
    val GoldDim = Color(0xFFB8862B)
    val Text = Color(0xFFEAF2FF)
    val TextMuted = Color(0xFF9AA8C7)
    val Ok = Color(0xFF4ADE80)
    val Warn = Color(0xFFFBBF24)
    val Error = Color(0xFFF87171)
}

private val LaraColorScheme = darkColorScheme(
    primary = LaraColors.Cyan,
    onPrimary = Color(0xFF00242A),
    secondary = LaraColors.Gold,
    onSecondary = Color(0xFF2A1E00),
    background = LaraColors.Background,
    onBackground = LaraColors.Text,
    surface = LaraColors.Surface,
    onSurface = LaraColors.Text,
    surfaceVariant = Color(0xFF16204A),
    onSurfaceVariant = LaraColors.TextMuted,
    outline = LaraColors.CyanDim,
    error = LaraColors.Error
)

@Composable
internal fun LaraTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LaraColorScheme) {
        // The transparent Scaffold inherits this; without it, text falls back to black (F1).
        CompositionLocalProvider(LocalContentColor provides LaraColors.Text) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(LaraColors.BackgroundTop, LaraColors.Background)))
            ) { content() }
        }
    }
}

/** Translucent "glass" card with a cyan→gold glowing border, as in the design mockup. */
@Composable
internal fun GlassCard(
    modifier: Modifier = Modifier,
    accent: Color = LaraColors.Cyan,
    corner: Dp = 20.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(corner)
    Box(
        modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0x332A3A70), Color(0x22101830))))
            .border(BorderStroke(1.2.dp, Brush.linearGradient(listOf(accent.copy(alpha = 0.9f), LaraColors.Gold.copy(alpha = 0.55f)))), shape)
            .padding(14.dp),
        content = content
    )
}

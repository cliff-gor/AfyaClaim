package ke.afyaclaim.pos.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.dp

object AfyaShapes {
    val card = RoundedCornerShape(24.dp)
    val cardSmall = RoundedCornerShape(18.dp)
    val input = RoundedCornerShape(14.dp)
    val pill = RoundedCornerShape(percent = 50)
}

private fun materialScheme(c: AfyaColors) = if (c.isDark) {
    darkColorScheme(
        primary = c.lime, onPrimary = c.onLime, primaryContainer = c.sage, onPrimaryContainer = c.ink,
        secondary = c.sage, onSecondary = c.ink, tertiary = c.amber,
        background = c.bg, onBackground = c.ink, surface = c.surface, onSurface = c.ink,
        surfaceVariant = c.surface, onSurfaceVariant = c.muted, surfaceContainer = c.surface,
        surfaceContainerHigh = c.surface, surfaceContainerHighest = c.outline,
        error = c.coral, onError = c.ink, outline = c.outline, outlineVariant = c.outline,
    )
} else {
    lightColorScheme(
        primary = c.ink, onPrimary = c.onHero, primaryContainer = c.lime, onPrimaryContainer = c.onLime,
        secondary = c.moss, onSecondary = c.surface, tertiary = c.amber,
        background = c.bg, onBackground = c.ink, surface = c.surface, onSurface = c.ink,
        surfaceVariant = c.bg, onSurfaceVariant = c.muted, surfaceContainer = c.surface,
        surfaceContainerHigh = c.surface, surfaceContainerHighest = c.outline,
        error = c.coral, onError = c.surface, outline = c.outline, outlineVariant = c.outline,
    )
}

@Composable
fun AfyaTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkAfyaColors else LightAfyaColors
    CompositionLocalProvider(LocalAfyaColors provides colors) {
        MaterialTheme(
            colorScheme = materialScheme(colors),
            typography = AfyaTypography,
            shapes = Shapes(
                extraSmall = AfyaShapes.input,
                small = AfyaShapes.input,
                medium = AfyaShapes.cardSmall,
                large = AfyaShapes.card,
                extraLarge = AfyaShapes.card,
            ),
            content = content,
        )
    }
}

object Afya {
    val colors: AfyaColors
        @Composable @ReadOnlyComposable get() = LocalAfyaColors.current
}

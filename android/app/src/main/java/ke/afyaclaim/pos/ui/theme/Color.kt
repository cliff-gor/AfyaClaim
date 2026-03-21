package ke.afyaclaim.pos.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Design tokens from SPEC section 14. */
@Immutable
data class AfyaColors(
    val bg: Color,
    val surface: Color,
    val ink: Color,
    val muted: Color,
    val lime: Color,
    val moss: Color,
    val sage: Color,
    val coral: Color,
    val amber: Color,
    val lavender: Color,
    /** Hero cards are always dark so the lime accent pops in both themes. */
    val hero: Color,
    val onHero: Color,
    val onHeroMuted: Color,
    val outline: Color,
    val isDark: Boolean,
) {
    val onLime: Color get() = Color(0xFF43511C)
    val coralSoft: Color get() = coral.copy(alpha = if (isDark) 0.18f else 0.12f)
    val amberSoft: Color get() = amber.copy(alpha = if (isDark) 0.20f else 0.18f)
    val sageSoft: Color get() = sage.copy(alpha = if (isDark) 0.55f else 0.45f)
    val limeSoft: Color get() = lime.copy(alpha = if (isDark) 0.16f else 0.35f)
}

val LightAfyaColors = AfyaColors(
    bg = Color(0xFFF3F4EF),
    surface = Color(0xFFFFFFFF),
    ink = Color(0xFF0E0F0C),
    muted = Color(0xFF6B6F66),
    lime = Color(0xFFC8F169),
    moss = Color(0xFF43511C),
    sage = Color(0xFFBCC388),
    coral = Color(0xFFF0605D),
    amber = Color(0xFFF2B33D),
    lavender = Color(0xFFE7E6F7),
    hero = Color(0xFF0E0F0C),
    onHero = Color(0xFFF2F3EE),
    onHeroMuted = Color(0xFF9A9E95),
    outline = Color(0xFFE2E4DB),
    isDark = false,
)

val DarkAfyaColors = AfyaColors(
    bg = Color(0xFF0B0C0A),
    surface = Color(0xFF16181A),
    ink = Color(0xFFF2F3EE),
    muted = Color(0xFF9A9E95),
    lime = Color(0xFFC8F169),
    moss = Color(0xFFBCC388),
    sage = Color(0xFF43511C),
    coral = Color(0xFFFF7A76),
    amber = Color(0xFFF2B33D),
    lavender = Color(0xFF2A2840),
    hero = Color(0xFF1D2019),
    onHero = Color(0xFFF2F3EE),
    onHeroMuted = Color(0xFF9A9E95),
    outline = Color(0xFF26292B),
    isDark = true,
)

val LocalAfyaColors = staticCompositionLocalOf { LightAfyaColors }

package com.metrocompose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily

// ---- Fonts ----
// Selawik — Microsoft's open-source Segoe UI substitute (matched metrics), bundled in res/font.
// Names are font-neutral so the typeface can be swapped without touching call sites.
val MetroLight = FontFamily(Font(R.font.selawik_light))
val MetroSemilight = FontFamily(Font(R.font.selawik_semilight))
val MetroRegular = FontFamily(Font(R.font.selawik_regular))

// ---- Palette: WP8 tokens. Define once, reuse across every component/screen. ----
object Metro {
    val Bg = Color(0xFF000000)
    val Fg = Color(0xFFFFFFFF)
    val Subtle = Color(0xFF9E9E9E) // secondary / disabled text
    val Dim = Color(0xFF5A5A5A)    // unselected pivot header

    // Accent + the classic WP8 tile colors.
    val Accent = Color(0xFF1BA1E2)  // cyan
    val Green = Color(0xFF60A917)
    val Red = Color(0xFFE51400)
    val Purple = Color(0xFFAA00FF)
    val Orange = Color(0xFFF09609)
    val Teal = Color(0xFF00ABA9)
    val Magenta = Color(0xFFD80073)
}

/**
 * The four theme-dependent colors plus the accent. Tile colors ([Metro.Red], [Metro.Green], …)
 * stay static on purpose — they're a fixed palette you pick from, not part of the theme.
 */
@Immutable
data class MetroColors(
    val bg: Color,
    val fg: Color,
    val subtle: Color,
    val dim: Color,
    val accent: Color
)

/** WP8's dark theme — the default, and what every component rendered before theming existed. */
val MetroDarkColors = MetroColors(
    bg = Metro.Bg,
    fg = Metro.Fg,
    subtle = Metro.Subtle,
    dim = Metro.Dim,
    accent = Metro.Accent
)

/** WP8's light theme: background and foreground swap, the greys move to stay readable on white. */
val MetroLightColors = MetroColors(
    bg = Color(0xFFFFFFFF),
    fg = Color(0xFF000000),
    subtle = Color(0xFF6E6E6E),
    dim = Color(0xFFB0B0B0),
    accent = Metro.Accent
)

/**
 * Defaults to the dark palette, so components look identical whether or not the caller
 * wrapped them in [MetroTheme]. Static because a theme change should restart composition
 * of the subtree rather than track reads.
 */
val LocalMetroColors = staticCompositionLocalOf { MetroDarkColors }

/** The 20 accent colors a WP8 user could pick from, in the order the OS listed them. */
val MetroAccents: List<Pair<String, Color>> = listOf(
    "lime" to Color(0xFFA4C400),
    "green" to Color(0xFF60A917),
    "emerald" to Color(0xFF008A00),
    "teal" to Color(0xFF00ABA9),
    "cyan" to Color(0xFF1BA1E2),
    "cobalt" to Color(0xFF0050EF),
    "indigo" to Color(0xFF6A00FF),
    "violet" to Color(0xFFAA00FF),
    "pink" to Color(0xFFF472D0),
    "magenta" to Color(0xFFD80073),
    "crimson" to Color(0xFFA20025),
    "red" to Color(0xFFE51400),
    "orange" to Color(0xFFFA6800),
    "amber" to Color(0xFFF0A30A),
    "yellow" to Color(0xFFE3C800),
    "brown" to Color(0xFF825A2C),
    "olive" to Color(0xFF6D8764),
    "steel" to Color(0xFF647687),
    "mauve" to Color(0xFF76608A),
    "sienna" to Color(0xFF7A3B3F)
)

/**
 * Installs a palette for the subtree. The first two knobs are the ones WP8 itself exposed: pick an
 * [accent] (see [MetroAccents]) and choose the dark or light [dark] background.
 *
 *   MetroTheme(accent = Metro.Magenta, dark = false) { App() }
 *
 * [background] goes past what the phone allowed, for an app that wants to offer its own background
 * colour. Give it one and the rest of the palette is derived from how light it is, so text stays
 * readable on a colour neither black nor white; the dark/light greys are otherwise unchanged. WP8's
 * own two backgrounds are still reachable as `null` plus [dark], and that is the faithful choice.
 *
 * Omitting the wrapper entirely is fine — components fall back to [MetroDarkColors].
 */
@Composable
fun MetroTheme(
    accent: Color = Metro.Accent,
    dark: Boolean = true,
    background: Color? = null,
    content: @Composable () -> Unit
) {
    val colors = remember(accent, dark, background) {
        val base = if (background != null) {
            // Luminance, not the caller's `dark` flag: a dark theme with a pale custom background
            // would otherwise paint white text on it.
            if (background.luminance() > 0.5f) MetroLightColors else MetroDarkColors
        } else {
            if (dark) MetroDarkColors else MetroLightColors
        }
        base.copy(accent = accent, bg = background ?: base.bg)
    }
    CompositionLocalProvider(LocalMetroColors provides colors, content = content)
}

/** Read the active palette: `MetroTheme.colors.accent`. */
object MetroTheme {
    val colors: MetroColors
        @Composable @ReadOnlyComposable get() = LocalMetroColors.current
}

/** True inside a [MetroBackdrop], where pages must not paint over what is already behind them. */
val LocalMetroHasBackdrop = staticCompositionLocalOf { false }

/**
 * Puts one background behind every page in the subtree.
 *
 * Without it each page paints the theme's flat background for itself, which is right for a single
 * screen and wrong for an app: a panorama with a gradient behind it and a detail page in flat black
 * look like two different apps, and navigating between them changes the wallpaper. Inside this, pages
 * leave their own background off ([LocalMetroHasBackdrop]) and [backdrop] shows through all of them,
 * so it stays put while pages come and go over it.
 *
 * A page that genuinely has to cover what is underneath — a now-playing screen over a library — should
 * keep painting its own background and not rely on this.
 *
 *   MetroBackdrop(backdrop = { CollectionGradient() }) { App() }
 */
@Composable
fun MetroBackdrop(
    backdrop: @Composable BoxScope.() -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier
            .fillMaxSize()
            .background(MetroTheme.colors.bg)
    ) {
        backdrop()
        CompositionLocalProvider(LocalMetroHasBackdrop provides true, content = content)
    }
}

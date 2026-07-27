package com.metrocompose.sample

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.metrocompose.MetroAccents
import com.metrocompose.MetroBackdrop
import com.metrocompose.MetroNavHost
import com.metrocompose.MetroTheme
import com.metrocompose.metroTurnstile
import com.metrocompose.rememberMetroBackStack

/** Navigation state. Sealed so a destination can carry data (the tapped song, the stack depth). */
sealed interface Nav {
    data object Start : Nav
    data object Buttons : Nav
    data object Controls : Nav
    data object Pivot : Nav
    data object LongList : Nav
    data object Dialogs : Nav
    data object Settings : Nav
    data object Panorama : Nav
    data object Circular : Nav
    data object Theme : Nav
    data object Songs : Nav
    data object Player : Nav
    data object Icons : Nav
    data object Motion : Nav
    data object Banners : Nav
    data class Song(val title: String, val artist: String) : Nav
    data class Deep(val level: Int) : Nav
}

/**
 * A round-trip encoding for a destination, so the back stack survives process death and not just
 * rotation — the three-argument [rememberMetroBackStack]. For a sealed interface that is a short
 * tag plus whatever the destination carries.
 */
fun encodeNav(nav: Nav): String = when (nav) {
    is Nav.Song -> "song|${nav.title}|${nav.artist}"
    is Nav.Deep -> "deep|${nav.level}"
    else -> nav::class.simpleName.orEmpty()
}

fun decodeNav(encoded: String): Nav {
    val parts = encoded.split("|")
    return when (parts[0]) {
        "song" -> Nav.Song(parts.getOrElse(1) { "" }, parts.getOrElse(2) { "" })
        "deep" -> Nav.Deep(parts.getOrNull(1)?.toIntOrNull() ?: 1)
        else -> NavObjects[parts[0]] ?: Nav.Start
    }
}

private val NavObjects: Map<String, Nav> = listOf(
    Nav.Start, Nav.Buttons, Nav.Controls, Nav.Pivot, Nav.LongList, Nav.Dialogs,
    Nav.Settings, Nav.Panorama, Nav.Circular, Nav.Theme, Nav.Songs, Nav.Player,
    Nav.Icons, Nav.Motion, Nav.Banners
).associateBy { it::class.simpleName.orEmpty() }

/** The three backgrounds the theme screen offers past WP8's own two. */
val CustomBackgrounds = listOf(
    "midnight" to Color(0xFF0E1621),
    "olive" to Color(0xFF1E2416),
    "parchment" to Color(0xFFF2ECD9)
)

class MainActivity : ComponentActivity() {

    /**
     * Shared with the composition so the volume banner can be driven from the hardware keys.
     * Held by the activity because that is where the keys arrive.
     */
    private val volume = VolumeState()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val start: Nav = when (intent?.getStringExtra("screen")) {
            "Settings" -> Nav.Settings
            "Panorama" -> Nav.Panorama
            "Buttons" -> Nav.Buttons
            "Songs" -> Nav.Songs
            else -> Nav.Start
        }
        setContent {
            CompositionLocalProvider(LocalVolume provides volume) { App(start) }
        }
    }

    /**
     * Android has no way to suppress its own volume panel, so [com.metrocompose.MetroVolumeBanner]
     * replaces it only if the activity eats the keys before the system sees them. Returning true
     * here is the whole trick — and its honest limit: inside the app you get the Metro banner, on
     * the lock screen you get Android's.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_UP -> { volume.nudge(+1); true }
        KeyEvent.KEYCODE_VOLUME_DOWN -> { volume.nudge(-1); true }
        else -> super.onKeyDown(keyCode, event)
    }

    /** The system beeps on key-up if it is left to handle it, so consume that too. */
    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN -> true
        else -> super.onKeyUp(keyCode, event)
    }
}

/**
 * App root: installs the theme (accent, dark/light, and a custom background past what the phone
 * allowed), optionally a [MetroBackdrop] behind every page, and runs navigation through
 * [MetroNavHost] — which owns the back stack, the turnstile transitions, and the continuum scopes.
 */
@Composable
fun App(initial: Nav = Nav.Start) {
    var dark by rememberSaveable { mutableStateOf(true) }
    var accentIndex by rememberSaveable { mutableIntStateOf(4) } // cyan — WP8's default-ish
    var backgroundIndex by rememberSaveable { mutableIntStateOf(-1) } // -1 = the theme's own
    var backdrop by rememberSaveable { mutableStateOf(false) }

    val accent = MetroAccents[accentIndex].second
    val background = CustomBackgrounds.getOrNull(backgroundIndex)?.second

    MetroTheme(accent = accent, dark = dark, background = background) {
        // The saveable overload: the whole stack comes back after the process is killed, not just
        // after a rotation. Function references rather than lambdas, so the saver is remembered
        // once instead of rebuilt on every recomposition.
        val nav = rememberMetroBackStack(initial, ::encodeNav, ::decodeNav)

        // One background behind every page, instead of each page painting its own: inside this,
        // MetroPage leaves its flat fill off and the gradient stays put while pages come and go
        // over it. Turn it on from the theme screen and navigate — the wallpaper no longer changes.
        MetroBackdropIf(backdrop) {
            MetroNavHost(
                backStack = nav,
                modifier = Modifier.fillMaxSize().pageBackground(),
                // The host's default, spelled out: this is the seam to reach for if a page wants
                // its own transition. Reversed when popping, so a page pushed off to the left
                // comes back from the left.
                transition = { popping -> metroTurnstile(reverse = popping) }
            ) { screen ->
                when (screen) {
                    Nav.Start -> StartScreen(onOpen = nav::push)
                    Nav.Buttons -> ButtonsScreen()
                    Nav.Controls -> ControlsScreen()
                    Nav.Pivot -> PivotScreen()
                    Nav.LongList -> LongListScreen(onOpen = { nav.push(it) })
                    Nav.Dialogs -> DialogsScreen()
                    Nav.Settings -> SettingsScreen()
                    Nav.Panorama -> PanoramaScreen()
                    Nav.Circular -> CircularPanoramaScreen(onOpen = nav::push)
                    Nav.Player -> PlayerScreen()
                    Nav.Icons -> IconsScreen()
                    Nav.Motion -> MotionScreen()
                    Nav.Banners -> BannersScreen()
                    Nav.Theme -> ThemeScreen(
                        dark = dark,
                        accentIndex = accentIndex,
                        backgroundIndex = backgroundIndex,
                        backdrop = backdrop,
                        onDark = { dark = it },
                        onAccent = { accentIndex = it },
                        onBackground = { backgroundIndex = it },
                        onBackdrop = { backdrop = it }
                    )
                    Nav.Songs -> SongListScreen(onOpen = { nav.push(it) })
                    is Nav.Song -> SongDetailScreen(screen)
                    is Nav.Deep -> BackStackScreen(screen.level, nav)
                }
            }
        }

        // One banner for the whole app, because the volume keys work on every screen.
        AppVolumeBanner()
    }
}

/** [MetroBackdrop] when [on], and nothing at all when it is off — so the difference is visible. */
@Composable
private fun MetroBackdropIf(on: Boolean, content: @Composable () -> Unit) {
    if (!on) {
        content()
        return
    }
    val colors = MetroTheme.colors
    MetroBackdrop(
        backdrop = {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(colors.accent.copy(alpha = 0.28f), colors.bg, colors.bg)
                        )
                    )
            )
        },
        content = content
    )
}

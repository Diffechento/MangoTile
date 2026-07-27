package com.metrocompose.sample

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.metrocompose.LocalMetroHasBackdrop
import com.metrocompose.Metro
import com.metrocompose.MetroTheme

/**
 * The flat page background — unless a [com.metrocompose.MetroBackdrop] has already put something
 * behind every page, in which case painting over it is exactly what makes one screen a gradient and
 * the next one black.
 *
 * [com.metrocompose.MetroPage] asks this question for itself through [LocalMetroHasBackdrop]. A
 * screen that builds its own full-height column, as several here do, has to ask it too.
 */
@Composable
fun Modifier.pageBackground(): Modifier =
    if (LocalMetroHasBackdrop.current) this else background(MetroTheme.colors.bg)

/**
 * One demo track. [tint] stands in for cover art — the sample ships no images, so the "artwork"
 * is a flat colour, which is enough to see [com.metrocompose.metroSlideIn] and
 * [com.metrocompose.MetroCrossfade] doing their work.
 */
data class Track(
    val title: String,
    val artist: String,
    val album: String,
    val tint: Color,
    val seconds: Int
)

val DemoTracks = listOf(
    Track("Get Lucky", "Daft Punk", "Random Access Memories", Metro.Accent, 248),
    Track("Nightcall", "Kavinsky", "OutRun", Metro.Magenta, 258),
    Track("Midnight City", "M83", "Hurry Up, We're Dreaming", Metro.Purple, 244),
    Track("Genesis", "Justice", "Cross", Metro.Red, 233),
    Track("Windowlicker", "Aphex Twin", "Windowlicker", Metro.Green, 366),
    Track("Reckoner", "Radiohead", "In Rainbows", Metro.Teal, 290)
)

fun Int.asClock(): String = "%d:%02d".format(this / 60, this % 60)

/**
 * The volume the activity's key handling drives and [com.metrocompose.MetroVolumeBanner] shows.
 *
 * Android will not let an app suppress the system volume panel, so the banner only replaces it if
 * the activity consumes the volume keys before the system sees them — see `MainActivity.onKeyDown`.
 * Fifteen steps is what the phone's media stream actually has, and the banner shows that raw step
 * rather than a percentage because it is the number the buttons move.
 */
class VolumeState {
    var visible by mutableStateOf(false)
    /** Whether the banner grows to carry the now-playing row — the `media` slot. */
    var showMedia by mutableStateOf(true)
    var step by mutableIntStateOf(6)
        private set

    val steps = 15
    val level: Float get() = step / steps.toFloat()
    val muted: Boolean get() = step == 0

    fun nudge(delta: Int) {
        step = (step + delta).coerceIn(0, steps)
        visible = true
    }

    fun hide() {
        visible = false
    }
}

val LocalVolume = staticCompositionLocalOf { VolumeState() }

/** Artists for the jump-list demos, deliberately mixing Latin and Cyrillic so the grid shows both. */
val DemoArtists = listOf(
    "Aphex Twin", "Air", "Arcade Fire", "Boards of Canada", "Bonobo", "Burial",
    "Caribou", "Daft Punk", "Deadmau5", "Four Tet", "Justice", "Kavinsky",
    "LCD Soundsystem", "M83", "Moderat", "Nujabes", "Odesza", "Phoenix",
    "Radiohead", "Röyksopp", "SBTRKT", "Tycho", "Untold", "Vitalic",
    "Washed Out", "XX", "Yeasayer", "Zero 7", "808 State", "2manydjs",
    "Аквариум", "Браво", "Гражданская оборона", "Кино", "Мумий Тролль",
    "Ногу свело", "Сплин", "Тараканы!", "Ундервуд", "ДДТ"
)

/** Genres already in use — what [com.metrocompose.MetroSuggestBox] offers back. */
val DemoGenres = listOf(
    "Electronic", "Electro", "Electro swing", "Ambient", "Downtempo", "House",
    "Deep house", "Techno", "Trip hop", "Shoegaze", "Post-rock", "Rock",
    "Rock & Roll", "Indie rock", "Synthpop", "Drum and bass"
)

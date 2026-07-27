package com.metrocompose.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrocompose.AppBar
import com.metrocompose.AppBarButton
import com.metrocompose.ListRow
import com.metrocompose.Metro
import com.metrocompose.MetroButton
import com.metrocompose.MetroCheckBox
import com.metrocompose.MetroLight
import com.metrocompose.MetroPage
import com.metrocompose.MetroPanorama
import com.metrocompose.MetroProgressBar
import com.metrocompose.MetroProgressDots
import com.metrocompose.MetroProgressRing
import com.metrocompose.MetroRadio
import com.metrocompose.MetroRegular
import com.metrocompose.MetroSemilight
import com.metrocompose.MetroSlider
import com.metrocompose.MetroSuggestBox
import com.metrocompose.MetroTextBox
import com.metrocompose.MetroTheme
import com.metrocompose.MetroToggle
import com.metrocompose.PanoramaSection
import com.metrocompose.SettingRow
import com.metrocompose.Tile
import com.metrocompose.metroTilt

// ---- Start screen: the WP8 tile grid. Tiles navigate to each demo. ----
@Composable
fun StartScreen(onOpen: (Nav) -> Unit) {
    val colors = MetroTheme.colors
    val u = 76.dp
    val med = 160.dp
    val wide = 328.dp
    val g = 8.dp

    Column(
        Modifier
            .fillMaxSize()
            .pageBackground()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, top = 56.dp, bottom = 40.dp)
    ) {
        Text(
            "start",
            color = colors.fg,
            fontFamily = MetroLight,
            fontSize = 52.sp,
            modifier = Modifier.padding(bottom = 22.dp)
        )
        Row {
            Tile("buttons", Metro.Accent, med, med, "▢") { onOpen(Nav.Buttons) }
            Spacer(Modifier.width(g))
            Tile("controls", Metro.Green, med, med, "▤") { onOpen(Nav.Controls) }
        }
        Spacer(Modifier.height(g))
        Tile("now playing", Metro.Purple, wide, med, "♫") { onOpen(Nav.Player) }
        Spacer(Modifier.height(g))
        Row {
            Tile("collection", Metro.Teal, med, u, "♪") { onOpen(Nav.Panorama) }
            Spacer(Modifier.width(g))
            Tile("pivot", Metro.Red, u, u) { onOpen(Nav.Pivot) }
            Spacer(Modifier.width(g))
            Tile("jump", Metro.Orange, u, u) { onOpen(Nav.LongList) }
        }
        Spacer(Modifier.height(g))
        Row {
            Tile("panorama ∞", Metro.Magenta, med, u, "◷") { onOpen(Nav.Circular) }
            Spacer(Modifier.width(g))
            Tile("motion", Metro.Green, u, u, "→") { onOpen(Nav.Motion) }
            Spacer(Modifier.width(g))
            Tile("icons", Metro.Accent, u, u, "★") { onOpen(Nav.Icons) }
        }
        Spacer(Modifier.height(g))
        Row {
            Tile("songs", Metro.Magenta, u, u) { onOpen(Nav.Songs) }
            Spacer(Modifier.width(g))
            Tile("dialogs", Metro.Green, u, u) { onOpen(Nav.Dialogs) }
            Spacer(Modifier.width(g))
            Tile("banners", Metro.Red, u, u) { onOpen(Nav.Banners) }
            Spacer(Modifier.width(g))
            Tile("stack", Metro.Teal, u, u) { onOpen(Nav.Deep(1)) }
        }
        Spacer(Modifier.height(g))
        Row {
            Tile("settings", Metro.Purple, med, u, "⚙") { onOpen(Nav.Settings) }
            Spacer(Modifier.width(g))
            Tile("theme", Metro.Accent, u, u) { onOpen(Nav.Theme) }
            Spacer(Modifier.width(g))
            Tile("games", Metro.Orange, u, u)
        }
    }
}

// ---- Buttons + toggles + app bar demo ----
@Composable
fun ButtonsScreen() {
    val colors = MetroTheme.colors
    Box(Modifier.fillMaxSize().pageBackground()) {
        MetroPage("ELEMENTS", "buttons") {
            Column(Modifier.padding(start = 24.dp, top = 4.dp)) {
                MetroButton("standard button") {}
                Spacer(Modifier.height(16.dp))
                MetroButton("accent button", filled = true) {}

                Spacer(Modifier.height(32.dp))
                Text("toggles", color = colors.subtle, fontFamily = MetroSemilight, fontSize = 24.sp)
                Spacer(Modifier.height(16.dp))

                var wifi by remember { mutableStateOf(true) }
                var bt by remember { mutableStateOf(false) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MetroToggle(wifi) { wifi = it }
                    Spacer(Modifier.width(16.dp))
                    Text("Wi-Fi", color = colors.fg, fontFamily = MetroSemilight, fontSize = 20.sp)
                }
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MetroToggle(bt) { bt = it }
                    Spacer(Modifier.width(16.dp))
                    Text("Bluetooth", color = colors.fg, fontFamily = MetroSemilight, fontSize = 20.sp)
                }
            }
        }
        AppBar(Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)) {
            AppBarButton("+", "add") {}
            AppBarButton("✓", "save") {}
            AppBarButton("✕", "cancel") {}
            AppBarButton("…", "more") {}
        }
    }
}

// ---- Settings menu ----
@Composable
fun SettingsScreen() {
    Box(Modifier.fillMaxSize().pageBackground()) {
        MetroPage("SETTINGS", "system") {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                var airplane by remember { mutableStateOf(false) }
                var wifi by remember { mutableStateOf(true) }
                var bt by remember { mutableStateOf(false) }
                var cellular by remember { mutableStateOf(true) }
                var location by remember { mutableStateOf(true) }
                var battery by remember { mutableStateOf(false) }

                SettingRow("airplane mode", airplane) { airplane = it }
                SettingRow("wi-fi", wifi) { wifi = it }
                SettingRow("bluetooth", bt) { bt = it }
                SettingRow("cellular data", cellular) { cellular = it }
                SettingRow("location", location) { location = it }
                SettingRow("battery saver", battery) { battery = it }

                Spacer(Modifier.height(8.dp))
                ListRow("about", "phone info, legal")
                ListRow("brightness", "auto")
                ListRow("date + time", "GMT+3, automatic")
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

// ---- Panorama with real multi-layer parallax ----
@Composable
fun PanoramaScreen() {
    MetroPanorama(
        title = "collection",
        overline = "MUSIC + VIDEOS",
        background = {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF10242C), Color(0xFF0A0A0A), Color(0xFF241019))
                        )
                    )
            ) {
                Text(
                    "♪",
                    color = Color(0x1AFFFFFF),
                    fontFamily = MetroLight,
                    fontSize = 460.sp,
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 40.dp)
                )
            }
        }
    ) {
        PanoramaSection("history") {
            Tile("Daft Punk", Metro.Accent, 220.dp, 108.dp)
            Spacer(Modifier.height(12.dp))
            Tile("Discovery", Metro.Green, 220.dp, 108.dp)
        }
        PanoramaSection("new") {
            Tile("Radiohead", Metro.Red, 220.dp, 108.dp)
            Spacer(Modifier.height(12.dp))
            Tile("Aphex Twin", Metro.Purple, 220.dp, 108.dp)
        }
        PanoramaSection("apps") {
            Tile("Podcasts", Metro.Orange, 220.dp, 108.dp)
            Spacer(Modifier.height(12.dp))
            Tile("Radio", Metro.Teal, 220.dp, 108.dp)
        }
    }
}

// ---- Controls gallery: progress, text box, slider, check/radio ----
@Composable
fun ControlsScreen() {
    val colors = MetroTheme.colors
    Box(Modifier.fillMaxSize().pageBackground()) {
        MetroPage("ELEMENTS", "controls") {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, end = 24.dp, bottom = 32.dp)
            ) {
                var vol by remember { mutableStateOf(0.4f) }

                SectionLabel("progress")
                MetroProgressDots()
                Spacer(Modifier.height(18.dp))
                MetroProgressRing()
                Spacer(Modifier.height(20.dp))
                MetroProgressBar(vol) // determinate — tied to the slider below

                Spacer(Modifier.height(28.dp))
                SectionLabel("text box")
                var name by remember { mutableStateOf("") }
                MetroTextBox(name, { name = it }, placeholder = "type your name")

                Spacer(Modifier.height(28.dp))
                SectionLabel("suggest box")
                Text(
                    "Free text, but it offers what the library already says. Type “el”. The popup " +
                        "is not focusable, so the keyboard stays up and the suggestions are an " +
                        "offer rather than a mode you have to leave.",
                    color = colors.dim,
                    fontFamily = MetroRegular,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
                var genre by remember { mutableStateOf("") }
                MetroSuggestBox(
                    value = genre,
                    onValueChange = { genre = it },
                    suggestions = DemoGenres,
                    placeholder = "genre"
                )

                Spacer(Modifier.height(28.dp))
                SectionLabel("slider — settings weight")
                MetroSlider(vol, { vol = it })

                Spacer(Modifier.height(20.dp))
                SectionLabel("slider — hairline, with a buffered fill")
                Text(
                    "trackHeight 2dp, no thumb, a short box: the now-playing weight, where the " +
                        "bar hugs the artwork instead of floating in a settings-sized target. " +
                        "secondaryValue draws the dimmer fill behind it.",
                    color = colors.dim,
                    fontFamily = MetroRegular,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
                var seeks by remember { mutableIntStateOf(0) }
                MetroSlider(
                    value = vol,
                    onValueChange = { vol = it },
                    secondaryValue = (vol + 0.18f).coerceAtMost(1f),
                    // A scrubber previews continuously and seeks once, rather than asking the
                    // player to seek on every pixel.
                    onValueChangeFinished = { seeks++ },
                    trackHeight = 2.dp,
                    thumbSize = null,
                    height = 22.dp
                )
                Text(
                    "seeks committed: $seeks",
                    color = colors.subtle,
                    fontFamily = MetroRegular,
                    fontSize = 13.sp
                )

                Spacer(Modifier.height(28.dp))
                SectionLabel("check + radio")
                var wifi by remember { mutableStateOf(true) }
                var bt by remember { mutableStateOf(false) }
                MetroCheckBox(wifi, { wifi = it }, "wi-fi")
                MetroCheckBox(bt, { bt = it }, "bluetooth")
                Spacer(Modifier.height(12.dp))
                var choice by remember { mutableStateOf(0) }
                MetroRadio(choice == 0, { choice = 0 }, "off")
                MetroRadio(choice == 1, { choice = 1 }, "vibrate")
                MetroRadio(choice == 2, { choice = 2 }, "ring")

                Spacer(Modifier.height(28.dp))
                SectionLabel("tilt — press and hold")
                Text(
                    "THE Metro touch response: while held, the element tips in 3D toward the " +
                        "finger and shrinks. Tile applies it for you; on anything else, share one " +
                        "MutableInteractionSource between metroTilt and the clickable.",
                    color = colors.dim,
                    fontFamily = MetroRegular,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // A tile gets it built in.
                    Tile("tile", Metro.Teal, 96.dp, 96.dp, "▣")
                    // And here it is by hand on a plain Box.
                    val press = remember { MutableInteractionSource() }
                    Box(
                        Modifier
                            .size(96.dp)
                            .metroTilt(press)
                            .background(colors.accent)
                            .clickable(interactionSource = press, indication = null) {},
                        contentAlignment = Alignment.Center
                    ) {
                        Text("metroTilt", color = Metro.Fg, fontFamily = MetroRegular, fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.height(20.dp))
                MetroButton("a button inverts instead") {}
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = MetroTheme.colors.subtle,
        fontFamily = MetroSemilight,
        fontSize = 22.sp,
        modifier = Modifier.padding(bottom = 12.dp)
    )
}

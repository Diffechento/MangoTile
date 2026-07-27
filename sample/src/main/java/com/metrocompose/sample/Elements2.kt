package com.metrocompose.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrocompose.ListRow
import com.metrocompose.Metro
import com.metrocompose.MetroAccents
import com.metrocompose.MetroButton
import com.metrocompose.MetroContextMenu
import com.metrocompose.MetroInputBox
import com.metrocompose.MetroLight
import com.metrocompose.MetroListBox
import com.metrocompose.MetroLongList
import com.metrocompose.MetroMessageBox
import com.metrocompose.MetroPage
import com.metrocompose.MetroPivot
import com.metrocompose.MetroRegular
import com.metrocompose.MetroSemilight
import com.metrocompose.MetroToggle
import com.metrocompose.Pivot
import com.metrocompose.Tile
import com.metrocompose.MetroTheme
import com.metrocompose.metroGroupChar

// ---- Pivot, in both forms ----
@Composable
fun PivotScreen() {
    val colors = MetroTheme.colors
    val songs = listOf("Get Lucky" to "Daft Punk", "Nightcall" to "Kavinsky", "Genesis" to "Justice", "Reckoner" to "Radiohead")
    val artists = listOf("Aphex Twin" to "12 albums", "Daft Punk" to "6 albums", "Justice" to "4 albums", "M83" to "8 albums")
    val albums = listOf("Discovery" to "Daft Punk", "Cross" to "Justice", "OutRun" to "Kavinsky", "In Rainbows" to "Radiohead")

    Column(Modifier.fillMaxSize().pageBackground().padding(top = 52.dp)) {
        Text(
            "MUSIC + VIDEOS",
            color = colors.fg,
            fontFamily = MetroSemilight,
            fontSize = 13.sp,
            letterSpacing = 2.sp,
            modifier = Modifier.padding(start = 24.dp, bottom = 6.dp)
        )
        // The whole control: headers wired to a swipeable pager, the underline tracking the drag.
        MetroPivot(listOf("songs", "artists", "albums"), Modifier.weight(1f)) { page ->
            val data = when (page) { 1 -> artists; 2 -> albums; else -> songs }
            LazyColumn(Modifier.fillMaxSize()) {
                items(data) { (a, b) -> ListRow(a, b) }
            }
        }

        // And the header strip on its own, for content a pager cannot hold — here it just filters
        // the row below it. `Pivot` hosts nothing; you drive the selection yourself.
        Text(
            "Pivot — headers only, no pager",
            color = colors.subtle,
            fontFamily = MetroSemilight,
            fontSize = 15.sp,
            modifier = Modifier.padding(start = 24.dp, top = 8.dp)
        )
        var tab by remember { mutableStateOf(0) }
        Pivot(listOf("all", "starred", "recent"), tab) { tab = it }
        Text(
            when (tab) {
                1 -> "3 starred tracks"
                2 -> "played in the last week"
                else -> "everything in the collection"
            },
            color = colors.fg,
            fontFamily = MetroRegular,
            fontSize = 18.sp,
            modifier = Modifier.padding(start = 24.dp, top = 6.dp, bottom = 24.dp)
        )
    }
}

// ---- Jump-list (LongListSelector) ----
@Composable
fun LongListScreen(onOpen: (Nav.Song) -> Unit) {
    val colors = MetroTheme.colors
    // The accent square is the WP8 look over text rows. Over rows that are themselves coloured
    // tiles it becomes a second competing block of accent — hence the switch.
    var filled by remember { mutableStateOf(true) }
    var tiles by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().pageBackground().padding(top = 52.dp)) {
        Text(
            "COLLECTION",
            color = colors.fg,
            fontFamily = MetroSemilight,
            fontSize = 13.sp,
            letterSpacing = 2.sp,
            modifier = Modifier.padding(start = 24.dp)
        )
        Text(
            "artists",
            color = colors.fg,
            fontFamily = MetroLight,
            fontSize = 52.sp,
            modifier = Modifier.padding(start = 22.dp, bottom = 4.dp)
        )
        Row(
            Modifier.padding(start = 24.dp, end = 24.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MetroToggle(filled) { filled = it }
            Spacer(Modifier.width(12.dp))
            Text(
                "filled group headers",
                color = colors.fg, fontFamily = MetroRegular, fontSize = 16.sp,
                modifier = Modifier.weight(1f)
            )
            MetroToggle(tiles) { tiles = it }
            Spacer(Modifier.width(12.dp))
            Text("tile rows", color = colors.fg, fontFamily = MetroRegular, fontSize = 16.sp)
        }
        // 40 artists over Latin and Cyrillic — the jump grid shows both alphabets and switches to
        // seven narrower tiles so sixty letters still fit.
        MetroLongList(
            items = DemoArtists,
            key = { it },
            group = { metroGroupChar(it) },
            filledGroupHeaders = filled,
            modifier = Modifier.weight(1f)
        ) { artist ->
            if (tiles) {
                Box(Modifier.padding(start = 24.dp, top = 4.dp, bottom = 4.dp)) {
                    Tile(
                        artist,
                        MetroAccents[artist.length.mod(MetroAccents.size)].second,
                        220.dp, 72.dp,
                        onClick = { onOpen(Nav.Song(artist, "artist")) }
                    )
                }
            } else {
                ListRow(artist, onClick = { onOpen(Nav.Song(artist, "artist")) })
            }
        }
    }
}

// ---- Dialogs: message box, input box, list picker, context menu ----
@Composable
fun DialogsScreen() {
    val colors = MetroTheme.colors
    var msg by remember { mutableStateOf(false) }
    var alert by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var last by remember { mutableStateOf("") }
    // Whether the track is already in a playlist decides whether "remove from playlist" applies.
    var inPlaylist by remember { mutableStateOf(false) }

    val menuItems = listOf("play", "add to playlist", "remove from playlist", "delete")

    MetroPage("ELEMENTS", "dialogs") {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 32.dp)) {
            MetroButton("message box") { msg = true }
            Spacer(Modifier.height(14.dp))
            MetroButton("alert — one button") { alert = true }
            Spacer(Modifier.height(14.dp))
            MetroButton("input box") { input = true }
            Spacer(Modifier.height(14.dp))
            MetroButton("list picker") { picker = true }

            Spacer(Modifier.height(28.dp))
            Text("context menu — hold the row", color = colors.subtle, fontFamily = MetroSemilight, fontSize = 20.sp)
            Text(
                "An action that does not apply is greyed, not hidden: the menu keeps the same " +
                    "shape every time you open it, so you learn where each entry is. Flip the " +
                    "switch and hold the row again.",
                color = colors.dim, fontFamily = MetroRegular, fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                MetroToggle(inPlaylist) { inPlaylist = it }
                Spacer(Modifier.width(12.dp))
                Text(
                    if (inPlaylist) "in a playlist" else "not in a playlist",
                    color = colors.fg, fontFamily = MetroRegular, fontSize = 16.sp
                )
            }
            Spacer(Modifier.height(8.dp))
            MetroContextMenu(
                expanded = menu,
                items = menuItems,
                onSelect = { i -> menu = false; last = "menu: " + menuItems[i] },
                onDismiss = { menu = false },
                // "remove from playlist" is inert while the track is not in one.
                disabledItems = if (inPlaylist) emptySet() else setOf(2)
            ) {
                ListRow("Get Lucky", "Daft Punk", onLongClick = { menu = true })
            }

            if (last.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Text(last, color = colors.accent, fontFamily = MetroRegular, fontSize = 18.sp)
            }
        }
    }

    MetroMessageBox(
        visible = msg,
        title = "delete track?",
        message = "This removes “Get Lucky” from your collection.",
        onConfirm = { msg = false; last = "deleted" },
        onDismiss = { msg = false }
    )
    // Pass null for `dismiss` and the box has one button — an alert rather than a question.
    MetroMessageBox(
        visible = alert,
        title = "no network",
        message = "The collection could not be refreshed.",
        onConfirm = { alert = false; last = "acknowledged" },
        onDismiss = { alert = false },
        confirm = "ok",
        dismiss = null
    )
    MetroInputBox(
        visible = input,
        title = "new playlist",
        placeholder = "playlist name",
        onConfirm = { name -> input = false; last = "created: $name" },
        onDismiss = { input = false }
    )
    MetroListBox(
        visible = picker,
        title = "add to playlist",
        items = listOf("Favourites", "Workout", "Chill", "Focus", "Late night"),
        onSelect = { i -> picker = false; last = "added to " + listOf("Favourites", "Workout", "Chill", "Focus", "Late night")[i] },
        onDismiss = { picker = false }
    )
}

// ---- Theme picker: dark/light, the 20 WP8 accents, a custom background, and the backdrop ----
@Composable
fun ThemeScreen(
    dark: Boolean,
    accentIndex: Int,
    backgroundIndex: Int,
    backdrop: Boolean,
    onDark: (Boolean) -> Unit,
    onAccent: (Int) -> Unit,
    onBackground: (Int) -> Unit,
    onBackdrop: (Boolean) -> Unit
) {
    val colors = MetroTheme.colors
    MetroPage("SETTINGS", "theme") {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 32.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MetroToggle(dark) { onDark(it) }
                Spacer(Modifier.width(16.dp))
                Text(
                    if (dark) "dark theme" else "light theme",
                    color = colors.fg, fontFamily = MetroRegular, fontSize = 20.sp
                )
            }
            if (backgroundIndex >= 0) {
                Text(
                    "A custom background overrides this — the palette is derived from how light " +
                        "the colour is, not from the switch.",
                    color = colors.dim, fontFamily = MetroRegular, fontSize = 13.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            Spacer(Modifier.height(28.dp))
            Text("accent", color = colors.subtle, fontFamily = MetroSemilight, fontSize = 22.sp)
            Spacer(Modifier.height(12.dp))

            val perRow = 5
            MetroAccents.chunked(perRow).forEachIndexed { rowIdx, rowItems ->
                Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowItems.forEachIndexed { colIdx, (_, color) ->
                        val idx = rowIdx * perRow + colIdx
                        Box(
                            Modifier
                                .size(56.dp)
                                .background(color)
                                .then(if (idx == accentIndex) Modifier.border(3.dp, colors.fg) else Modifier)
                                .clickable { onAccent(idx) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Text("background", color = colors.subtle, fontFamily = MetroSemilight, fontSize = 22.sp)
            Text(
                "Past what the phone allowed. Give MetroTheme a colour and the greys and the text " +
                    "colour come from its luminance, so a pale background cannot end up with " +
                    "white text on it — try “parchment” with the dark theme still on.",
                color = colors.dim, fontFamily = MetroRegular, fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // -1 is WP8's own pair, still reachable and still the faithful choice.
                BackgroundSwatch("theme", colors.bg, backgroundIndex == -1) { onBackground(-1) }
                CustomBackgrounds.forEachIndexed { i, (name, color) ->
                    BackgroundSwatch(name, color, backgroundIndex == i) { onBackground(i) }
                }
            }

            Spacer(Modifier.height(28.dp))
            Text("backdrop", color = colors.subtle, fontFamily = MetroSemilight, fontSize = 22.sp)
            Text(
                "MetroBackdrop puts one background behind every page in the subtree. Pages inside " +
                    "it leave their own flat fill off, so it stays put while pages come and go " +
                    "over it — turn it on and navigate back to start.",
                color = colors.dim, fontFamily = MetroRegular, fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                MetroToggle(backdrop) { onBackdrop(it) }
                Spacer(Modifier.width(16.dp))
                Text(
                    if (backdrop) "one backdrop for the app" else "each page paints its own",
                    color = colors.fg, fontFamily = MetroRegular, fontSize = 18.sp
                )
            }
        }
    }
}

@Composable
private fun BackgroundSwatch(name: String, color: Color, selected: Boolean, onClick: () -> Unit) {
    val colors = MetroTheme.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(62.dp)
                .background(color)
                .border(if (selected) 3.dp else 1.dp, if (selected) colors.accent else colors.dim)
                .clickable(onClick = onClick)
        )
        Spacer(Modifier.height(4.dp))
        Text(name, color = colors.subtle, fontFamily = MetroRegular, fontSize = 11.sp)
    }
}

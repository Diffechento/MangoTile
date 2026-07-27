package com.metrocompose.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrocompose.ListRow
import com.metrocompose.Metro
import com.metrocompose.MetroBottomInset
import com.metrocompose.MetroLongList
import com.metrocompose.MetroPanorama
import com.metrocompose.MetroPanoramaSection
import com.metrocompose.MetroRegular
import com.metrocompose.MetroSemilight
import com.metrocompose.MetroTheme
import com.metrocompose.MetroTopBanner
import com.metrocompose.SettingRow
import com.metrocompose.Tile
import com.metrocompose.metroGroupChar

/**
 * The phone's panorama proper — the list-taking [MetroPanorama] overload.
 *
 * Three things the lambda overload on the collection screen does not do:
 *
 *  - **it wraps**, so past "settings" at the far end comes "history" again and nothing is ever more
 *    than one swipe away, which is what makes putting settings at the far end reasonable at all;
 *  - **it is lazy**, so the forty-artist jump list in section two costs nothing until you are
 *    near it;
 *  - **the next header leans in** by `sectionPeek`, dimmer the further out it is, while that
 *    section's content is still entirely off screen.
 *
 * Sections are values, not composed children, which is what lets the panorama place them itself.
 * Keep the *number* of them stable while it is on screen — page numbers are read modulo the count.
 */
@Composable
fun CircularPanoramaScreen(onOpen: (Nav) -> Unit) {
    // A photograph or an album cover cannot be tiled: two copies meet in a hard vertical line. Flip
    // this and watch the seam appear in the backdrop as you swipe past the wrap point.
    var tiles by remember { mutableStateOf(true) }
    var banner by remember { mutableStateOf<String?>(null) }

    // Built once. The section lambdas capture the state delegates above, so reading `tiles` inside
    // one of them is still live — and the *count* never changes, which is what matters: with
    // wrapping, a page number means a section modulo the count, so inserting one under the user
    // renumbers every page and the panorama appears to jump somewhere else.
    val sections = remember {
        listOf(
            MetroPanoramaSection(
                title = "history",
                headerPadding = 24.dp,
                onHeaderClick = { banner = "history — the header is the control" }
            ) {
                TileColumn()
            },
            MetroPanoramaSection(
                title = "artists",
                headerPadding = 24.dp,
                onHeaderClick = { banner = "artists — 40 of them, composed only when near" }
            ) {
                // A full LongListSelector inside one section, Latin and Cyrillic both. The jump
                // grid opens in a popup over the whole screen, so the list need not be screen-wide
                // to get a full alphabet — which is the only reason one fits inside a panorama
                // section at all.
                MetroLongList(
                    items = DemoArtists,
                    key = { it },
                    group = { metroGroupChar(it) },
                    modifier = Modifier.fillMaxWidth().weight(1f)
                ) { artist ->
                    ListRow(artist, onClick = { onOpen(Nav.Song(artist, "artist")) })
                }
            },
            MetroPanoramaSection(
                title = "new",
                headerPadding = 24.dp
            ) {
                TileColumn(offset = 2)
            },
            MetroPanoramaSection(
                title = "settings",
                headerPadding = 24.dp
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        "At the far end, and still one swipe from the start.",
                        color = MetroTheme.colors.subtle,
                        fontFamily = MetroRegular,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 10.dp)
                    )
                    SettingRow("tile the backdrop", tiles) { tiles = it }
                    Text(
                        if (tiles) {
                            "On: the backdrop is drawn twice and its speed nudged so a full cycle " +
                                "moves it a whole number of windows — right for a gradient."
                        } else {
                            "Off: drawn once, wider than the window, and panned inside its own " +
                                "overhang — right for a photograph, which cannot meet itself."
                        },
                        color = MetroTheme.colors.dim,
                        fontFamily = MetroRegular,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                    Spacer(Modifier.height(20.dp))
                    Text(
                        "Swipe once more to come back round to “history”.",
                        color = MetroTheme.colors.subtle,
                        fontFamily = MetroRegular,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                    // The panorama does not inset its sections — they are lists, and a list carries
                    // the navigation bar inside its own scroll. This column is not one, so it says
                    // so itself.
                    MetroBottomInset(extra = 12.dp)
                }
            }
        )
    }

    Box(Modifier.fillMaxSize()) {
        MetroPanorama(
            // Deliberately not "collection" — that is the lambda-overload panorama on the other
            // tile, and two demos under one name are indistinguishable once you are inside them.
            title = "my music",
            sections = sections,
            overline = "COLLECTION",
            // Small, with the headers indented instead, so a section's header lines up with the
            // 24dp gutter its own ListRows bring.
            sectionPadding = 2.dp,
            sectionPeek = 96.dp,
            backgroundTiles = tiles,
            background = { PanoramaBackdrop(tiles) }
        )

        MetroTopBanner(
            visible = banner != null,
            onHide = { banner = null }
        ) {
            Text(
                banner.orEmpty(),
                color = MetroTheme.colors.fg,
                fontFamily = MetroSemilight,
                fontSize = 16.sp
            )
        }
    }
}

/** Two tiles, so each section has something with height in it. */
@Composable
private fun TileColumn(offset: Int = 0) {
    Column(
        Modifier.padding(start = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        repeat(3) { i ->
            val t = DemoTracks[(i + offset).mod(DemoTracks.size)]
            Tile(t.artist, t.tint, 220.dp, 100.dp, "♪")
        }
    }
}

/**
 * Deliberately asymmetric, so `backgroundTiles = false` has something to show: a gradient that ends
 * where it began tiles invisibly whatever you do.
 */
@Composable
private fun PanoramaBackdrop(tiles: Boolean) {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                if (tiles) {
                    Brush.horizontalGradient(
                        listOf(Color(0xFF10242C), Color(0xFF241019), Color(0xFF10242C))
                    )
                } else {
                    Brush.radialGradient(
                        listOf(Metro.Teal.copy(alpha = 0.45f), Color(0xFF0A0A0A)),
                        center = Offset(220f, 380f),
                        radius = 900f
                    )
                }
            )
    )
}

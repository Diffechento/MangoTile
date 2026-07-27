package com.metrocompose.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrocompose.CenteredGlyph
import com.metrocompose.MetroIcon
import com.metrocompose.MetroLineIcon
import com.metrocompose.MetroPage
import com.metrocompose.MetroRegular
import com.metrocompose.MetroSemilight
import com.metrocompose.MetroTheme
import com.metrocompose.TransportButton

/**
 * The drawn icon set, and why it exists.
 *
 * Android's font fallback renders ♥ ⇄ ↻ ❚❚ at whatever weight, baseline and ink height whichever
 * font happens to supply them likes — the bottom of this screen puts the typed symbols next to the
 * drawn ones so the difference is visible rather than asserted.
 */
@Composable
fun IconsScreen() {
    val colors = MetroTheme.colors
    var shuffle by remember { mutableStateOf(true) }
    var starred by remember { mutableStateOf(false) }

    MetroPage("ELEMENTS", "icons") {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp)
        ) {
            IconsLabel("the set — MetroLineIcon")
            // MetroIcon.entries is every glyph the library draws; one stroke width across all of
            // them, and each scales with its box rather than with a font size.
            MetroIcon.entries.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
                    row.forEach { icon ->
                        Column(
                            Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            MetroLineIcon(icon, colors.fg, Modifier.size(34.dp))
                            Spacer(Modifier.height(6.dp))
                            Text(
                                icon.name,
                                color = colors.subtle,
                                fontFamily = MetroRegular,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    // Keep the last, short row aligned with the ones above it.
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }

            Spacer(Modifier.height(20.dp))
            IconsLabel("one size, one weight")
            Text(
                "The transport shapes share a height and an optical weight, so the middle button " +
                    "does not change size when playback starts. Press it.",
                color = colors.subtle, fontFamily = MetroRegular, fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            var playing by remember { mutableStateOf(false) }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TransportButton(
                    icon = MetroIcon.Shuffle, contentDescription = "shuffle",
                    active = shuffle, onClick = { shuffle = !shuffle }
                )
                TransportButton(
                    icon = MetroIcon.Previous, contentDescription = "previous",
                    ringSize = 48.dp, onClick = {}
                )
                TransportButton(
                    icon = if (playing) MetroIcon.Pause else MetroIcon.Play,
                    contentDescription = if (playing) "pause" else "play",
                    ringSize = 60.dp, iconSize = 26.dp, touchSize = 68.dp,
                    onClick = { playing = !playing }
                )
                TransportButton(
                    icon = MetroIcon.Next, contentDescription = "next",
                    ringSize = 48.dp, onClick = {}
                )
                TransportButton(
                    icon = if (starred) MetroIcon.StarFilled else MetroIcon.Star,
                    contentDescription = "favourite",
                    active = starred, onClick = { starred = !starred }
                )
            }

            Spacer(Modifier.height(28.dp))
            IconsLabel("typed glyphs — CenteredGlyph")
            Text(
                "A glyph centred on its layout box (left) against the same glyph centred on its " +
                    "ink (right). The transport symbols are not in Selawik, so each arrives from a " +
                    "fallback font with its own side bearings.",
                color = colors.subtle, fontFamily = MetroRegular, fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            // Three pairs, not four: each pair is two boxes wide and a fourth runs off the edge.
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("▶", "⏭", "❚❚").forEach { glyph ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            Modifier.size(44.dp).border(2.dp, colors.dim),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(glyph, color = colors.dim, fontSize = 20.sp)
                        }
                        Box(
                            Modifier.size(44.dp).border(2.dp, colors.fg),
                            contentAlignment = Alignment.Center
                        ) {
                            CenteredGlyph(glyph, colors.fg, 20.sp, Modifier.matchParentSize())
                        }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
            IconsLabel("tinted by the theme")
            Text(
                "Icons take a colour, so they follow the accent — change it on the theme screen.",
                color = colors.subtle, fontFamily = MetroRegular, fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                listOf(MetroIcon.Star, MetroIcon.Shuffle, MetroIcon.Repeat, MetroIcon.Speaker)
                    .forEach { icon ->
                        Box(
                            Modifier.size(52.dp).background(colors.accent),
                            contentAlignment = Alignment.Center
                        ) {
                            MetroLineIcon(icon, colors.bg, Modifier.size(26.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                    }
            }
        }
    }
}

@Composable
private fun IconsLabel(text: String) {
    Text(
        text,
        color = MetroTheme.colors.subtle,
        fontFamily = MetroSemilight,
        fontSize = 22.sp,
        modifier = Modifier.padding(bottom = 12.dp)
    )
}

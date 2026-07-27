package com.metrocompose.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrocompose.MetroLight
import com.metrocompose.MetroRegular
import com.metrocompose.MetroSemilight
import com.metrocompose.MetroTheme
import com.metrocompose.metroContinuum

private data class Song(val title: String, val artist: String)

private fun titleKey(title: String) = "song-title-$title"

private val SONGS = listOf(
    Song("Get Lucky", "Daft Punk"),
    Song("Instant Crush", "Daft Punk"),
    Song("Nightcall", "Kavinsky"),
    Song("Midnight City", "M83"),
    Song("Genesis", "Justice"),
    Song("Digital Love", "Daft Punk"),
    Song("Windowlicker", "Aphex Twin"),
    Song("Reckoner", "Radiohead"),
    Song("Weird Fishes", "Radiohead"),
    Song("Around the World", "Daft Punk")
)

/**
 * Song list. Tapping a row opens the detail screen; the row's title is tagged with
 * [metroContinuum] so it flows into the big title over there — no scope threading needed,
 * MetroNavHost installs the shared scopes.
 */
@Composable
fun SongListScreen(onOpen: (Nav.Song) -> Unit) {
    val colors = MetroTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .pageBackground()
            .padding(top = 52.dp)
    ) {
        Text(
            "MUSIC",
            color = colors.fg,
            fontFamily = MetroSemilight,
            fontSize = 13.sp,
            letterSpacing = 2.sp,
            modifier = Modifier.padding(start = 24.dp, bottom = 4.dp)
        )
        Text(
            "songs",
            color = colors.fg,
            fontFamily = MetroLight,
            fontSize = 52.sp,
            modifier = Modifier.padding(start = 22.dp, bottom = 18.dp)
        )

        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            items(SONGS) { song ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(Nav.Song(song.title, song.artist)) }
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = song.title,
                        color = colors.fg,
                        fontFamily = MetroRegular,
                        fontSize = 25.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.metroContinuum(titleKey(song.title))
                    )
                    Text(
                        text = song.artist,
                        color = colors.subtle,
                        fontFamily = MetroRegular,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

/**
 * Song detail. The big title carries the SAME continuum key as the tapped row, so it appears
 * to grow out of that row and settle here, reversing on Back.
 */
@Composable
fun SongDetailScreen(song: Nav.Song) {
    val colors = MetroTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .pageBackground()
            .padding(top = 52.dp)
    ) {
        Text(
            song.artist.uppercase(),
            color = colors.fg,
            fontFamily = MetroSemilight,
            fontSize = 13.sp,
            letterSpacing = 2.sp,
            modifier = Modifier.padding(start = 24.dp)
        )
        Text(
            text = song.title,
            color = colors.fg,
            fontFamily = MetroLight,
            fontSize = 64.sp,
            maxLines = 2,
            overflow = TextOverflow.Visible,
            modifier = Modifier
                .padding(start = 22.dp, top = 2.dp, bottom = 24.dp)
                .metroContinuum(titleKey(song.title))
        )

        Text("track", color = colors.subtle, fontFamily = MetroSemilight,
            fontSize = 22.sp, modifier = Modifier.padding(start = 24.dp, bottom = 8.dp))
        DetailRow("1", song.title, "4:08")
        DetailRow("2", "Intro", "0:42")
        DetailRow("3", "Reprise", "3:15")
        Spacer(Modifier.height(24.dp))
        Text("more from ${song.artist}", color = colors.subtle,
            fontFamily = MetroSemilight, fontSize = 22.sp,
            modifier = Modifier.padding(start = 24.dp, bottom = 8.dp))
        DetailRow("♪", "Random Access Memories", "album")
        DetailRow("♪", "Discovery", "album")
    }
}

@Composable
private fun DetailRow(lead: String, title: String, trailing: String) {
    val colors = MetroTheme.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp)) {
        Text("$lead   $title", color = colors.fg, fontFamily = MetroRegular, fontSize = 22.sp)
        Text(trailing, color = colors.subtle, fontFamily = MetroRegular, fontSize = 14.sp)
    }
}

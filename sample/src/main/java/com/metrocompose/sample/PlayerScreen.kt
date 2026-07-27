package com.metrocompose.sample

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrocompose.MetroBarRiseMillis
import com.metrocompose.MetroBottomBar
import com.metrocompose.MetroBottomInset
import com.metrocompose.MetroCrossfade
import com.metrocompose.MetroIcon
import com.metrocompose.MetroLight
import com.metrocompose.MetroRegular
import com.metrocompose.MetroRisingPage
import com.metrocompose.MetroSemilight
import com.metrocompose.MetroSlideInMillis
import com.metrocompose.MetroSlider
import com.metrocompose.MetroSwap
import com.metrocompose.MetroTheme
import com.metrocompose.TransportButton
import com.metrocompose.metroDismissDown
import com.metrocompose.metroSlideIn
import com.metrocompose.metroSwipe
import com.metrocompose.rememberMetroDismiss
import com.metrocompose.rememberMetroReveal
import com.metrocompose.rememberMetroSwipe
import kotlinx.coroutines.delay

/** The mini player's content height. [MetroRisingPage] adds the navigation bar to it itself. */
private val MiniPlayerHeight = 62.dp

/**
 * The half of the framework a music player is built out of, all in one screen:
 *
 *  - [MetroBottomBar] for the mini player, which hands its space back *as* it leaves;
 *  - [MetroRisingPage] for the now-playing screen that comes up out of that strip — an overlay, not
 *    a navigation destination, so the list underneath is never torn down;
 *  - [rememberMetroReveal] to pull the strip up, [rememberMetroDismiss] to push the page back down;
 *  - [rememberMetroSwipe] across the artwork to change track;
 *  - [metroSlideIn] on the artwork, [MetroSwap] on the three text lines and [MetroCrossfade] on the
 *    backdrop, which is what a track change looks like on the phone;
 *  - ringed and bare [TransportButton]s, and the hairline [MetroSlider].
 */
@Composable
fun PlayerScreen() {
    val colors = MetroTheme.colors
    var index by rememberSaveable { mutableIntStateOf(0) }
    var playing by rememberSaveable { mutableStateOf(true) }
    var open by rememberSaveable { mutableStateOf(false) }
    var shuffle by rememberSaveable { mutableStateOf(false) }
    var repeat by rememberSaveable { mutableIntStateOf(0) } // 0 off, 1 all, 2 one
    // A plain `remember`: a Set is not one of the types rememberSaveable's default saver handles.
    var starred by remember { mutableStateOf(setOf<Int>()) }

    val track = DemoTracks[index.mod(DemoTracks.size)]
    fun next() { index = (index + 1).mod(DemoTracks.size) }
    fun previous() { index = (index - 1).mod(DemoTracks.size) }

    // Position advances on its own so the scrubber has something to show. Reset per track.
    var position by remember(track) { mutableFloatStateOf(0f) }
    LaunchedEffect(track, playing) {
        while (playing && position < 1f) {
            delay(1000)
            position = (position + 1f / track.seconds).coerceAtMost(1f)
        }
    }

    Box(Modifier.fillMaxSize().pageBackground()) {
        // ---- The library underneath. Nothing here is disposed while the player is open. ----
        Column(Modifier.fillMaxSize().padding(top = 52.dp)) {
            Text(
                "MUSIC",
                color = colors.fg,
                fontFamily = MetroSemilight,
                fontSize = 13.sp,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(start = 24.dp)
            )
            Text(
                "now playing",
                color = colors.fg,
                fontFamily = MetroLight,
                fontSize = 46.sp,
                modifier = Modifier.padding(start = 22.dp, bottom = 12.dp)
            )
            LazyColumn(Modifier.weight(1f)) {
                itemsIndexed(DemoTracks) { i, t ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { index = i; playing = true }
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(44.dp).background(t.tint))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                t.title,
                                color = if (i == index) colors.accent else colors.fg,
                                fontFamily = MetroRegular,
                                fontSize = 22.sp
                            )
                            Text(t.artist, color = colors.subtle, fontFamily = MetroRegular, fontSize = 14.sp)
                        }
                        Text(t.seconds.asClock(), color = colors.subtle, fontFamily = MetroRegular, fontSize = 14.sp)
                    }
                }
                // The list's surface runs to the bottom edge, so the inset lives inside its scroll —
                // plus the strip the mini player occupies.
                item { MetroBottomInset(extra = MiniPlayerHeight) }
            }
        }

        // ---- The strip. Gone while the page it opens is up, so the two never overlap. ----
        MetroBottomBar(
            visible = !open,
            modifier = Modifier.align(Alignment.BottomCenter),
            durationMillis = MetroBarRiseMillis
        ) {
            MiniPlayer(
                track = track,
                playing = playing,
                onPlayPause = { playing = !playing },
                onNext = { next() },
                onOpen = { open = true }
            )
        }

        // ---- The page that rises out of it. An overlay, so Back has to be wired by hand. ----
        MetroRisingPage(
            visible = open,
            fromHeight = MiniPlayerHeight,
            modifier = Modifier.fillMaxSize(),
            // One number for the bar and the page, because they are two halves of the same
            // movement and drift apart the moment they are timed separately.
            durationMillis = MetroBarRiseMillis
        ) {
            NowPlaying(
                track = track,
                playing = playing,
                position = position,
                shuffle = shuffle,
                repeat = repeat,
                starred = index in starred,
                onPlayPause = { playing = !playing },
                onNext = { next() },
                onPrevious = { previous() },
                onShuffle = { shuffle = !shuffle },
                onRepeat = { repeat = (repeat + 1) % 3 },
                onStar = { starred = if (index in starred) starred - index else starred + index },
                onSeek = { position = it },
                onClose = { open = false }
            )
        }
        BackHandler(enabled = open) { open = false }
    }
}

/**
 * The strip itself. Pulling it up opens the page — [rememberMetroReveal] is [rememberMetroDismiss]
 * with the sign flipped, so the gesture and its inverse read as one thing.
 */
@Composable
private fun MiniPlayer(
    track: Track,
    playing: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onOpen: () -> Unit
) {
    val colors = MetroTheme.colors
    val reveal = rememberMetroReveal(onReveal = onOpen)

    Row(
        Modifier
            .fillMaxWidth()
            .height(MiniPlayerHeight)
            .metroDismissDown(reveal)
            .graphicsLayer { translationY = reveal.offset }
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(46.dp).background(track.tint))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, color = colors.fg, fontFamily = MetroRegular, fontSize = 17.sp, maxLines = 1)
            Text(track.artist, color = colors.subtle, fontFamily = MetroRegular, fontSize = 13.sp, maxLines = 1)
        }
        TransportButton(
            icon = if (playing) MetroIcon.Pause else MetroIcon.Play,
            contentDescription = if (playing) "pause" else "play",
            touchSize = 44.dp,
            iconSize = 18.dp,
            onClick = onPlayPause
        )
        TransportButton(
            icon = MetroIcon.Next,
            contentDescription = "next",
            touchSize = 44.dp,
            iconSize = 18.dp,
            onClick = onNext
        )
    }
}

/** The now-playing page: every piece of the signature motion at once. */
@Composable
private fun NowPlaying(
    track: Track,
    playing: Boolean,
    position: Float,
    shuffle: Boolean,
    repeat: Int,
    starred: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onStar: () -> Unit,
    onSeek: (Float) -> Unit,
    onClose: () -> Unit
) {
    val colors = MetroTheme.colors
    // Push the whole page down to send it away — the counterpart of the strip it came out of.
    val dismiss = rememberMetroDismiss(onDismiss = onClose)
    // And swipe the artwork sideways to change track.
    val swipe = rememberMetroSwipe(onNext = onNext, onPrevious = onPrevious)

    Box(
        Modifier
            .fillMaxSize()
            // Its own background on purpose, unlike the pages: this one genuinely has to cover the
            // library underneath, so it must not let a MetroBackdrop show through.
            .background(colors.bg)
            .metroDismissDown(dismiss)
            .graphicsLayer { translationY = dismiss.offset }
    ) {
        // Backdrop: never a blank frame between two pictures, which is what a black flash on a
        // track change is. It drifts as it changes, so the swap has a direction.
        MetroCrossfade(target = track, modifier = Modifier.fillMaxSize()) { t ->
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(listOf(t.tint.copy(alpha = 0.55f), Color(0xFF0A0A0A)))
                    )
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(top = 52.dp, bottom = 12.dp)
        ) {
            Text(
                "NOW PLAYING  ·  DRAG DOWN TO CLOSE",
                color = colors.fg,
                fontFamily = MetroSemilight,
                fontSize = 11.sp,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(start = 24.dp, bottom = 14.dp)
            )

            // Artwork. Two layers following the finger at different fractions is what gives one
            // screen the panorama's sense of depth; the slide-in is unclipped on purpose, so the
            // arriving cover passes over its neighbours.
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .metroSwipe(swipe),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .size(250.dp)
                        .graphicsLayer { translationX = swipe.offset }
                        .metroSlideIn(track.title)
                        .background(track.tint),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        track.album.take(1),
                        color = Color.White.copy(alpha = 0.35f),
                        fontFamily = MetroLight,
                        fontSize = 150.sp
                    )
                }
            }

            // Three lines turning over in sequence: out, a beat with the line empty, then in.
            // Staggering the delay is how the phone swaps track, artist and album.
            Column(Modifier.padding(horizontal = 24.dp)) {
                MetroSwap(track.title, delayMillis = MetroSlideInMillis / 4) {
                    Text(it, color = colors.fg, fontFamily = MetroLight, fontSize = 34.sp, maxLines = 1)
                }
                MetroSwap(track.artist, delayMillis = MetroSlideInMillis / 4 + 70) {
                    Text(it, color = colors.fg, fontFamily = MetroRegular, fontSize = 19.sp, maxLines = 1)
                }
                MetroSwap(track.album, delayMillis = MetroSlideInMillis / 4 + 140) {
                    Text(it, color = colors.subtle, fontFamily = MetroRegular, fontSize = 15.sp, maxLines = 1)
                }
            }

            Spacer(Modifier.height(14.dp))

            // The hairline weight: no thumb, a thin track, and a short box so the bar hugs the
            // artwork instead of floating in a settings-sized touch target.
            Column(Modifier.padding(horizontal = 24.dp)) {
                MetroSlider(
                    value = position,
                    onValueChange = onSeek,
                    secondaryValue = (position + 0.14f).coerceAtMost(1f), // "buffered"
                    onValueChangeFinished = { /* a real player seeks here, not on every pixel */ },
                    trackHeight = 2.dp,
                    thumbSize = null,
                    height = 22.dp
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        (position * track.seconds).toInt().asClock(),
                        color = colors.subtle, fontFamily = MetroRegular, fontSize = 12.sp
                    )
                    Text(
                        track.seconds.asClock(),
                        color = colors.subtle, fontFamily = MetroRegular, fontSize = 12.sp
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            // Ringed for the buttons you aim at without looking, bare for the toggles — and the
            // toggles tint with the accent while they are on rather than swapping glyph.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TransportButton(
                    icon = MetroIcon.Shuffle, contentDescription = "shuffle",
                    active = shuffle, onClick = onShuffle
                )
                TransportButton(
                    icon = MetroIcon.Previous, contentDescription = "previous",
                    ringSize = 48.dp, onClick = onPrevious
                )
                TransportButton(
                    icon = if (playing) MetroIcon.Pause else MetroIcon.Play,
                    contentDescription = if (playing) "pause" else "play",
                    ringSize = 60.dp, iconSize = 26.dp, touchSize = 68.dp, onClick = onPlayPause
                )
                TransportButton(
                    icon = MetroIcon.Next, contentDescription = "next",
                    ringSize = 48.dp, onClick = onNext
                )
                TransportButton(
                    icon = if (repeat == 2) MetroIcon.RepeatOne else MetroIcon.Repeat,
                    contentDescription = "repeat",
                    active = repeat != 0, onClick = onRepeat
                )
                TransportButton(
                    icon = if (starred) MetroIcon.StarFilled else MetroIcon.Star,
                    contentDescription = "favourite",
                    active = starred, onClick = onStar
                )
            }
        }
    }
}

package com.metrocompose.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrocompose.MetroButton
import com.metrocompose.MetroCrossfade
import com.metrocompose.MetroGrowEdge
import com.metrocompose.MetroLight
import com.metrocompose.MetroPage
import com.metrocompose.MetroRegular
import com.metrocompose.MetroSemilight
import com.metrocompose.MetroSlideInEasing
import com.metrocompose.MetroSlideInMillis
import com.metrocompose.MetroSwap
import com.metrocompose.MetroSwipeState
import com.metrocompose.MetroTheme
import com.metrocompose.metroGrowIn
import com.metrocompose.metroSlideIn
import com.metrocompose.metroSwipe
import com.metrocompose.rememberMetroSwipe

/**
 * The signature motion, each piece on its own so the difference between them is visible.
 *
 * Everything here is keyed on one counter: press "next" and every demo below re-runs at once, which
 * is the only way to see that [metroSlideIn] is a *translation* and [metroGrowIn] is a squeeze, that
 * [MetroSwap] leaves the line empty for a beat and [MetroCrossfade] never does.
 */
@Composable
fun MotionScreen() {
    val colors = MetroTheme.colors
    var step by remember { mutableIntStateOf(0) }
    val item = DemoTracks[step.mod(DemoTracks.size)]

    MetroPage("ELEMENTS", "motion") {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MetroButton("next", filled = true) { step++ }
                Spacer(Modifier.size(14.dp))
                Text(
                    "key = ${item.title}",
                    color = colors.subtle, fontFamily = MetroRegular, fontSize = 14.sp
                )
            }

            Spacer(Modifier.height(26.dp))
            MotionLabel("metroSlideIn", "flies in a whole element-width from the edge, unclipped")
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                Box(
                    Modifier
                        .size(120.dp)
                        .metroSlideIn(item.title)
                        .background(item.tint)
                )
                // The same motion arriving from the other edge — `edge` picks which side it
                // travels in from, not which side it is anchored to.
                Box(
                    Modifier
                        .padding(start = 140.dp)
                        .size(120.dp)
                        .graphicsLayer { alpha = 0.55f }
                        .metroSlideIn(item.title, edge = MetroGrowEdge.Start)
                        .background(item.tint)
                )
            }

            Spacer(Modifier.height(26.dp))
            MotionLabel("metroGrowIn", "squeezed flat against an edge, then stretched back out")
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetroGrowEdge.entries.forEach { edge ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .size(66.dp)
                                .metroGrowIn(item.title, edge = edge)
                                .background(item.tint)
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            edge.name,
                            color = colors.subtle, fontFamily = MetroRegular, fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(26.dp))
            MotionLabel("MetroSwap", "out, a beat with the line empty, then in — staggered down the stack")
            Column(Modifier.height(96.dp)) {
                MetroSwap(item.title, delayMillis = 0) {
                    Text(it, color = colors.fg, fontFamily = MetroLight, fontSize = 30.sp, maxLines = 1)
                }
                MetroSwap(item.artist, delayMillis = 70) {
                    Text(it, color = colors.fg, fontFamily = MetroRegular, fontSize = 18.sp, maxLines = 1)
                }
                MetroSwap(item.album, delayMillis = 140) {
                    Text(it, color = colors.subtle, fontFamily = MetroRegular, fontSize = 14.sp, maxLines = 1)
                }
            }

            Spacer(Modifier.height(10.dp))
            MotionLabel("MetroCrossfade", "no blank frame — for a backdrop, where one would read as a flash")
            MetroCrossfade(
                target = item,
                modifier = Modifier.fillMaxWidth().height(120.dp)
            ) { t ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .background(Brush.horizontalGradient(listOf(t.tint, Color(0xFF0A0A0A))))
                )
            }

            Spacer(Modifier.height(26.dp))
            MotionLabel("metroSwipe", "swipe the card — committed swipes are seen through, not undone")
            SwipeDemo()

            Spacer(Modifier.height(26.dp))
            Text(
                "MetroSlideInMillis is ${MetroSlideInMillis}ms, on MetroSlideInEasing " +
                    "(${MetroSlideInEasing.transform(0.25f).format2()} of the way at a quarter of " +
                    "the time — a gentle push off, then a long glide home, rather than starting " +
                    "at full speed). Anything that has to land with the slide, such as the swapped " +
                    "lines above, is expressed against that constant rather than a number of its own.",
                color = colors.subtle, fontFamily = MetroRegular, fontSize = 13.sp
            )
        }
    }
}

/**
 * [rememberMetroSwipe] on its own. The two layers follow the finger at different fractions, which is
 * where the sense of depth comes from — read [MetroSwipeState.offset] inside `graphicsLayer` so only
 * the layer is invalidated, never the composition.
 */
@Composable
private fun SwipeDemo() {
    val colors = MetroTheme.colors
    var index by remember { mutableIntStateOf(0) }
    val swipe = rememberMetroSwipe(
        onNext = { index = (index + 1).mod(DemoTracks.size) },
        onPrevious = { index = (index - 1).mod(DemoTracks.size) }
    )
    val item = DemoTracks[index]

    Box(
        Modifier
            .fillMaxWidth()
            .height(140.dp)
            .background(colors.bg)
            .metroSwipe(swipe),
        contentAlignment = Alignment.Center
    ) {
        // Backdrop at a third of the speed.
        Box(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
                .graphicsLayer { translationX = swipe.offset * 0.33f }
                .background(item.tint.copy(alpha = 0.35f))
        )
        Column(
            Modifier.graphicsLayer { translationX = swipe.offset },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(item.title, color = colors.fg, fontFamily = MetroLight, fontSize = 26.sp)
            Text(item.artist, color = colors.subtle, fontFamily = MetroRegular, fontSize = 14.sp)
        }
    }
}

@Composable
private fun MotionLabel(title: String, subtitle: String) {
    val colors = MetroTheme.colors
    Text(title, color = colors.subtle, fontFamily = MetroSemilight, fontSize = 22.sp)
    Text(
        subtitle,
        color = colors.dim, fontFamily = MetroRegular, fontSize = 13.sp,
        modifier = Modifier.padding(bottom = 12.dp)
    )
}

/** Two decimals, without pulling in a formatter for one line of copy. */
private fun Float.format2(): String = "%.2f".format(this)

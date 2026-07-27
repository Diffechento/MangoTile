package com.metrocompose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.absoluteValue
import kotlinx.coroutines.launch

/**
 * The full WP8 pivot: a header strip above a swipeable page host.
 *
 * Unlike the header-only [Pivot], the headers here are physically tied to the pager — they
 * slide continuously as the finger drags and the titles fade between bright and dim, so the
 * strip tracks the gesture instead of snapping when the page settles. Tapping a header
 * animates to that page.
 *
 * Pages are lazy: [HorizontalPager] composes the current page and its immediate neighbours
 * and discards the rest, so a pivot over four big library lists costs one list, not four.
 *
 *   MetroPivot(listOf("artists", "albums", "songs")) { page ->
 *       when (page) { 0 -> Artists(); 1 -> Albums(); else -> Songs() }
 *   }
 */
@Composable
fun MetroPivot(
    titles: List<String>,
    modifier: Modifier = Modifier,
    state: PagerState = rememberPagerState { titles.size },
    headerSpacing: Dp = 22.dp,
    startPadding: Dp = 24.dp,
    page: @Composable (Int) -> Unit
) {
    Column(modifier) {
        PivotHeaders(titles, state, headerSpacing, startPadding)
        HorizontalPager(state = state, modifier = Modifier.weight(1f)) { index ->
            page(index)
        }
    }
}

/**
 * Split out so that reading the pager's per-frame offset invalidates only the header strip,
 * leaving the pages themselves out of the recomposition.
 */
@Composable
private fun PivotHeaders(
    titles: List<String>,
    state: PagerState,
    headerSpacing: Dp,
    startPadding: Dp
) {
    val colors = MetroTheme.colors
    val scope = rememberCoroutineScope()

    // Measured header widths (each already includes its trailing gap, since onSizeChanged sits
    // outside the padding), so the strip can be shifted by an exact pixel offset.
    val widths = remember(titles) { mutableStateListOf(*Array(titles.size) { 0f }) }

    // Distance from the strip's start to the left edge of header [index].
    fun startOf(index: Int): Float {
        var acc = 0f
        for (i in 0 until index.coerceIn(0, widths.size)) acc += widths[i]
        return acc
    }

    Box(Modifier.fillMaxWidth().clipToBounds()) {
        Row(
            Modifier
                // The strip is wider than the screen by design — the titles past the current one
                // trail off the right edge and the graphicsLayer shift brings them in. Without an
                // unbounded measure the Row hands each title only the width its predecessors left
                // over, so the last one gets nothing and renders as a one-pixel sliver of its
                // first letter, which is then dutifully shifted to the gutter as if it were the
                // whole word.
                .wrapContentWidth(Alignment.Start, unbounded = true)
                .padding(start = startPadding)
                .graphicsLayer {
                    // Deferred read: the shift is recomputed at draw time, not by recomposing.
                    val current = state.currentPage
                    val fraction = state.currentPageOffsetFraction
                    val from = startOf(current)
                    val to = startOf(current + 1)
                    translationX = -(from + fraction * (to - from))
                }
        ) {
            val position = state.currentPage + state.currentPageOffsetFraction
            titles.forEachIndexed { i, title ->
                // Fully bright on the active header, fully dim one page away, interpolated between.
                val distance = (i - position).absoluteValue.coerceIn(0f, 1f)
                Text(
                    text = title,
                    color = lerp(colors.fg, colors.dim, distance),
                    fontFamily = MetroLight,
                    fontSize = 38.sp,
                    maxLines = 1,
                    modifier = Modifier
                        .onSizeChanged { size ->
                            val w = size.width.toFloat()
                            if (i < widths.size && widths[i] != w) widths[i] = w
                        }
                        .padding(end = headerSpacing)
                        .clickable { scope.launch { state.animateScrollToPage(i) } }
                )
            }
        }
    }
}

package com.metrocompose

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * A header that scrolls away with the list under it, and comes back when that list is dragged past
 * its top — the phone's own behaviour on a page whose content is longer than the screen: the thing
 * naming where you are gives up its height to the thing you are reading.
 *
 * The header keeps its place in the layout and gives up its *height*, so what follows it moves up and
 * grows into the space rather than being covered by it. A list underneath therefore gets taller as the
 * header goes, which is the point: the space is only worth taking if the content receives it.
 *
 * Wire it in three lines — one state, one modifier on the header, one on any ancestor of the list:
 *
 *   val collapse = rememberMetroCollapse()
 *   Column(Modifier.metroCollapseOnScroll(collapse)) {
 *       AlbumHeader(Modifier.metroCollapsingHeader(collapse))
 *       LazyColumn { … }
 *   }
 *
 * The order the two halves take a drag is what makes it feel right: the header goes **first**, before
 * the list scrolls at all, and comes back **last**, only once the list has nothing left to give. A
 * header that waited for the list to hit its end would need a second gesture to reappear.
 */
@Stable
class MetroCollapse internal constructor() {

    /** How tall the header is when open; measured by [metroCollapsingHeader], not given. */
    var maxPx: Float = 0f
        internal set

    /** How much of it is currently rolled away, 0f..[maxPx]. */
    var offsetPx: Float by mutableFloatStateOf(0f)
        private set

    /** 0f open, 1f gone — for a header that wants to fade or shrink as it goes. */
    val progress: Float get() = if (maxPx <= 0f) 0f else (offsetPx / maxPx).coerceIn(0f, 1f)

    /**
     * Takes what it can of a vertical drag and answers with what it took.
     *
     * Negative [delta] is the content moving up, which rolls the header away; positive gives it back.
     */
    internal fun drag(delta: Float): Float {
        val target = (offsetPx - delta).coerceIn(0f, maxPx)
        val taken = offsetPx - target
        offsetPx = target
        return taken
    }

    internal suspend fun settle(open: Boolean) {
        val from = offsetPx
        val to = if (open) 0f else maxPx
        if (from == to) return
        animate(from, to, animationSpec = tween(SettleMillis)) { value, _ -> offsetPx = value }
    }

    internal val connection: NestedScrollConnection = object : NestedScrollConnection {

        // Rolling away happens before the list moves: the height nobody is reading goes first.
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
            if (available.y < 0f) Offset(0f, drag(available.y)) else Offset.Zero

        // Coming back happens after the list has run out, which is the top of it.
        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource
        ): Offset = if (available.y > 0f) Offset(0f, drag(available.y)) else Offset.Zero

        // A fling that reaches the top of the list keeps going into the header, so throwing a list
        // back to its start brings the header with it instead of stopping a hair short of it.
        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            if (available.y > 0f && offsetPx > 0f) {
                settle(open = true)
                return available
            }
            return Velocity.Zero
        }
    }

    private companion object {
        const val SettleMillis = 220
    }
}

@Composable
fun rememberMetroCollapse(): MetroCollapse = remember { MetroCollapse() }

/**
 * The header itself: measured at its full height, laid out shorter by however much of it has been
 * rolled away, and drawn lifted by the same amount.
 *
 * Nothing is clipped by this — the content is placed above the node's own bounds — so **clip whatever
 * contains it at the edge the header should vanish behind**: the page's title, the status bar. Without
 * that the header slides over what is above it, which on a phone means a 108sp word across the clock.
 */
fun Modifier.metroCollapsingHeader(
    collapse: MetroCollapse,
    overhang: Dp = 0.dp
): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val extra = overhang.roundToPx()
    collapse.maxPx = (placeable.height + extra).toFloat()
    val rolled = collapse.offsetPx.roundToInt().coerceIn(0, placeable.height + extra)
    // Past its own height the header has no layout left to give and simply keeps travelling, which is
    // what [overhang] is for: ink drawn outside the node — a letter's descender under a trimmed line
    // box — would otherwise stay behind as a sliver along the clip edge after everything else has gone.
    layout(placeable.width, (placeable.height - rolled).coerceAtLeast(0)) {
        placeable.place(0, -rolled)
    }
}

/**
 * Put this on any ancestor of the scrolling thing, so its drags reach the header.
 *
 * It is a separate modifier from [metroCollapsingHeader] because the two live in different places: the
 * height belongs to the header, and the gesture belongs to whatever contains the list. Attaching the
 * connection to the header itself would only hear drags that started on the header.
 */
fun Modifier.metroCollapseOnScroll(collapse: MetroCollapse): Modifier =
    nestedScroll(collapse.connection)

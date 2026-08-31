package com.metrocompose

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The band along the edge that answers to the finger. Wide enough for a thumb laid against the side of
 * the phone, and no wider than the gutter a row keeps to the left of its own text, so what is under it
 * is padding rather than anything to read.
 */
private val StripWidth = 32.dp

/** Where the hairline sits inside the band, and how thick it and its block are. */
private val TrackInset = 8.dp
private val TrackWidth = 2.dp
private val ThumbWidth = 6.dp
private val ThumbHeight = 56.dp

/**
 * The label tile: [MetroLongList]'s own group tile, and deliberately *not* its size or its column.
 * Sat at the header's own 24dp with the header's own 44dp it reads as a group header that has come
 * loose — on the device, dragging past "o" put an identical square carrying "n" a few pixels to the
 * left of it. Bigger, and clear of that column, it reads as what it is: something held under a finger.
 */
private val LabelInset = 40.dp
private val LabelTile = 56.dp
private val LabelRing = 3.dp

/**
 * How far from the block the finger may land and still be taken as having grabbed *it*, carrying it
 * from where it stands instead of jumping it to the finger.
 */
private val GrabMargin = 22.dp

/** How long the hairline stays after a scroll stops, so the thing can be found without being told. */
private const val HintLingerMillis = 700L

/**
 * Fast scroll by the screen's edge: drag down the band along the leading edge of the list this wraps
 * and it travels its whole length under the finger, with the group reached named in a tile beside it.
 *
 * This is the movement a gallery makes, and it is not the movement WP8's own alphabet makes: the jump
 * grid answers *"take me to Н"*, a scrubber answers *"take me a third of the way in"* — a question the
 * grid cannot put, since a third of the way in is not a letter. They are complementary rather than
 * rival, which is why the tile under the finger is deliberately the grid's own tile (a [GroupHeader]):
 * arriving is then read in the same alphabet as jumping.
 *
 * It **wraps** the list rather than sitting over it, and [state] must be that list's own:
 *
 *     MetroEdgeScroll(state, itemCount = items.size, label = { letterOf(items[it]) }) {
 *         LazyColumn(Modifier.fillMaxSize(), state = state) { … }
 *     }
 *
 * [itemCount] is how many lazy items the drag may address, and it is deliberately not always
 * `layoutInfo.totalItemsCount`: a list whose last item is a [MetroBottomInset] would otherwise spend
 * the bottom of the band on a spacer. Left null it asks the layout. [label] is handed a lazy index and
 * answers with what the tile should say — an empty string draws no tile.
 *
 * Five decisions in here are not the obvious ones:
 *
 *  - **The gesture belongs to the list's container, not to a strip laid over it, and that is the whole
 *    reason this composable takes the list as its content.** An overlay with a `pointerInput` in it
 *    takes the hit test away from everything beneath it whether it consumes the events or not — which
 *    on a music library means the left 32dp of every row silently stops playing the song it is on.
 *    Measured exactly that way on the device: a tap at x=40px did nothing while the same tap at x=400
 *    started a track. From the container the band can watch a press, decline it, and let the row have
 *    it.
 *  - **The finger's position is the list's position, not a delta.** A relative scrubber cannot reach
 *    the end of a list from the middle of the band: the travel left under the finger is all the travel
 *    it has, so half a band means half a library and the last letter is out of reach. Absolute mapping
 *    can jump on the first touch, which is what [GrabMargin] is for — land on the block and it is
 *    carried from where it stands, land away from it and the list goes where you pointed, which is how
 *    a scrollbar's track has always behaved.
 *  - **Nothing settles when the finger lifts, on purpose.** Everything else here that follows a finger
 *    ends on [MetroSettleSpring] carrying the gesture's velocity, because it is a surface being thrown.
 *    A scrubber is a *pointer*: coasting on after the lift would land somewhere the finger did not
 *    choose, and choosing is the whole of what the band is for.
 *  - **It consumes on [PointerEventPass.Initial] once the axis is locked**, which is what beats the
 *    `LazyColumn` inside. Both wait on the same touch slop and would cross it on the same event;
 *    consuming that event on the pass that runs from the outside in means the list never starts,
 *    instead of the two of them scrolling one finger twice.
 *  - **Before the lock it consumes nothing, and a press outside the band is dropped at once.** A tap
 *    in the band still plays the row it is on, and a sideways drag there is still whatever surrounds
 *    the list — on a panorama, the section change.
 */
@Composable
fun MetroEdgeScroll(
    state: LazyListState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    itemCount: Int? = null,
    label: (Int) -> String = { "" },
    content: @Composable BoxScope.() -> Unit
) {
    val colors = MetroTheme.colors
    val density = LocalDensity.current

    // The three measurements are three separate derived states rather than one object on purpose:
    // only [worthwhile] is read during composition, and it changes about once in a list's life. Rolled
    // into one value the *whole* of this composable — the wrapped list included — would recompose on
    // every scrolled pixel, because the other two change with it. Measured before that split: 30ms
    // median while dragging, against 18–20ms for an ordinary scroll on the same list.
    val worthwhile by remember(state, itemCount) {
        derivedStateOf {
            val info = state.layoutInfo
            val visible = info.visibleItemsInfo.size
            // Worth offering at all: two screenfuls. Below that the list's own scroll reaches
            // everything in one movement and a hairline down the side earns nothing.
            visible > 0 && (itemCount ?: info.totalItemsCount) > visible * 2
        }
    }
    /** The last index the band may point at. Read from the gesture only, never in composition. */
    val lastIndex by remember(state, itemCount) {
        derivedStateOf { ((itemCount ?: state.layoutInfo.totalItemsCount) - 1).coerceAtLeast(0) }
    }
    /**
     * The furthest the list can rest, which is a screenful short of its end — map the band to *that*
     * and the block reaches the bottom exactly when the list does. Mapping to the last index instead
     * leaves the block short of the bottom at the end of the list, which reads as the scrubber being
     * out by a screenful.
     */
    val restIndex by remember(state, itemCount) {
        derivedStateOf {
            val info = state.layoutInfo
            ((itemCount ?: info.totalItemsCount) - info.visibleItemsInfo.size).coerceAtLeast(1)
        }
    }

    /**
     * Where the list is, as a fraction of its travel, interpolated across the first visible item so the
     * block moves smoothly rather than in one-item steps — on a short list a step is a visible jump.
     */
    val listFraction by remember(state, itemCount) {
        derivedStateOf {
            val info = state.layoutInfo
            val total = itemCount ?: info.totalItemsCount
            val rest = (total - info.visibleItemsInfo.size).coerceAtLeast(1)
            val first = info.visibleItemsInfo.firstOrNull()
            val within =
                if (first == null || first.size <= 0) 0f
                else ((-first.offset).toFloat() / first.size).coerceIn(0f, 1f)
            ((state.firstVisibleItemIndex + within) / rest).coerceIn(0f, 1f)
        }
    }

    var active by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var bandPx by remember { mutableFloatStateOf(0f) }
    // The index the drag is asking for. -1 means "nothing asked", and setting it back to -1 at the end
    // of a gesture re-arms the collector, so dragging to an index the previous drag already used still
    // moves the list.
    var target by remember { mutableIntStateOf(-1) }

    LaunchedEffect(state) {
        snapshotFlow { target }.collectLatest { index -> if (index >= 0) state.scrollToItem(index) }
    }

    // The hairline shows while the list moves and for a moment after, which is the only thing that says
    // the band is there at all. A finger down holds it up for as long as it stays down.
    var linger by remember { mutableStateOf(false) }
    val scrolling = state.isScrollInProgress
    LaunchedEffect(scrolling, active) {
        if (scrolling || active) {
            linger = true
        } else {
            delay(HintLingerMillis)
            linger = false
        }
    }

    val shown = enabled && worthwhile && (active || linger)
    val alpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(if (shown) 120 else 260),
        label = "metro-edge-scroll"
    )

    val stripPx = with(density) { StripWidth.toPx() }
    val thumbPx = with(density) { ThumbHeight.toPx() }
    val trackInsetPx = with(density) { TrackInset.toPx() }
    val trackPx = with(density) { TrackWidth.toPx() }
    val thumbWidthPx = with(density) { ThumbWidth.toPx() }
    val grabMarginPx = with(density) { GrabMargin.toPx() }
    val tilePx = with(density) { LabelTile.toPx() }
    val labelInsetPx = with(density) { LabelInset.roundToPx() }

    // All three read state as they are called rather than as they were composed: the gesture's lambda
    // outlives the composition that made it, and a block grabbed against a stale position jumps.
    fun travel(): Float = (bandPx - thumbPx).coerceAtLeast(1f)
    fun fraction(): Float = if (active) dragFraction else listFraction
    fun thumbCentre(): Float = fraction() * travel() + thumbPx / 2f

    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged { bandPx = it.height.toFloat() }
            .then(
                if (!enabled) Modifier
                else Modifier.pointerInput(Unit) {
                    awaitEachGesture {
                        // Not `requireUnconsumed`: a press the row under the band has already claimed
                        // for its own tap is still a press this may take, because it is the movement
                        // that decides and not who saw the finger land.
                        val down = awaitFirstDown(
                            requireUnconsumed = false,
                            pass = PointerEventPass.Initial
                        )
                        // Anywhere but the band is nothing to do with this. Dropped before a single
                        // event is consumed, so the list under it scrolls as it always did.
                        if (down.position.x > stripPx || !worthwhile) return@awaitEachGesture
                        var locked = false
                        var grab = 0f
                        try {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (change.changedToUpIgnoreConsumed()) break
                                // Absolute positions rather than positionChange(), which answers zero
                                // for a change something else has consumed already — the panorama's
                                // pager consumes the down on this very pass while it is still settling,
                                // and a scrubber driven by deltas would sit dead in exactly that case.
                                val dx = change.position.x - down.position.x
                                val dy = change.position.y - down.position.y
                                if (!locked) {
                                    val slop = viewConfiguration.touchSlop
                                    if (abs(dx) < slop && abs(dy) < slop) continue
                                    // Sideways belongs to whatever surrounds this list — on the home
                                    // screen it changes section — and is left unconsumed.
                                    if (abs(dx) > abs(dy)) return@awaitEachGesture
                                    locked = true
                                    active = true
                                    val offBlock = down.position.y - thumbCentre()
                                    grab =
                                        if (abs(offBlock) <= grabMarginPx + thumbPx / 2f) offBlock
                                        else 0f
                                }
                                change.consume()
                                dragFraction = ((change.position.y - grab - thumbPx / 2f) /
                                    travel()).coerceIn(0f, 1f)
                                target = (dragFraction * restIndex)
                                    .roundToInt()
                                    .coerceIn(0, lastIndex)
                            }
                        } finally {
                            if (locked) {
                                active = false
                                target = -1
                            }
                        }
                    }
                }
            )
    ) {
        content()

        // The hairline and its block. Drawing only, with no pointer input of its own — a node that
        // takes part in hit testing here would be the very thing this composable exists to avoid.
        Box(
            Modifier
                .matchParentSize()
                .drawBehind {
                    if (alpha <= 0.01f) return@drawBehind
                    drawRect(
                        color = colors.dim,
                        alpha = 0.35f * alpha,
                        topLeft = Offset(trackInsetPx, 0f),
                        size = Size(trackPx, size.height)
                    )
                    drawRect(
                        color = colors.accent,
                        alpha = alpha,
                        topLeft = Offset(
                            trackInsetPx - (thumbWidthPx - trackPx) / 2f,
                            fraction() * travel()
                        ),
                        size = Size(thumbWidthPx, thumbPx)
                    )
                }
        )

        // The tile naming where the finger has arrived — the same accent block the group headers and
        // the jump grid carry, so "where am I" is always answered in the same hand. Handed lambdas
        // rather than values so the index is read inside *its* composition scope: read out here, a new
        // index every frame would recompose this whole composable, and the list it wraps with it.
        EdgeScrollLabel(
            text = { if (active && target >= 0) label(target) else "" },
            offset = {
                IntOffset(
                    labelInsetPx,
                    (thumbCentre() - tilePx / 2f)
                        .coerceIn(0f, (bandPx - tilePx).coerceAtLeast(0f))
                        .roundToInt()
                )
            }
        )
    }
}

@Composable
private fun EdgeScrollLabel(text: () -> String, offset: Density.() -> IntOffset) {
    val label = text()
    if (label.isEmpty()) return
    // A band of page colour around it rather than a shadow, which nothing here has: the tile passes
    // over the group headers as it travels, and two accent squares touching become one L-shaped blob.
    Box(
        Modifier
            .offset(offset)
            .background(MetroTheme.colors.bg)
            .padding(LabelRing)
    ) {
        GroupHeader(label = label, enabled = true, tileSize = LabelTile, filled = true)
    }
}

/** The three numbers the band takes off the list, measured together. */
private data class EdgeScrollMetrics(
    val lastIndex: Int,
    val restIndex: Int,
    val worthwhile: Boolean
)

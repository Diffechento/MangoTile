package com.metrocompose

import androidx.compose.animation.core.animate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/** How far the finger has to travel, as a fraction of the page, for a slow drag to change page. */
private const val CommitFraction = 0.24f

/** A flick this fast (px/s) changes page however short it was. */
private const val FlickVelocity = 380f

/**
 * How far a page may be pulled toward a neighbour that is not there, as a fraction of its width.
 * Small: enough to answer the finger, not enough to promise a page.
 */
private const val BlockedTravel = 0.09f

/**
 * One page dragged **between its neighbours**, which are on screen the whole time.
 *
 * This is the difference between a gesture that *is* a transition and one that triggers an animation
 * afterwards. [MetroSwipeState] moves a page under the finger and then either brings it home or throws
 * it out and flies a replacement in; either way the change is a separate movement the hand is no longer
 * part of. Here the neighbouring pages are laid out beside the current one and everything travels
 * together, so letting go only has to finish a movement that has already been made, and the content
 * that arrives is the content that was visibly coming.
 *
 * **Pages are identified by an index the caller owns**, and that is what makes it work against state
 * that changes asynchronously. A player told to skip does not become the next track in the same frame —
 * the command goes to a media session and the new state arrives a frame or several later. Anything that
 * "finishes the animation and then resets the offset" therefore shows one frame of the wrong content.
 * Here every page's position is derived from `page - index`, so when [index] catches up, each page is
 * still exactly where it was drawn and nothing has to be reset in step with anything.
 *
 * Ask [offsetFor] where to draw each page, in a `graphicsLayer` rather than in composition:
 *
 *     val pager = rememberMetroPageSwipe(
 *         index = state.queueIndex,
 *         previousIndex = state.previousIndex,
 *         nextIndex = state.nextIndex,
 *         onSettleTo = player::skipToQueueIndex
 *     )
 *     Box(Modifier.metroPageSwipe(pager)) {
 *         Face(state.current, Modifier.graphicsLayer { translationX = pager.offsetForSlot(0) })
 *         if (pager.active) {
 *             state.previous?.let { Face(it, Modifier.graphicsLayer { translationX = pager.offsetForSlot(-1) }) }
 *             state.next?.let { Face(it, Modifier.graphicsLayer { translationX = pager.offsetForSlot(1) }) }
 *         }
 *     }
 *
 * Compose the neighbours only while [active] if they are expensive — album art at full size is — and
 * remember that at rest only the current page is needed.
 */
@Stable
class MetroPageSwipeState internal constructor(
    private val scope: CoroutineScope,
    private val onSettleTo: (Int) -> Unit,
    private val commitFraction: Float,
    private val flickVelocity: Float
) {

    /** Pixels the pages are displaced by right now: negative dragging toward the next page. */
    var offset: Float by mutableFloatStateOf(0f)
        private set

    /** The page width in px, measured by [Modifier.metroPageSwipe]. */
    var widthPx: Int by mutableIntStateOf(0)
        internal set

    /** The caller's current page, and what lies either side of it, as last reported. */
    private var index: Int = Int.MIN_VALUE
    private var previousIndex: Int? = null
    private var nextIndex: Int? = null

    /** The page a committed gesture is travelling to, and which way, until the caller agrees. */
    private var pendingIndex: Int? = null
    private var pendingStep: Int = 0

    private var running: Job? = null

    /**
     * True while anything other than the current page is on screen — the cue to compose neighbours,
     * and to stop composing them again once the gesture is over.
     */
    val active: Boolean get() = offset != 0f || pendingIndex != null

    /**
     * Where to draw a page, in px from the middle of the screen: [slot] is -1 for the one before the
     * current page, 0 for the current one, +1 for the one after. Read it in a `graphicsLayer` — it
     * changes every frame of a drag, and read there only the layer is invalidated.
     */
    fun offsetForSlot(slot: Int): Float = slot * widthPx.toFloat() + offset

    /**
     * Takes the caller's page and its neighbours every composition, and keeps the pages where they are
     * drawn when the caller's page changes.
     *
     * **Slots, not arithmetic on the index**, because "the next page" is not always "the index plus
     * one": a queue on repeat answers the last track's `next` with the first one. So the caller says
     * which index lies either side, a committed gesture remembers the index it asked for *and the way
     * it went*, and when that index arrives the offset is given back exactly one slot. Every page's
     * position is then unchanged across the frame the new state lands on, which is the whole point —
     * a media session does not answer in the same frame, and anything that finishes the animation and
     * *then* resets the offset shows one frame of the wrong page.
     *
     * A page that arrives without anybody having dragged for it — a track ending, a button — is simply
     * adopted: there is no gesture in flight to keep in step with.
     */
    internal fun sync(current: Int, before: Int?, after: Int?) {
        previousIndex = before
        nextIndex = after
        if (current == index) return
        val awaited = pendingIndex
        val first = index == Int.MIN_VALUE
        index = current
        if (awaited != null && current == awaited) {
            offset += pendingStep * widthPx.toFloat()
            pendingIndex = null
            pendingStep = 0
            settleHome()
        } else {
            pendingIndex = null
            pendingStep = 0
            if (offset != 0f && !first) settleHome() else offset = 0f
        }
    }

    private fun indexForStep(step: Int): Int? = if (step > 0) nextIndex else previousIndex

    /**
     * True while a finger is what stopped an animation, which is the one case where being left between
     * pages is correct. Anything else that kills it — a scope going away, a composition disposed — must
     * end somewhere the pages can be.
     */
    private var handedToFinger: Boolean = false

    internal fun grab() {
        handedToFinger = true
        running?.cancel()
        running = null
    }

    internal fun drag(deltaX: Float) {
        if (deltaX == 0f) return
        val span = widthPx.toFloat().coerceAtLeast(1f)
        val goingAway = offset == 0f || (deltaX < 0f) == (offset < 0f)
        if (!goingAway) {
            offset += deltaX
            return
        }
        // A page that is not there is answered with a ninth of the width and no more; one that is
        // gets the finger exactly, up to its own width.
        val towardNext = deltaX < 0f
        val reachable = indexForStep(if (towardNext) 1 else -1) != null
        val limit = span * if (reachable) 1f else BlockedTravel
        val give = (1f - abs(offset) / limit).coerceIn(0f, 1f)
        offset += deltaX * give
    }

    internal fun settle(velocityX: Float) {
        val span = widthPx.toFloat().coerceAtLeast(1f)
        val towardNext = if (abs(offset) > 8f) offset < 0f else velocityX < 0f
        val step = if (towardNext) 1 else -1
        val wanted = indexForStep(step)
        val committed = wanted != null &&
            (abs(offset) > span * commitFraction || abs(velocityX) > flickVelocity)

        if (!committed || wanted == null) {
            animateOffset(0f, velocityX)
            return
        }
        // Travel the rest of the way, then ask for the page that is now in the middle. The offset is
        // *left* at the edge on purpose: the neighbour is centred there, so what is on screen is
        // already right, and it stays right until the caller's index agrees and [sync] gives the slot
        // back. Nothing has to be timed against the state arriving.
        pendingIndex = wanted
        pendingStep = step
        animateOffset(step * -span, velocityX) {
            onSettleTo(wanted)
            // And a way back if that page never becomes the caller's — a skip the player refused, a
            // queue that changed underneath. Without it the pages would sit off-centre for ever with
            // nothing in flight to bring them home, which is the same shape of bug as a rising page
            // stranded half way up the screen.
            scope.launch {
                delay(HandOverTimeoutMillis)
                if (pendingIndex == wanted) {
                    pendingIndex = null
                    pendingStep = 0
                    settleHome()
                }
            }
        }
    }

    private fun settleHome() {
        animateOffset(0f, 0f)
    }

    private fun animateOffset(to: Float, velocity: Float, onArrive: (() -> Unit)? = null) {
        handedToFinger = false
        running?.cancel()
        running = scope.launch {
            try {
                animate(offset, to, velocity, MetroSettleSpring) { value, _ -> offset = value }
            } finally {
                if (!handedToFinger) offset = to
            }
            onArrive?.invoke()
        }
    }

    private companion object {
        /** Long enough for a media session to answer, short enough not to look like a hang. */
        const val HandOverTimeoutMillis = 700L
    }
}

/**
 * Remembers the state for a page dragged between its neighbours.
 *
 * [index] is the caller's own page number and must be the authoritative one — a queue position, a list
 * index — not something derived from the gesture. [previousIndex] and [nextIndex] say which pages lie
 * either side and are null where there is none, so the end of a queue answers a drag by barely moving
 * instead of promising a page that is not coming; they are also what makes a wrapping queue work, where
 * "the next page" is not the index plus one. [onSettleTo] is asked for one of those indices once the
 * gesture has travelled to it.
 */
@Composable
fun rememberMetroPageSwipe(
    index: Int,
    previousIndex: Int?,
    nextIndex: Int?,
    onSettleTo: (Int) -> Unit,
    commitFraction: Float = CommitFraction,
    flickVelocity: Float = FlickVelocity
): MetroPageSwipeState {
    val scope = rememberCoroutineScope()
    val settleTo by rememberUpdatedState(onSettleTo)
    val state = remember(commitFraction, flickVelocity) {
        MetroPageSwipeState(
            scope = scope,
            onSettleTo = { settleTo(it) },
            commitFraction = commitFraction,
            flickVelocity = flickVelocity
        )
    }
    // In a `SideEffect`, so the rebase happens after the composition that first sees the new index and
    // before that frame is drawn: the layers read the corrected offset, and no frame shows a page in
    // the place it occupied under the old index.
    SideEffect { state.sync(index, previousIndex, nextIndex) }
    return state
}

/**
 * Makes the element answer to sideways drags through [state]. Applies no transform of its own — ask
 * [MetroPageSwipeState.offsetFor] where each page goes.
 *
 * The vertical axis is left alone, so this composes with a page that can also be pushed away: put both
 * through [Modifier.metroRiseDrag], which locks the axis once per gesture.
 */
/**
 * A rising page that can also be paged sideways, with the axis decided once per gesture.
 *
 * The pair a player wants: sideways changes track, downward puts the page away, and a thumb 30 degrees
 * off the horizontal does exactly one of them. Stacking [metroPageSwipe] and [metroRiseDrag] instead
 * gives two independent detectors and both fire — see [Modifier.metroDrag] for why.
 *
 * [upward] is a *third* thing the same surface can do: a page stacked above this one — a queue over a
 * player — which an upward drag opens once this page has nowhere further to rise. Handed to the same
 * detector for the same reason the other two are.
 */
fun Modifier.metroRiseDrag(
    state: MetroRisingPageState,
    pager: MetroPageSwipeState,
    enabled: Boolean = true,
    swipeEnabled: Boolean = true,
    upward: MetroRisingPageState? = null,
    upwardEnabled: Boolean = true
): Modifier = composed {
    val target = remember { MetroRiseTarget() }
    onSizeChanged { pager.widthPx = it.width }
        .metroLockedDrag(
            horizontal = if (swipeEnabled) {
                MetroDragAxis(
                    onGrab = { pager.grab(); true },
                    onDelta = { pager.drag(it) },
                    onStop = { pager.settle(it) }
                )
            } else {
                null
            },
            vertical = metroRiseAxis(
                target = target,
                down = state.takeIf { enabled },
                up = upward?.takeIf { upwardEnabled }
            )
        )
}

fun Modifier.metroPageSwipe(state: MetroPageSwipeState, enabled: Boolean = true): Modifier = composed {
    onSizeChanged { state.widthPx = it.width }
        .metroLockedDrag(
            horizontal = if (enabled) {
                MetroDragAxis(
                    onGrab = { state.grab(); true },
                    onDelta = { state.drag(it) },
                    onStop = { state.settle(it) }
                )
            } else {
                null
            },
            vertical = null
        )
}

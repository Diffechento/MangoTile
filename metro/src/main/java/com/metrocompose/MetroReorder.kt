package com.metrocompose

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.sign

/**
 * How fast the list creeps when the carried row is held against one of its ends, in px per frame at
 * the very edge. Slow on purpose: this is a movement you steer, and a list that bolts takes the row
 * somewhere nobody chose.
 */
private const val EdgeScrollPxPerFrame = 14f

/**
 * A row of a `LazyColumn` picked up by holding it, and the little machine that decides where it lands.
 *
 * **Why the hold, when this framework's own reorderable list used a grip.** A handle is unambiguous and
 * costs a column of the row; holding costs nothing and is what a phone teaches. The two are reconciled
 * here rather than chosen between: the hold *lifts* the row — visibly, under the finger, before it has
 * moved anywhere — and if the finger then lets go without going anywhere, [Modifier.metroReorderRow]
 * says so, so the same hold can still open the context menu it always opened. One gesture, two
 * outcomes, decided by whether the hand moved: nothing is guessed, and no affordance has to be drawn to
 * explain it.
 *
 * **Moves are committed as they happen, not at the end.** Every step reports through `onMove`, so the
 * list the caller owns is the only order in play and the row under the finger is always the row the
 * caller has. Holding the final order back until release would mean drawing a list that disagrees with
 * the caller's for the length of the gesture, and then a frame of the old order at the moment of the
 * hand-over — the same hazard [MetroPageSwipeState] exists to avoid. It does mean the caller's own
 * order has to answer immediately: a list behind an asynchronous player should keep a local copy and
 * send the command, rather than waiting to be told what it already knows.
 *
 * Read [offsetFor] in a `graphicsLayer` — the carried row's displacement changes every frame.
 */
@Stable
class MetroReorderState internal constructor(
    private val scope: CoroutineScope,
    private val listState: LazyListState,
    private val onMove: (Int, Int) -> Unit
) {

    /** Which row is being carried, as its index in the caller's list, or -1. */
    var index: Int by mutableIntStateOf(-1)
        private set

    /** True while a row is up. The cue to draw it as lifted, and to leave the rest alone. */
    val active: Boolean get() = index >= 0

    /**
     * How far the finger is from the slot the carried row occupies *now*, in px — which is the whole of
     * the row's displacement and the reason there is only one number here.
     *
     * A step gives a row's height back the moment it commits, because the list has reordered and the
     * row's own slot has moved down by exactly that: keeping a second running total of the distance
     * travelled and adding the two would draw the row one row below the finger per step, and by the
     * third step it is nowhere near the hand.
     */
    private var held by mutableFloatStateOf(0f)

    private var liftedSize: Int = 0
    private var creep: Job? = null

    /**
     * Where to draw the row at [row], in px from where the list put it.
     *
     * The carried row follows the finger. The rows it has passed step aside by exactly its height,
     * which is what makes the gap under the finger the gap it will land in — and they are *drawn*
     * aside rather than moved, because the caller's list has already been told about every step, so
     * asking it again for the intermediate positions would be asking it twice.
     */
    fun offsetFor(row: Int): Float = if (row == index) held else 0f

    internal fun lift(at: Int) {
        creep?.cancel()
        index = at
        held = 0f
        liftedSize = sizeOf(at) ?: 0
        creep = scope.launch { creepWhileHeld() }
    }

    internal fun drag(delta: Float) {
        if (!active) return
        held += delta
        step()
    }

    internal fun drop() {
        creep?.cancel()
        creep = null
        index = -1
        held = 0f
    }

    /**
     * Commits as many single-place moves as the finger has earned.
     *
     * A *whole* neighbour has to be cleared rather than half of it: the row under the finger is the one
     * being carried, so swapping at the half-way point means the list reorders while the thing you are
     * holding is still over the row it is going to displace, and a slow drag then flickers back and
     * forth across that boundary. A full row of travel is also exactly what the step gives back, so the
     * row stays under the finger across the commit.
     */
    private fun step() {
        while (true) {
            val direction = held.sign.toInt()
            if (direction == 0) return
            val next = index + direction
            if (next < 0 || next > lastIndex()) return
            val span = (sizeOf(next) ?: liftedSize).toFloat()
            if (span <= 0f || abs(held) < span) return
            onMove(index, next)
            index = next
            held -= direction * span
        }
    }

    /**
     * Scrolls the list while the carried row is held against one of its ends, and — the part that is
     * easy to get wrong — feeds the pixels it actually scrolled back in as though the finger had
     * travelled them.
     *
     * It has to: the finger is still, so relative to the *content* the row has moved by whatever went
     * past under it, and that is both what decides the next step and what keeps the row drawn under the
     * finger while its own slot slides away.
     */
    private suspend fun creepWhileHeld() {
        while (active) {
            val speed = edgeSpeed()
            if (speed != 0f) {
                val moved = listState.scrollBy(speed)
                if (moved != 0f) {
                    held += moved
                    step()
                }
            }
            // One frame at a time, so the creep is a speed rather than a race with the drag.
            withFrameNanos { }
        }
    }

    /**
     * How hard to creep: nothing until the carried row is within a row's height of an end, then
     * proportionally, so the speed is something the hand sets by how far it pushes.
     */
    private fun edgeSpeed(): Float {
        val info = listState.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return 0f
        val top = item.offset + held
        val bottom = top + item.size
        val zone = item.size.toFloat().coerceAtLeast(1f)
        val overTop = info.viewportStartOffset + zone - top
        val overBottom = bottom - (info.viewportEndOffset - zone)
        return when {
            overTop > 0f && listState.canScrollBackward ->
                -EdgeScrollPxPerFrame * (overTop / zone).coerceAtMost(1f)
            overBottom > 0f && listState.canScrollForward ->
                EdgeScrollPxPerFrame * (overBottom / zone).coerceAtMost(1f)
            else -> 0f
        }
    }

    private fun sizeOf(row: Int): Int? =
        listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == row }?.size

    /**
     * The last row there is, taken from the list's own count — which includes whatever the caller put
     * after the rows (a bottom inset, a footer), so a row cannot be carried past the end of the list
     * into one of those. Callers with trailing items pass the real count instead.
     */
    private fun lastIndex(): Int = lastRow ?: (listState.layoutInfo.totalItemsCount - 1)

    /** Set by [rememberMetroReorder] when the caller knows better than the lazy list does. */
    internal var lastRow: Int? = null
}

/**
 * Remembers the state behind [Modifier.metroReorderRow].
 *
 * [onMove] is asked to move one row one place, as it happens, and must be answered in the order it is
 * asked — it is the caller's list that the next step is measured against.
 *
 * [rowCount] is how many of the lazy list's items are actually rows. A list that ends with an inset, a
 * footer or a "add to this" tile has more items than rows, and without this a row can be carried into
 * one of them and land somewhere the caller's list has no index for.
 */
@Composable
fun rememberMetroReorder(
    listState: LazyListState,
    rowCount: Int? = null,
    onMove: (from: Int, to: Int) -> Unit
): MetroReorderState {
    val scope = rememberCoroutineScope()
    val move by rememberUpdatedState(onMove)
    val state = remember(listState) {
        MetroReorderState(scope = scope, listState = listState, onMove = { from, to -> move(from, to) })
    }
    state.lastRow = rowCount?.let { it - 1 }
    return state
}

/**
 * Hold this row to pick it up, then drag it to where it belongs.
 *
 * Put it on the row *inside* a lazy item, not on the item itself: it draws the row lifted (translated
 * under the finger, and above its neighbours), which needs a node of its own.
 *
 * [onHeldStill] is the other half of the hold — the finger came off without the row having gone
 * anywhere, so the hold meant what a hold usually means. Give it the row's context menu and both
 * gestures live on one press with nothing to learn: move it and it moves, let go and the menu is there.
 *
 * The hold is detected here rather than with `combinedClickable`, and that is deliberate. A row in a
 * list already has a clickable on it; a second long-press detector on the same node races the first,
 * and whichever wins, the one that lost has already told the user something was about to happen. This
 * one watches the pointer itself, ignores the fact that the row's own click may have claimed the press,
 * and consumes everything from the lift onward — including the release, so a row put down is not also
 * a row tapped.
 */
fun Modifier.metroReorderRow(
    state: MetroReorderState,
    index: Int,
    enabled: Boolean = true,
    onHeldStill: (() -> Unit)? = null
): Modifier = composed {
    val lifted = state.active && state.index == index
    val held by rememberUpdatedState(onHeldStill)
    // The row's index, read through a handle rather than captured. A committed step moves this row to
    // another index, and that must not be allowed to restart the detector — see the note on the
    // `pointerInput` key below.
    val row by rememberUpdatedState(index)
    val viewConfiguration = LocalViewConfiguration.current

    zIndex(if (lifted) 1f else 0f)
        .graphicsLayer { translationY = state.offsetFor(index) }
        .then(
            if (!enabled) {
                Modifier
            } else {
                // Keyed on the state alone, **not on the index**. Keying on the index is the obvious
                // thing and it breaks the gesture on its first step: the row's index is exactly what a
                // commit changes, the handler is torn down and restarted mid-drag, and the coroutine
                // that would have put the row down is cancelled — leaving it lifted, over its
                // neighbours, with no finger and nothing in flight to release it.
                Modifier.pointerInput(state) {
                    awaitEachGesture {
                        // Unconsumed is not required: the row's own clickable has already seen this
                        // press, and that is not a reason to refuse the hold it turns into.
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (!awaitHold(down.id, viewConfiguration.longPressTimeoutMillis)) {
                            return@awaitEachGesture
                        }

                        state.lift(row)
                        var travelled = 0f
                        try {
                            while (true) {
                                // The **Initial** pass, which is dispatched outer-first, and that is the
                                // whole reason this works: the row's own clickable is *inside* this node,
                                // and on the Main pass an inner node is asked first — so it would see the
                                // release before this could consume it and the row would be tapped as well
                                // as moved. Consuming here also takes the drag off the list, which would
                                // otherwise scroll under the finger.
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                // Read the movement *before* consuming: `positionChange` answers zero for a
                                // change that is already consumed, which is exactly how a whole drag turns
                                // into a row that lifts and then refuses to move.
                                val delta = change.positionChange().y
                                change.consume()
                                if (!change.pressed) break
                                travelled += abs(delta)
                                state.drag(delta)
                            }
                        } finally {
                            // Released, cancelled, or the item disposed under the finger: a row is
                            // never left up with nothing holding it.
                            state.drop()
                        }
                        // A hold that went nowhere is a hold, not a move. The slop is the same
                        // threshold everything else in the framework asks the hand for. Reached only
                        // when the gesture ended by itself — a cancelled one throws past this.
                        if (travelled <= viewConfiguration.touchSlop) held?.invoke()
                    }
                }
            }
        )
}

/**
 * Waits out the hold. True if the finger was still on the row for [timeoutMillis]; false if it left
 * early or set off in any direction, which is a tap or a scroll and belongs to somebody else.
 */
private suspend fun AwaitPointerEventScope.awaitHold(
    pointerId: PointerId,
    timeoutMillis: Long
): Boolean {
    // `withTimeoutOrNull` answering null *is* the hold: the loop only ends when the gesture has turned
    // into something else, and every one of those paths answers with a value.
    val ended = withTimeoutOrNull(timeoutMillis) {
        var travel = 0f
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == pointerId }
            if (change == null || !change.pressed) break
            travel += change.positionChange().getDistance()
            if (travel > viewConfiguration.touchSlop) break
        }
        true
    }
    return ended == null
}

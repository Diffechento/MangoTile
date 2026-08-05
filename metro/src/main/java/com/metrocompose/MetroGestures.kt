package com.metrocompose

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

/**
 * How far the finger has to travel, as a fraction of the element's width, for a slow drag to count
 * as "move on" rather than "put it back".
 */
private const val CommitFraction = 0.24f

/**
 * A flick this fast (px/s) commits however short it was. Deliberately low: on the phone a quick
 * dismissive flick moves to the next item even though the content barely left its place.
 */
private const val FlickVelocity = 380f

/**
 * How far a swipe may pull content toward something that is not there — the end of the queue — as a
 * fraction of the element's width.
 *
 * Not zero, because a gesture that does nothing at all is indistinguishable from one the app failed
 * to notice; and small, because the content coming back from a tenth of the screen is the answer
 * ("there is nothing that way") while coming back from half of it is a promise that was broken.
 */
private const val BlockedTravel = 0.09f

/** Time an uncommitted drag takes to fall back into place, if it has to be a duration. */
private const val ArriveMillis = 280

/**
 * The slowest a committed swipe is allowed to leave (px/s), so a slow, deliberate drag past the
 * commit point still goes rather than crawling off the screen over a second.
 */
private const val MinCarrySpeed = 1400f

/** A flick faster than this is a slip rather than an intention; velocity trackers can say anything. */
private const val MaxFlickVelocity = 8000f

/** Bounds on how long the leave may take, whatever arithmetic the velocity produces. */
private const val MinCarryMillis = 70
private const val MaxCarryMillis = 240

/**
 * How long content should take to travel [distance] px if it carries on at [velocity], which is what
 * makes the leave continue the gesture instead of restarting it: a `LinearEasing` tween of exactly
 * this length leaves at exactly the speed the finger had.
 */
private fun carryMillis(distance: Float, velocity: Float): Int =
    (distance / max(abs(velocity), MinCarrySpeed) * 1000f).toInt()
        .coerceIn(MinCarryMillis, MaxCarryMillis)

/**
 * The live sideways displacement of a swipeable page, and the little state machine that decides what
 * happens when the finger comes off.
 *
 * Read [offset] inside a `graphicsLayer` block rather than in composition — it changes every frame,
 * and read that way only the layer is invalidated:
 *
 *     val swipe = rememberMetroSwipe(onNext = player::next, onPrevious = player::previous)
 *     Box(Modifier.metroSwipe(swipe)) {
 *         Backdrop(Modifier.graphicsLayer { translationX = swipe.offset * 0.33f })
 *         Content(Modifier.graphicsLayer { translationX = swipe.offset })
 *     }
 *
 * Layers may follow at different fractions, which is what gives one screen the panorama's sense of
 * depth.
 */
class MetroSwipeState internal constructor(
    private val onNext: () -> Unit,
    private val onPrevious: () -> Unit,
    private val commitFraction: Float,
    private val flickVelocity: Float,
    private val carryThrough: Boolean = true,
    private val canGoNext: () -> Boolean = { true },
    private val canGoPrevious: () -> Boolean = { true }
) {

    private var live by mutableFloatStateOf(0f)
    internal var width by mutableIntStateOf(0)

    /** Pixels the content is displaced by right now: negative dragging toward "next". */
    val offset: Float get() = live

    /** True while the content is anywhere other than home, i.e. a swipe is in flight. */
    val active: Boolean get() = live != 0f

    private fun reachable(towardNext: Boolean): Boolean =
        if (towardNext) canGoNext() else canGoPrevious()

    /**
     * Follows the finger, with two limits that both read as answers rather than as stiffness.
     *
     * Content tracks the hand exactly — anything less is the gesture feeling detached from it — until
     * it approaches what it is allowed to travel, and then gives progressively less. The allowance is
     * a whole element width where there is something to move to, and [BlockedTravel] of one where
     * there is not, which is how the end of a queue answers a swipe.
     *
     * Resistance applies only to travel *away* from home; coming back is always one-to-one, so the
     * hand is never fighting the thing it is putting back.
     */
    internal fun onDrag(delta: Float) {
        if (delta == 0f) return
        val span = width.toFloat().coerceAtLeast(1f)
        val goingAway = live == 0f || (delta < 0f) == (live < 0f)
        if (!goingAway) {
            live += delta
            return
        }
        val limit = span * if (reachable(delta < 0f)) 1f else BlockedTravel
        val give = (1f - abs(live) / limit).coerceIn(0f, 1f)
        live += delta * give
    }

    /**
     * Committed swipes are seen through rather than undone: the content carries on out of the frame
     * at the speed the finger left it, and the next one comes in from the far edge. Spring the
     * content back to the middle at the same moment as the track changes — which is what this used
     * to do — and the two motions fight, which is the "jerky, no animation" complaint. Nothing is
     * ever left displaced: every path here ends at exactly zero.
     *
     * Two different curves, for two different jobs. The leave is a **linear** tween whose length is
     * derived from the finger's own speed ([carryMillis]), because linear is the only easing whose
     * first frame can be made to match the hand exactly and because content leaving the screen should
     * hold its speed rather than politely decelerate at the edge. Everything that comes *home*
     * instead ends on [MetroSettleSpring], which continues the gesture for the same reason and cannot
     * overshoot into a bounce.
     */
    internal suspend fun onDragStopped(velocity: Float) {
        val span = width.toFloat().coerceAtLeast(1f)
        // Which way it went: the displacement decides once there is any, otherwise the flick does.
        val forward = if (abs(live) > 8f) live < 0f else velocity < 0f
        // A flick toward the end of the queue is not a change of track, however hard it was thrown.
        val committed = reachable(forward) &&
            (abs(live) > span * commitFraction || abs(velocity) > flickVelocity)

        // Without carry-through the page comes home and the *content* answers for the change, which
        // is the right shape when what is on the page mostly stays: sweeping a whole player sideways
        // to move between two tracks of one album flips a cover for an identical cover, and the eye
        // reads that as the picture changing when it did not. The gesture still follows the finger —
        // that is the feedback — and what actually differs then animates on its own.
        if (!committed || !carryThrough) {
            if (committed) {
                if (forward) onNext() else onPrevious()
            }
            animate(live, 0f, velocity, MetroSettleSpring) { v, _ -> live = v }
            live = 0f
            return
        }

        val exit = if (forward) -span else span
        try {
            animate(
                initialValue = live,
                targetValue = exit,
                animationSpec = tween(carryMillis(abs(exit - live), velocity), easing = LinearEasing)
            ) { v, _ -> live = v }
            if (forward) onNext() else onPrevious()
            // Off-screen on the other side, so the hand-over itself is never seen.
            live = -exit
            // Decelerating, and starting at full speed: the replacement is not being set in motion,
            // it is the same movement continuing on the other side of the frame.
            animate(live, 0f, 0f, tween(ArriveMillis, easing = LinearOutSlowInEasing)) { v, _ ->
                live = v
            }
            live = 0f
        } finally {
            // Grabbing the content again in the middle of the hand-over cancels this, and cancelling
            // it half a screen out would leave the *new* track's page sitting off the edge with no
            // gesture in flight to bring it back. Whatever was in the air, home is where it belongs.
            if (abs(live) >= span * 0.999f) live = 0f
        }
    }
}

/**
 * A page being pushed down out of the way, and the decision about whether it goes.
 *
 * The counterpart of [MetroRisingPage]: what came up out of the bar goes back down into it, and on a
 * phone that means dragging it there rather than reaching for Back.
 *
 * For a page that should be *dragged* between the two states rather than nudged and released, see
 * [MetroRisingPageState] — there the finger owns the whole travel and this state is not involved.
 * This one is for the lighter case: a page that answers a push with a push and decides at the end.
 *
 * On release the callback fires *first* and the offset unwinds over the same time as the page's own
 * drop animation, so the two overlap into one movement instead of the page snapping back up and then
 * falling.
 */
class MetroDismissState internal constructor(
    private val onDismiss: () -> Unit,
    private val commitFraction: Float,
    private val flickVelocity: Float,
    private val followFraction: Float,
    private val downward: Boolean = true,
    private val maxTravelFraction: Float = 0.55f
) {

    private var live by mutableFloatStateOf(0f)
    internal var height by mutableIntStateOf(0)

    /**
     * How far the content is being held from home, in pixels: positive for a page being pushed down,
     * negative for a strip being pulled up. Only ever on the one side the gesture works in.
     */
    val offset: Float get() = live

    /**
     * Follows the finger exactly as far as the decision is still open, and then progressively less.
     *
     * The resistance starts at precisely the point where letting go would commit, so the hand is told
     * where the threshold is by feel rather than being left to guess — and it can then push on to
     * [maxTravelFraction] of the element without the page pretending it is coming off.
     *
     * It used to follow at a flat half speed for the whole travel, on the reasoning that a page which
     * tracks the finger exactly promises a dismissal it might not perform. That is true of the *end*
     * of the gesture and wrong at the start of it, where halving everything reads as the page being
     * dragged through treacle — the single most common way a gesture is called unsmooth.
     */
    internal fun onDrag(delta: Float) {
        if (delta == 0f) return
        val goingHome = if (downward) delta < 0f else delta > 0f
        val give = if (goingHome) 1f else resistance()
        val moved = live + delta * followFraction * give
        live = if (downward) moved.coerceAtLeast(0f) else moved.coerceAtMost(0f)
    }

    private fun resistance(): Float {
        val span = height.toFloat().coerceAtLeast(1f)
        val free = span * commitFraction
        val limit = span * maxTravelFraction
        val over = abs(live) - free
        if (over <= 0f) return 1f
        return (1f - over / (limit - free).coerceAtLeast(1f)).coerceIn(0f, 1f)
    }

    internal suspend fun onDragStopped(velocity: Float) {
        val span = height.toFloat().coerceAtLeast(1f)
        val far = abs(live) > span * commitFraction
        val flicked = if (downward) velocity > flickVelocity else velocity < -flickVelocity
        val committed = far || flicked
        if (committed) onDismiss()
        // A committed gesture unwinds from rest on purpose: the page's own drop is what the eye
        // follows from here, and handing this half the finger's speed as well would send the offset
        // further out before it came back, against the movement it is supposed to be part of.
        animate(live, 0f, if (committed) 0f else velocity, MetroSettleSpring) { v, _ -> live = v }
        live = 0f
    }
}

/**
 * Remembers the state for a page that can be pushed down to dismiss it.
 *
 * [commitFraction] is of the element's height; a flick past [flickVelocity] counts however short it
 * was, which is how a phone answers a quick flick down. [followFraction] scales the whole gesture
 * (1f being the finger's own speed) and [maxTravelFraction] is where the resistance asymptotes.
 */
@Composable
fun rememberMetroDismiss(
    onDismiss: () -> Unit,
    commitFraction: Float = 0.18f,
    flickVelocity: Float = 900f,
    followFraction: Float = 1f,
    maxTravelFraction: Float = 0.55f
): MetroDismissState {
    val dismiss by rememberUpdatedState(onDismiss)
    return remember(commitFraction, flickVelocity, followFraction, maxTravelFraction) {
        MetroDismissState(
            onDismiss = { dismiss() },
            commitFraction = commitFraction,
            flickVelocity = flickVelocity,
            followFraction = followFraction,
            maxTravelFraction = maxTravelFraction
        )
    }
}

/**
 * Remembers the state for a strip that can be pulled *up* to open what it summarises — a mini player
 * that becomes the player.
 *
 * The same machine as [rememberMetroDismiss] with the sign flipped, so the pair reads as one gesture
 * and its inverse: pull the strip up to open the page, push the page down to put it back.
 *
 * Where the page is a [MetroRisingPage], prefer [rememberMetroRisingPage] and
 * [Modifier.metroRiseDrag]: there the same pull *is* the page rising, rather than nudging a strip
 * which then triggers an animation of its own.
 */
@Composable
fun rememberMetroReveal(
    onReveal: () -> Unit,
    commitFraction: Float = 0.35f,
    flickVelocity: Float = 600f,
    followFraction: Float = 1f,
    maxTravelFraction: Float = 0.9f
): MetroDismissState {
    val reveal by rememberUpdatedState(onReveal)
    return remember(commitFraction, flickVelocity, followFraction, maxTravelFraction) {
        MetroDismissState(
            onDismiss = { reveal() },
            commitFraction = commitFraction,
            flickVelocity = flickVelocity,
            followFraction = followFraction,
            downward = false,
            maxTravelFraction = maxTravelFraction
        )
    }
}

/**
 * Makes the element answer to a vertical drag through [state] — downward for a
 * [rememberMetroDismiss], upward for a [rememberMetroReveal]; the state decides which way it listens.
 * Applies no transform of its own; read `state.offset` in a `graphicsLayer` as with [metroSwipe].
 *
 * For an element that answers to *both* axes — a player that changes track sideways and closes
 * downward — use [Modifier.metroDrag] instead of stacking this and [metroSwipe]. Two one-axis
 * detectors on one element do not arbitrate: a diagonal drag can start both of them.
 */
fun Modifier.metroDismissDown(state: MetroDismissState, enabled: Boolean = true): Modifier = composed {
    val drag = rememberDraggableState { delta -> state.onDrag(delta) }
    onSizeChanged { state.height = it.height }
        .draggable(
            state = drag,
            orientation = Orientation.Vertical,
            enabled = enabled,
            onDragStopped = { velocity -> state.onDragStopped(velocity) }
        )
}

/**
 * Remembers the state for one swipeable page. [onNext] fires for a swipe toward the start of the
 * screen (finger right to left) and [onPrevious] for the other direction.
 *
 * [canGoNext] and [canGoPrevious] are asked before a swipe is allowed to commit *and* while the
 * finger is still down, which is what lets the end of a queue answer a swipe with resistance instead
 * of with a full flight out of the frame and a track that never changes. They are read on every drag
 * frame, so keep them cheap — a flag off a state object, not a search.
 */
@Composable
fun rememberMetroSwipe(
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    commitFraction: Float = CommitFraction,
    flickVelocity: Float = FlickVelocity,
    carryThrough: Boolean = true,
    canGoNext: () -> Boolean = { true },
    canGoPrevious: () -> Boolean = { true }
): MetroSwipeState {
    // Rebuilding the state whenever a callback changes identity would drop an in-flight swipe, so
    // the state is kept and the callbacks are reached through a handle that recomposition updates.
    val next by rememberUpdatedState(onNext)
    val previous by rememberUpdatedState(onPrevious)
    val forward by rememberUpdatedState(canGoNext)
    val backward by rememberUpdatedState(canGoPrevious)
    return remember(commitFraction, flickVelocity, carryThrough) {
        MetroSwipeState(
            onNext = { next() },
            onPrevious = { previous() },
            commitFraction = commitFraction,
            flickVelocity = flickVelocity,
            carryThrough = carryThrough,
            canGoNext = { forward() },
            canGoPrevious = { backward() }
        )
    }
}

/**
 * Makes the element answer to sideways swipes through [state]. Applies no transform of its own —
 * how, and how much, the content follows the finger is up to the caller's `graphicsLayer`.
 *
 * See [Modifier.metroDrag] for an element that answers to both axes.
 */
fun Modifier.metroSwipe(state: MetroSwipeState, enabled: Boolean = true): Modifier = composed {
    val drag = rememberDraggableState { delta -> state.onDrag(delta) }
    onSizeChanged { state.width = it.width }
        .draggable(
            state = drag,
            orientation = Orientation.Horizontal,
            enabled = enabled,
            onDragStopped = { velocity -> state.onDragStopped(velocity) }
        )
}

/**
 * Both axes on one element, with the axis decided **once** per gesture.
 *
 * This exists because stacking [metroSwipe] and [metroDismissDown] on the same node does not work as
 * well as it reads. Each is a one-axis `draggable`, each waits for the touch slop to be crossed in
 * its *own* direction, and neither knows the other is there: a drag that is 30° off the horizontal
 * crosses both thresholds, and the element then goes sideways and downward at once, or the first
 * detector to claim the pointer wins and the gesture that fires is not the one that was made. On a
 * player, where sideways means "next track" and downward means "put this away", that is the whole
 * gesture surface behaving unpredictably at the angles a thumb actually produces.
 *
 * Here one detector watches the pointer, locks the axis to whichever component was larger when the
 * slop was crossed, and gives every later frame to that axis alone. The other state is not touched
 * for the rest of the gesture, so nothing arrives at half strength and no gesture half-fires.
 *
 * A locked axis with no state to drive (`null`, or switched off) is dropped rather than swallowed:
 * the pointer is left unconsumed, so a list underneath still scrolls. Anything a child consumes
 * first — a slider being dragged, a row being held — ends this gesture at once for the same reason.
 */
fun Modifier.metroDrag(
    swipe: MetroSwipeState? = null,
    dismiss: MetroDismissState? = null,
    swipeEnabled: Boolean = true,
    dismissEnabled: Boolean = true
): Modifier = composed {
    val horizontal = swipe?.takeIf { swipeEnabled }
    val vertical = dismiss?.takeIf { dismissEnabled }
    onSizeChanged {
        horizontal?.width = it.width
        vertical?.height = it.height
    }.metroLockedDrag(
        horizontal = horizontal?.let { s ->
            MetroDragAxis(onDelta = { s.onDrag(it) }, onStop = { s.onDragStopped(it) })
        },
        vertical = vertical?.let { s ->
            MetroDragAxis(onDelta = { s.onDrag(it) }, onStop = { s.onDragStopped(it) })
        }
    )
}

/**
 * One half of a two-axis gesture: what a frame of it does, and what letting go does.
 *
 * [onGrab] is handed the travel that crossed the slop — **signed**, so an axis that means two different
 * things in its two directions can decide which of them this gesture is (a player pushed down goes
 * away, pulled up shows its queue) — and answers whether it is taking the gesture at all. Answering
 * false leaves the pointer unconsumed, exactly as a null axis does, so a surface underneath still gets
 * it: an axis that is only half live must not swallow the direction it has nothing to do with.
 */
internal class MetroDragAxis(
    val onGrab: (Float) -> Boolean = { true },
    val onDelta: (Float) -> Unit,
    val onStop: suspend (Float) -> Unit
)

private enum class MetroAxis { Horizontal, Vertical }

/** Holds the settle so the next grab can cancel it; a plain `var` in a lambda would not survive. */
private class MetroSettleHolder {
    var job: Job? = null
}

/**
 * The one gesture detector behind [metroDrag] and [Modifier.metroRiseDrag]: waits for the slop,
 * decides the axis, and from then on hands every frame to that axis and consumes the pointer.
 *
 * The settle is launched into the composition's scope rather than run here, so a second gesture can
 * cancel it and take over from wherever the content had got to — which is what makes a page
 * grabbable while it is still moving. A cancelled settle leaves the state where it stands; every
 * state that can be interrupted mid-flight is responsible for not being left somewhere impossible.
 */
internal fun Modifier.metroLockedDrag(
    horizontal: MetroDragAxis?,
    vertical: MetroDragAxis?
): Modifier = composed {
    val scope = rememberCoroutineScope()
    val settle = remember { MetroSettleHolder() }
    val across = rememberUpdatedState(horizontal)
    val down = rememberUpdatedState(vertical)

    pointerInput(horizontal == null, vertical == null) {
        awaitEachGesture {
            // Unconsumed is not required: a press may already have been claimed by whatever is being
            // pressed, and that is not a reason to ignore the drag it turns into.
            val first = awaitFirstDown(requireUnconsumed = false)
            val tracker = VelocityTracker()
            tracker.addPosition(first.uptimeMillis, first.position)

            var travelX = 0f
            var travelY = 0f
            var axis: MetroAxis? = null
            var chosen: MetroDragAxis? = null

            while (true) {
                val event = awaitPointerEvent()
                // The pointer is gone (cancelled, or taken over by the system). Whatever the finger
                // had already moved still has to be settled, so this leaves the loop rather than the
                // gesture: a page abandoned half way up with no settle would stay there.
                val change = event.changes.firstOrNull { it.id == first.id } ?: break
                // A child took it — a slider under the thumb, a row being held. Only worth asking
                // before the axis is locked; after it, every frame is consumed here by design.
                if (axis == null && change.isConsumed) return@awaitEachGesture
                if (!change.pressed) break
                tracker.addPosition(change.uptimeMillis, change.position)
                val delta = change.positionChange()

                if (axis == null) {
                    travelX += delta.x
                    travelY += delta.y
                    val slop = viewConfiguration.touchSlop
                    if (abs(travelX) < slop && abs(travelY) < slop) continue
                    // Larger component wins, and wins for the whole gesture.
                    val locked =
                        if (abs(travelX) >= abs(travelY)) MetroAxis.Horizontal else MetroAxis.Vertical
                    val wanted =
                        if (locked == MetroAxis.Horizontal) across.value else down.value
                    // Nothing here answers to that axis — or nothing answers to the *direction* it
                    // went in: leave the pointer alone so something else can have it.
                    if (wanted == null) return@awaitEachGesture
                    val travel = if (locked == MetroAxis.Horizontal) travelX else travelY
                    // The settle is cancelled only once the axis has agreed to take over: a gesture
                    // that is declined must leave whatever is in flight to finish on its own.
                    if (!wanted.onGrab(travel)) return@awaitEachGesture
                    settle.job?.cancel()
                    axis = locked
                    chosen = wanted
                    change.consume()
                    // The slop itself is not travel — the same as `draggable`, and the reason a drag
                    // does not begin with a jump of a finger's width.
                    continue
                }

                change.consume()
                chosen?.onDelta(if (axis == MetroAxis.Horizontal) delta.x else delta.y)
            }

            val locked = axis ?: return@awaitEachGesture
            val ending = chosen ?: return@awaitEachGesture
            val velocity = tracker.calculateVelocity()
            val speed = (if (locked == MetroAxis.Horizontal) velocity.x else velocity.y)
                .coerceIn(-MaxFlickVelocity, MaxFlickVelocity)
            settle.job = scope.launch { ending.onStop(speed) }
        }
    }
}

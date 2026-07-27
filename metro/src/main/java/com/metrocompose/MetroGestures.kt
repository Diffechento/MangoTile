package com.metrocompose

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.onSizeChanged
import kotlin.math.abs

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

/** Time the committed content takes to leave, and the replacement to come in. */
private const val LeaveMillis = 170
private const val ArriveMillis = 280

/** Time an uncommitted drag takes to fall back into place. */
private const val ReturnMillis = 240

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
    private val carryThrough: Boolean = true
) {

    private var live by mutableFloatStateOf(0f)
    internal var width by mutableIntStateOf(0)

    /** Pixels the content is displaced by right now: negative dragging toward "next". */
    val offset: Float get() = live

    /** True while the content is anywhere other than home, i.e. a swipe is in flight. */
    val active: Boolean get() = live != 0f

    internal fun onDrag(delta: Float) {
        live += delta
    }

    /**
     * Committed swipes are seen through rather than undone: the content carries on out of the frame
     * at the speed the finger left it, and the next one comes in from the far edge. Spring the
     * content back to the middle at the same moment as the track changes — which is what this used
     * to do — and the two motions fight, which is the "jerky, no animation" complaint. Nothing is
     * ever left displaced: every path here ends at exactly zero.
     */
    internal suspend fun onDragStopped(velocity: Float) {
        val span = width.toFloat().coerceAtLeast(1f)
        val committed = abs(live) > span * commitFraction || abs(velocity) > flickVelocity
        // Which way it went: the displacement decides once there is any, otherwise the flick does.
        val forward = if (abs(live) > 8f) live < 0f else velocity < 0f

        // Without carry-through the page comes home and the *content* answers for the change, which
        // is the right shape when what is on the page mostly stays: sweeping a whole player sideways
        // to move between two tracks of one album flips a cover for an identical cover, and the eye
        // reads that as the picture changing when it did not. The gesture still follows the finger —
        // that is the feedback — and what actually differs then animates on its own.
        if (committed && !carryThrough) {
            if (forward) onNext() else onPrevious()
            animate(live, 0f, velocity, tween(ReturnMillis, easing = LinearOutSlowInEasing)) { v, _ ->
                live = v
            }
            live = 0f
            return
        }

        if (!committed) {
            animate(live, 0f, velocity, tween(ReturnMillis, easing = LinearOutSlowInEasing)) { v, _ ->
                live = v
            }
            live = 0f
            return
        }

        val exit = if (forward) -span else span
        animate(live, exit, velocity, tween(LeaveMillis, easing = FastOutLinearInEasing)) { v, _ ->
            live = v
        }
        if (forward) onNext() else onPrevious()
        // Off-screen on the other side, so the hand-over itself is never seen.
        live = -exit
        animate(live, 0f, animationSpec = tween(ArriveMillis, easing = LinearOutSlowInEasing)) { v, _ ->
            live = v
        }
        live = 0f
    }
}

/**
 * A page being pushed down out of the way, and the decision about whether it goes.
 *
 * The counterpart of [MetroRisingPage]: what came up out of the bar goes back down into it, and on a
 * phone that means dragging it there rather than reaching for Back. The page follows the finger at
 * [followFraction] of its speed — damped, because it is not going anywhere until you let go, and a
 * page that tracks the finger exactly promises a dismissal it might not perform.
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
    private val downward: Boolean = true
) {

    private var live by mutableFloatStateOf(0f)
    internal var height by mutableIntStateOf(0)

    /**
     * How far the content is being held from home, in pixels: positive for a page being pushed down,
     * negative for a strip being pulled up. Only ever on the one side the gesture works in.
     */
    val offset: Float get() = live

    internal fun onDrag(delta: Float) {
        val moved = live + delta * followFraction
        live = if (downward) moved.coerceAtLeast(0f) else moved.coerceAtMost(0f)
    }

    internal suspend fun onDragStopped(velocity: Float) {
        val span = height.toFloat().coerceAtLeast(1f)
        val far = abs(live) > span * commitFraction
        val flicked = if (downward) velocity > flickVelocity else velocity < -flickVelocity
        val committed = far || flicked
        if (committed) onDismiss()
        animate(live, 0f, if (committed) 0f else velocity, tween(ReturnMillis, easing = LinearOutSlowInEasing)) { v, _ ->
            live = v
        }
        live = 0f
    }
}

/**
 * Remembers the state for a page that can be pushed down to dismiss it.
 *
 * [commitFraction] is of the element's height; a flick past [flickVelocity] counts however short it
 * was, which is how a phone answers a quick flick down.
 */
@Composable
fun rememberMetroDismiss(
    onDismiss: () -> Unit,
    commitFraction: Float = 0.18f,
    flickVelocity: Float = 900f,
    followFraction: Float = 0.5f
): MetroDismissState {
    val dismiss by rememberUpdatedState(onDismiss)
    return remember(commitFraction, flickVelocity, followFraction) {
        MetroDismissState({ dismiss() }, commitFraction, flickVelocity, followFraction)
    }
}

/**
 * Remembers the state for a strip that can be pulled *up* to open what it summarises — a mini player
 * that becomes the player.
 *
 * The same machine as [rememberMetroDismiss] with the sign flipped, so the pair reads as one gesture
 * and its inverse: pull the strip up to open the page, push the page down to put it back.
 */
@Composable
fun rememberMetroReveal(
    onReveal: () -> Unit,
    commitFraction: Float = 0.35f,
    flickVelocity: Float = 600f,
    followFraction: Float = 0.5f
): MetroDismissState {
    val reveal by rememberUpdatedState(onReveal)
    return remember(commitFraction, flickVelocity, followFraction) {
        MetroDismissState(
            onDismiss = { reveal() },
            commitFraction = commitFraction,
            flickVelocity = flickVelocity,
            followFraction = followFraction,
            downward = false
        )
    }
}

/**
 * Makes the element answer to a vertical drag through [state] — downward for a
 * [rememberMetroDismiss], upward for a [rememberMetroReveal]; the state decides which way it listens.
 * Applies no transform of its own; read `state.offset` in a `graphicsLayer` as with [metroSwipe].
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
 */
@Composable
fun rememberMetroSwipe(
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    commitFraction: Float = CommitFraction,
    flickVelocity: Float = FlickVelocity,
    carryThrough: Boolean = true
): MetroSwipeState {
    // Rebuilding the state whenever a callback changes identity would drop an in-flight swipe, so
    // the state is kept and the callbacks are reached through a handle that recomposition updates.
    val next by rememberUpdatedState(onNext)
    val previous by rememberUpdatedState(onPrevious)
    return remember(commitFraction, flickVelocity, carryThrough) {
        MetroSwipeState({ next() }, { previous() }, commitFraction, flickVelocity, carryThrough)
    }
}

/**
 * Makes the element answer to sideways swipes through [state]. Applies no transform of its own —
 * how, and how much, the content follows the finger is up to the caller's `graphicsLayer`.
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

package com.metrocompose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.delay

/**
 * Which edge of the box stays put while [Modifier.metroGrowIn] opens it.
 *
 * [End] is the player's: new artwork arrives squeezed against its right edge and stretches back
 * out leftward, which is how the phone swaps a cover.
 */
enum class MetroGrowEdge { Start, End, Top, Bottom }

/**
 * How long [Modifier.metroSlideIn] travels by default.
 *
 * Seven frames off a Lumia is ~300ms; judged side by side that was called too fast three times, at
 * 300, then 440. The duration was never the whole story — `LinearOutSlowInEasing` leaves at full
 * speed and spends most of the distance in the first third, so however long the tail is, the *start*
 * still snaps. [MetroSlideInEasing] fixes the start and this fixes the length. Anything that has to
 * line up with the slide — a stack of [MetroSwap]s waiting for the artwork, say — should be
 * expressed against this constant.
 */
const val MetroSlideInMillis = 600

/**
 * The curve [Modifier.metroSlideIn] travels on: a gentle push off, then a long glide home.
 *
 * Not `LinearOutSlowInEasing`, which starts at maximum speed — an element that appears already
 * moving flat out reads as a jump cut however long it then takes to settle. The small ease-in is
 * what turns the same travel into something that was *set* moving.
 */
val MetroSlideInEasing = CubicBezierEasing(0.22f, 0f, 0.05f, 1f)

/**
 * Reopens the element from one edge whenever [key] changes — the content is squeezed flat against
 * that edge and stretches back out, taking its own drawing with it.
 *
 * Deliberately does nothing on first composition: this animates *updates*, so arriving on a screen
 * stays the job of the page transition, or of the continuum if the element is flying in.
 *
 *   AlbumArt(album, modifier = Modifier.metroGrowIn(album.id))
 */
fun Modifier.metroGrowIn(
    key: Any?,
    edge: MetroGrowEdge = MetroGrowEdge.End,
    durationMillis: Int = 360
): Modifier = composed {
    val open = remember { Animatable(1f) }
    val seenFirstKey = remember { mutableStateOf(false) }

    LaunchedEffect(key) {
        if (!seenFirstKey.value) {
            seenFirstKey.value = true
            return@LaunchedEffect
        }
        open.snapTo(0f)
        open.animateTo(1f, tween(durationMillis, easing = LinearOutSlowInEasing))
    }

    graphicsLayer {
        transformOrigin = when (edge) {
            MetroGrowEdge.Start -> TransformOrigin(0f, 0.5f)
            MetroGrowEdge.End -> TransformOrigin(1f, 0.5f)
            MetroGrowEdge.Top -> TransformOrigin(0.5f, 0f)
            MetroGrowEdge.Bottom -> TransformOrigin(0.5f, 1f)
        }
        when (edge) {
            MetroGrowEdge.Start, MetroGrowEdge.End -> scaleX = open.value
            MetroGrowEdge.Top, MetroGrowEdge.Bottom -> scaleY = open.value
        }
    }
}

/**
 * Flies the element in from one edge whenever [key] changes: the new content starts a whole
 * element-width (or height) beyond [edge] and travels into place, decelerating.
 *
 * This is what the phone's now-playing artwork does when the track changes, and it is a
 * *translation*, not the squeeze [metroGrowIn] performs — measured off a Lumia, the incoming
 * cover's two edges move by the same amount, seven frames from off-screen to home.
 *
 * Deliberately unclipped, and deliberately does nothing on first composition. The cover arriving
 * over its neighbours is the effect; and arriving on a screen is the job of the page transition,
 * or of the continuum if the element is flying in from another page.
 *
 *   AlbumArt(album, modifier = Modifier.metroSlideIn(album.id))
 */
fun Modifier.metroSlideIn(
    key: Any?,
    edge: MetroGrowEdge = MetroGrowEdge.End,
    durationMillis: Int = MetroSlideInMillis
): Modifier = composed {
    val travel = remember { Animatable(0f) }
    val seenFirstKey = remember { mutableStateOf(false) }

    LaunchedEffect(key) {
        if (!seenFirstKey.value) {
            seenFirstKey.value = true
            return@LaunchedEffect
        }
        travel.snapTo(1f)
        travel.animateTo(0f, tween(durationMillis, easing = MetroSlideInEasing))
    }

    graphicsLayer {
        when (edge) {
            MetroGrowEdge.Start -> translationX = -size.width * travel.value
            MetroGrowEdge.End -> translationX = size.width * travel.value
            MetroGrowEdge.Top -> translationY = -size.height * travel.value
            MetroGrowEdge.Bottom -> translationY = size.height * travel.value
        }
    }
}

/**
 * Turns [content] over whenever [target] changes, in the phone's three beats: the old words fade
 * out, the line stays **empty** for a moment, and then the new ones fade in. The position never
 * moves — WP8 swaps the words in place, it does not slide them.
 *
 * The empty beat is the whole point and is why this is not a cross-fade. Measured off a Lumia
 * changing track: the title's ink falls to nothing over about six frames, three frames pass with
 * the line blank, and the new title takes seven more to arrive. A cross-fade overlaps the two
 * instead, which reads as a blur rather than as words being replaced.
 *
 * [delayMillis] holds the whole sequence back, and is what makes a stack of these look right: give
 * each line slightly more delay than the one above it and they turn over in order instead of
 * blinking as one block. On the phone the text also waits for the artwork to land before it starts
 * at all, so the delay is generally worth more than you would guess — a few hundred milliseconds.
 *
 * The defaults are much quicker than the frame counts above (which were 180 / 120 / 260): with the
 * delay in front of them the measured version was judged too slow against the phone twice, because
 * three beats plus a lead read as one long wait rather than as words being swapped. The *shape* —
 * out, blank, in — is what carries the resemblance; the beats themselves want to be brisk, and the
 * text wants to finish about when the artwork lands rather than long after it.
 */
@Composable
fun <T> MetroSwap(
    target: T,
    modifier: Modifier = Modifier,
    durationMillis: Int = 130,
    delayMillis: Int = 0,
    fadeOutMillis: Int = 100,
    holdMillis: Int = 50,
    content: @Composable (T) -> Unit
) {
    // What is on screen right now, which lags [target] by the fade-out plus the empty beat.
    var shown by remember { mutableStateOf(target) }
    val fade = remember { Animatable(1f) }

    LaunchedEffect(target) {
        // The waits have to answer to the system's animation scale exactly as the fades do —
        // an `Animatable` reads it for itself, a plain `delay` does not. Without this, turning
        // animations off in Accessibility leaves the words blinking out and back with the pauses
        // still in place, and slowing the scale down to inspect a frame distorts the sequence.
        val scale = coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
        suspend fun pause(millis: Int) {
            if (millis > 0 && scale > 0f) delay((millis * scale).toLong())
        }

        if (shown != target) {
            pause(delayMillis)
            if (fade.value > 0f) {
                fade.animateTo(0f, tween(fadeOutMillis, easing = FastOutLinearInEasing))
            }
            shown = target
            // Nothing is visible here: the new content is already composed, at zero alpha.
            pause(holdMillis)
        }
        // Also the recovery path if [target] came back to what is already shown mid-fade.
        if (fade.value < 1f) {
            fade.animateTo(1f, tween(durationMillis, easing = LinearOutSlowInEasing))
        }
    }

    Box(modifier.graphicsLayer { alpha = fade.value }) {
        content(shown)
    }
}

/**
 * Turns one picture into another without ever showing what is behind them.
 *
 * Unlike [MetroSwap], the two overlap: the outgoing content stays until the incoming one has faded
 * up over it. That is the right shape for a backdrop — a blank beat in the middle of a full-bleed
 * image is a black flash, and a black flash is what a track change looked like before this.
 *
 * [slideFraction] gives the change a direction: the incoming picture drifts in by that fraction of
 * its width while the outgoing one drifts the same distance further, so the two move together like
 * layers at different depths rather than dissolving in place. Keep it small — this is a backdrop,
 * and it is not supposed to compete with the thing in front of it.
 */
@Composable
fun <T> MetroCrossfade(
    target: T,
    modifier: Modifier = Modifier,
    durationMillis: Int = 520,
    slideFraction: Float = 0.10f,
    content: @Composable (T) -> Unit
) {
    // What is on screen: the current value, plus whatever it is replacing until the fade is done.
    var shown by remember { mutableStateOf(target) }
    var leaving by remember { mutableStateOf<T?>(null) }
    val progress = remember { Animatable(1f) }

    LaunchedEffect(target) {
        if (shown == target) return@LaunchedEffect
        leaving = shown
        shown = target
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis, easing = LinearOutSlowInEasing))
        leaving = null
    }

    Box(modifier) {
        val outgoing = leaving
        if (outgoing != null) {
            Box(
                Modifier.graphicsLayer {
                    alpha = 1f - progress.value
                    translationX = -size.width * slideFraction * progress.value
                }
            ) {
                content(outgoing)
            }
        }
        Box(
            Modifier.graphicsLayer {
                alpha = progress.value
                translationX = size.width * slideFraction * (1f - progress.value)
            }
        ) {
            content(shown)
        }
    }
}

/**
 * WP8 "tilt" press feedback: while held, the element tips in 3D toward the finger and
 * shrinks slightly, then springs back on release. THE signature Metro touch response.
 *
 * It reads presses from a shared [interactionSource], so put the same source on the
 * element's `clickable(...)`:
 *
 *   val press = remember { MutableInteractionSource() }
 *   Box(Modifier.metroTilt(press).clickable(interactionSource = press, indication = null) { ... })
 */
fun Modifier.metroTilt(
    interactionSource: MutableInteractionSource,
    maxTiltDegrees: Float = 10f,
    pressedScale: Float = 0.94f
): Modifier = composed {
    var size by remember { mutableStateOf(IntSize.Zero) }
    var pressPos by remember { mutableStateOf<Offset?>(null) }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> pressPos = interaction.pressPosition
                is PressInteraction.Release -> pressPos = null
                is PressInteraction.Cancel -> pressPos = null
            }
        }
    }

    // Press position normalized to [-0.5, 0.5] from the element's center.
    val pos = pressPos
    val nx = if (pos != null && size.width > 0) (pos.x / size.width - 0.5f) else 0f
    val ny = if (pos != null && size.height > 0) (pos.y / size.height - 0.5f) else 0f
    val active = pos != null

    val rotY by animateFloatAsState(if (active) nx * maxTiltDegrees else 0f, tween(120), label = "tiltY")
    val rotX by animateFloatAsState(if (active) -ny * maxTiltDegrees else 0f, tween(120), label = "tiltX")
    val scale by animateFloatAsState(if (active) pressedScale else 1f, tween(120), label = "tiltScale")

    this
        .onSizeChanged { size = it }
        .graphicsLayer {
            rotationX = rotX
            rotationY = rotY
            scaleX = scale
            scaleY = scale
            cameraDistance = 16f * density // gentle perspective
        }
}

package com.metrocompose

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * WP8-flavored "turnstile" page transition for AnimatedContent: the incoming page
 * swings in from the right with a slight scale-up while the outgoing one slides away.
 *
 * Set [reverse] when going *back* so the motion mirrors — the page that was pushed off to
 * the left comes back from the left. [MetroNavHost] does this for you.
 *
 *   AnimatedContent(nav, transitionSpec = { metroTurnstile() }) { screen -> ... }
 */
fun <S> AnimatedContentTransitionScope<S>.metroTurnstile(
    durationMillis: Int = 300,
    reverse: Boolean = false
): ContentTransform {
    val floatSpec = tween<Float>(durationMillis)
    val direction = if (reverse) -1 else 1
    val enter = slideInHorizontally(tween(durationMillis)) { full -> direction * full / 5 } +
        scaleIn(floatSpec, initialScale = 0.92f) +
        fadeIn(floatSpec)
    val exit = slideOutHorizontally(tween(durationMillis)) { full -> -direction * full / 8 } +
        fadeOut(floatSpec)
    return enter togetherWith exit
}

/**
 * How long a bar takes to give way and a page to rise out of it. One number for both, because they
 * are two halves of the same movement and drift apart the moment they are timed separately.
 */
const val MetroBarRiseMillis = 320

/**
 * A strip along the bottom of the screen that comes and goes — a mini player, a now-playing bar —
 * animated the way it has to be to pair with [metroRiseFromBar].
 *
 * It hands its space back *as* it leaves, rather than keeping it and releasing it all at once when
 * the animation ends: a plain slide-out does the latter, and the page above then jumps by the
 * bar's height at the moment the transition lands. Shrinking towards the bottom instead, the strip
 * is swallowed downward while the page grows into the space it had, and the page rising out of it
 * arrives in step.
 *
 * The strip carries the navigation bar itself: its surface runs to the bottom edge of the screen and
 * its content sits above the gesture bar. Leaving that to the caller is what produces the usual bad
 * result — a bar that stops short, with a band of whatever is behind it, or the system's own scrim,
 * showing underneath.
 *
 *   MetroBottomBar(visible = playing && current != NowPlaying) { MiniPlayer(...) }
 *
 * [background] paints the bar's whole surface, **including the strip under the navigation bar** that
 * the content is kept clear of. That is the point of it being here rather than in the caller's own
 * content: a bar whose content draws its own picture leaves the inset flat, and the seam between the
 * two is a band of bare colour along the bottom edge of the screen. It is drawn in a box already
 * given `matchParentSize`, as [MetroPanorama]'s backdrop is, so the lambda only has to fill it.
 */
@Composable
fun MetroBottomBar(
    visible: Boolean,
    modifier: Modifier = Modifier,
    durationMillis: Int = MetroBarRiseMillis,
    background: (@Composable BoxScope.() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = expandVertically(tween(durationMillis), expandFrom = Alignment.Bottom) +
            fadeIn(tween(durationMillis)),
        exit = shrinkVertically(tween(durationMillis), shrinkTowards = Alignment.Bottom) +
            fadeOut(tween(durationMillis / 2))
    ) {
        // The flat colour stays underneath whatever [background] draws, so a picture that has not
        // loaded yet — or one with any transparency in it — still has something opaque behind it.
        Box(Modifier.background(MetroTheme.colors.bg)) {
            if (background != null) {
                // Clipped, because the useful thing to draw here is a *piece* of something bigger —
                // the top of a picture the page above will show all of — and that is expressed by
                // overflowing this box with `requiredHeight` and letting it be cut off here.
                Box(Modifier.matchParentSize().clipToBounds(), content = background)
            }
            // `navigationBarsPadding` consumes the inset, so a caller that pads again gets no gap
            // twice. It is on the content alone: the surface above reaches the bottom of the screen.
            Box(Modifier.navigationBarsPadding()) {
                content()
            }
        }
    }
}

/**
 * A whole page that comes up out of a bar along the bottom of the screen — a now-playing screen
 * opening from a mini player. It starts with only its top [fromHeight] showing, in the strip the
 * bar occupied, and rises to cover the screen; going away, it drops back into the strip.
 *
 * Deliberately an overlay over the app rather than a destination in a [MetroNavHost], and that is
 * the point of it existing. As a destination, the page it covers is torn down while it is open and
 * has to be composed again from nothing on the way back — a whole panorama's worth of lists, in the
 * same frames as the animation, which stutters visibly. Here nothing underneath is touched: opening
 * and closing are one layer moving. Pair with [MetroBottomBar] on the strip itself, and install a
 * `BackHandler` next to it so system Back closes the page:
 *
 *   MetroRisingPage(visible = playerOpen, fromHeight = MiniPlayerHeight) { NowPlayingScreen() }
 *   BackHandler(enabled = playerOpen) { playerOpen = false }
 *
 * Note that an overlay is outside the navigation host, so continuum can't pair anything with it.
 */
@Composable
fun MetroRisingPage(
    visible: Boolean,
    fromHeight: Dp,
    modifier: Modifier = Modifier,
    durationMillis: Int = MetroBarRiseMillis,
    content: @Composable () -> Unit
) {
    // [fromHeight] is the bar's *content* height. The navigation bar under it is added here, because
    // [MetroBottomBar] draws the strip's surface over that inset too, so the strip on screen is that
    // much taller than the caller's number. The page itself stays full-bleed on purpose — a backdrop
    // that stops at the gesture bar is precisely what this is avoiding — so inset whatever inside it
    // can be pressed.
    val barInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val restingPx = with(LocalDensity.current) { (fromHeight + barInset).roundToPx() }
    // Rises decelerating and leaves accelerating — the WP8 asymmetry, and the difference between a
    // page that drops into the strip and one that seems to give up a third of the way down: a
    // decelerating exit spends its last frames barely moving, so the removal at the end reads as the
    // page vanishing rather than arriving.
    // Opaque the whole way: what shows in the strip is the page itself, not a ghost of it. Only
    // the travel is animated.
    val rise = tween<IntOffset>(durationMillis, easing = LinearOutSlowInEasing)
    val drop = tween<IntOffset>(durationMillis, easing = FastOutLinearInEasing)
    val resting: (Int) -> Int = { full -> (full - restingPx).coerceAtLeast(0) }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = slideInVertically(rise, initialOffsetY = resting),
        exit = slideOutVertically(drop, targetOffsetY = resting)
    ) {
        content()
    }
}

/**
 * The same page as [MetroRisingPage] above, except that a finger can hold it anywhere between the
 * strip and the screen.
 *
 * **Why this exists.** With a boolean, a pull on the strip and the page's rise are two different
 * movements: the strip is nudged a little way under the thumb, a threshold decides, and then a
 * canned 320ms animation plays from wherever the page happens to be. The hand feels one thing, the
 * eye then watches another, and the join between them is exactly what gets reported as a gesture
 * that is not smooth. Here the pull *is* the rise — [progress] is the page's position and the finger
 * writes it directly — and letting go only finishes a movement that is already underway, from the
 * speed the hand was going at.
 *
 * Everything else about the component is unchanged: it is an overlay rather than a destination,
 * nothing underneath is torn down, and the page is full-bleed so a backdrop reaches the bottom edge
 * of the screen.
 *
 * Drive it with [Modifier.metroRiseDrag] on both ends of the gesture — the strip it comes out of and
 * the page itself — and keep the app's own boolean, which taps, Back and a widget still set:
 *
 *     val rising = rememberMetroRisingPage(playerOpen, MiniPlayerHeight) { playerOpen = it }
 *     MetroRisingPage(rising) { NowPlayingScreen(Modifier.metroRiseDrag(rising)) }
 *     MiniPlayer(Modifier.metroRiseDrag(rising))
 */
@Stable
class MetroRisingPageState internal constructor(
    private val scope: CoroutineScope,
    private val onOpenChange: (Boolean) -> Unit,
    private val commitFraction: Float,
    private val flickVelocity: Float,
    initiallyOpen: Boolean
) {

    /**
     * Where the page is: 0f resting in the strip, 1f covering the screen.
     *
     * Read it in a `graphicsLayer` and not in composition — a drag writes it every frame.
     */
    var progress: Float by mutableFloatStateOf(if (initiallyOpen) 1f else 0f)
        private set

    /**
     * Whether the page needs to exist at all, which is *not* [progress] compared against zero: this
     * is a plain boolean that changes twice per open-and-close, so composing the page reads a flag
     * rather than a value that moves every frame.
     */
    var present: Boolean by mutableStateOf(initiallyOpen)
        private set

    /** Where it is heading. Also what an external request is compared against, to avoid restarting. */
    private var target: Float = if (initiallyOpen) 1f else 0f

    /** Distance between the two states in px, measured by the page and guessed before it exists. */
    internal var travelPx: Float = 0f

    /** How much of the page shows while it rests in the strip: the strip's height plus its inset. */
    internal var restingPx: Float = 0f

    /**
     * How tall the page is, in px — which is what the **strip** needs if it wants to draw the same
     * picture the page does.
     *
     * At rest the page is not composed at all, so whatever the strip paints is what the eye is
     * looking at; the instant a drag begins, the page appears over exactly that rectangle. If the two
     * do not agree the picture jumps at the start of every gesture — a cover cropped into a short wide
     * band and the same cover cropped into a whole screen are at completely different magnifications,
     * and that step is precisely what the rise was supposed not to have. Draw the strip's copy in a
     * box this tall, aligned to the strip's *top*, and the two are the same drawing.
     */
    val pageHeightPx: Float get() = travelPx + restingPx

    /**
     * How far down the page is being held right now, in px: 0 when it covers the screen, the whole
     * travel when it rests in the strip.
     *
     * Public because a layer *inside* the page may need to undo it. A backdrop is the case this exists
     * for: drawn as part of the page it travels with the page, so the picture slides upward as the page
     * rises and every part of the screen shows a different piece of it on the way — which is read, quite
     * reasonably, as the picture changing rather than being uncovered. Undo this on that layer and the
     * picture stands still in screen coordinates while the page becomes a widening window onto it.
     */
    val offsetPx: Float get() = (1f - progress) * travelPx

    /** Which end the gesture started from, so the same fraction can mean "open it" and "close it". */
    private var grabbedOpen: Boolean = initiallyOpen

    private var running: Job? = null

    internal fun measured(pageHeight: Float) {
        if (pageHeight > restingPx) travelPx = pageHeight - restingPx
    }

    private fun place(value: Float) {
        progress = value
        present = value > 0f
    }

    /** Cancels whatever is in flight and leaves the page where it stands, ready to be dragged on. */
    internal fun grab() {
        handedToFinger = true
        running?.cancel()
        running = null
        grabbedOpen = target >= 1f
    }

    /**
     * True while a finger is what stopped the animation, which is the one case where being left part
     * way is correct. Anything *else* that kills it — a scope going away underneath, a composition
     * being disposed — must not leave a page hanging in the middle of the screen with nothing in
     * flight to finish it, so the end position is claimed on the way out instead.
     */
    private var handedToFinger: Boolean = false

    internal fun drag(deltaY: Float) {
        val travel = travelPx.coerceAtLeast(1f)
        // Upward is negative in screen coordinates and opening in this one. Clamped rather than
        // rubber-banded: past either end there is nothing to show, and a page that can be pulled
        // above the top of the screen is a page with a gap under it.
        place((progress - deltaY / travel).coerceIn(0f, 1f))
    }

    /**
     * Decides which end the page is going to and starts it on its way, from the finger's own speed.
     *
     * The threshold is measured from wherever the gesture *started*, so one number means "a bit of
     * the way open" from the strip and "a bit of the way closed" from the page — the asymmetry a pair
     * of separate fractions used to encode, without the two of them being fractions of different
     * things (a 75dp strip and a whole screen) and therefore incomparable.
     */
    internal fun settle(velocityY: Float) {
        val upward = -velocityY
        val open = when {
            upward > flickVelocity -> true
            upward < -flickVelocity -> false
            grabbedOpen -> progress > 1f - commitFraction
            else -> progress > commitFraction
        }
        // The app's boolean is set first, so Back, the strip and this all agree about what is open
        // while the movement finishes.
        onOpenChange(open)
        animateTo(open, velocityY)
    }

    /**
     * Fire-and-forget, so it can be called from a pointer loop as well as from composition.
     *
     * Two different things have to be dropped here, and telling them apart is the whole of it:
     *
     *  - **An echo of a movement already under way.** [settle] sets the app's boolean before it
     *    animates, so the caller's `LaunchedEffect(open)` asks for the same end a frame later. Acting
     *    on that would cancel the running animation and restart it from rest, throwing away the very
     *    velocity this was all for.
     *  - **A request to go where it already is.** Nothing to do.
     *
     * What must *not* be dropped is the third case, which is the one a plain `target == to` guard got
     * wrong: a drag released **without committing** ends where it began, so `target` is unchanged
     * while `progress` is somewhere in the middle and nothing is animating. Dropping that leaves the
     * page hanging half way up the screen for ever, with no gesture in flight to bring it home — the
     * exact symptom of letting go of the strip a little too early.
     */
    internal fun animateTo(open: Boolean, velocityY: Float = 0f) {
        val to = if (open) 1f else 0f
        if (target == to && (running?.isActive == true || progress == to)) return
        target = to
        handedToFinger = false
        running?.cancel()
        running = scope.launch {
            try {
                val travel = travelPx.coerceAtLeast(1f)
                // Progress is a fraction, so the finger's px/s becomes fractions/s, and the spring is
                // told what one pixel is worth so its tail is cut where it stops being visible.
                animate(progress, to, -velocityY / travel, metroSettleSpring(0.5f / travel)) { value, _ ->
                    place(value)
                }
            } finally {
                // Reached, cancelled or thrown, the page ends up somewhere it can be: a coroutine
                // scope belongs to a composition and compositions go away — while the insets settle
                // at startup, when a parent is disposed — and an animation killed halfway would
                // otherwise leave the page stopped in the middle of the screen with no gesture in
                // flight to bring it home. A finger is the one thing allowed to stop it there,
                // because a finger is going to decide.
                if (!handedToFinger) place(to)
            }
        }
    }
}

/**
 * Remembers the state for a [MetroRisingPage] a finger can hold anywhere.
 *
 * [open] is the app's own truth — set by a tap, by Back, by a widget — and the page follows it. A
 * drag reports the other way, through [onOpenChange], so the two never disagree about what is open.
 *
 * [fromHeight] is the strip's *content* height, as with the boolean overload; the navigation bar
 * under it is added here. Zero is the other case — a page that rises out of the bottom edge of the
 * screen rather than out of a strip, which is what a second page stacked over the first one does — and
 * then nothing is added, because there is no strip whose inset to inherit.
 *
 * **Pass [windowHeight] if anything reads [MetroRisingPageState.pageHeightPx].** Without it the page's
 * height is guessed from the configuration until the page has been composed once and can measure
 * itself, and that guess is wrong by the system bars: `screenHeightDp` is the space an app is given,
 * while the page — full-bleed, by design — is as tall as the window. Being 8% out costs almost nothing
 * on the drag itself, and everything to a strip trying to draw the top of the same picture. The caller
 * knows the number exactly: put a `BoxWithConstraints` where the page will go and hand over its
 * `maxHeight`.
 */
@Composable
fun rememberMetroRisingPage(
    open: Boolean,
    fromHeight: Dp,
    commitFraction: Float = 0.15f,
    flickVelocity: Float = 500f,
    windowHeight: Dp = Dp.Unspecified,
    onOpenChange: (Boolean) -> Unit
): MetroRisingPageState {
    val scope = rememberCoroutineScope()
    val changed by rememberUpdatedState(onOpenChange)
    val state = remember(commitFraction, flickVelocity) {
        MetroRisingPageState(
            scope = scope,
            onOpenChange = { changed(it) },
            commitFraction = commitFraction,
            flickVelocity = flickVelocity,
            initiallyOpen = open
        )
    }

    val density = LocalDensity.current
    val barInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // A page that comes out of a strip shows that strip's height *and* the navigation bar the strip
    // draws over. A page that comes out of the bottom edge of the screen instead — `fromHeight` of
    // zero, which is what a second page stacked over the first one does — has no strip to inherit, and
    // adding the inset there leaves a band of the page's own top showing along the bottom edge for the
    // last frames of every drop, then vanishing when the page is finally removed.
    state.restingPx =
        if (fromHeight > 0.dp) with(density) { (fromHeight + barInset).toPx() } else 0f
    if (windowHeight.isSpecified) {
        // Told, not guessed, and re-read every composition so a rotation or a resize is simply the
        // next value. `isSpecified` and not `!= Dp.Unspecified`: that constant is a NaN, and NaN is
        // not equal to itself, so the obvious comparison is true even when nothing was passed.
        state.travelPx = (with(density) { windowHeight.toPx() } - state.restingPx).coerceAtLeast(1f)
    } else if (state.travelPx <= 0f) {
        // A bootstrap travel, because the first drag happens on the strip — before the page exists
        // and can measure itself. Wrong by the system bars (see the note above), which one gesture's
        // worth of arithmetic can live with; the page corrects it the moment it composes, and nothing
        // jumps when it does because what is stored is the position, not the pixels it came from.
        val screen = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
        state.travelPx = (screen - state.restingPx).coerceAtLeast(1f)
    }

    // Taps, Back and the widget. The first run is a no-op: the state was built already agreeing.
    LaunchedEffect(open) { state.animateTo(open) }
    return state
}

/**
 * The page itself. Composed only while it is anywhere other than resting, so closing it really does
 * remove it — and while it rests, the strip underneath is live again.
 */
@Composable
fun MetroRisingPage(
    state: MetroRisingPageState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    if (!state.present) return
    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged { state.measured(it.height.toFloat()) }
            .graphicsLayer {
                // The one place [progress] is read: a drag invalidates this layer and nothing else.
                translationY = state.offsetPx
                // Clipped to the page, and clipped *here* rather than by a modifier of its own,
                // because a layer's clip is applied before its transform: the content is cut to the
                // page's own rectangle and the whole thing then travels. Without it, anything a page
                // deliberately draws outside its bounds — a backdrop overscaled so panning never
                // exposes an edge is the usual one — hangs above the page's top edge while it rises,
                // as a band of tinted nothing over the library. It only shows part-way, which is why
                // a page that could only be all the way open or all the way shut never revealed it.
                clip = true
            }
    ) {
        content()
    }
}

/**
 * Hands a vertical drag to a [MetroRisingPageState] — the pull that opens the page and the push that
 * puts it back, which are the same gesture in the two directions and are therefore one modifier.
 *
 * Put it on the strip *and* on the page. [swipe] is the other axis, if the element has one: the
 * player changes track sideways, and passing the swipe state here rather than adding
 * [Modifier.metroSwipe] alongside is what stops a diagonal thumb doing both at once — see
 * [Modifier.metroDrag] for why two one-axis detectors on one element cannot arbitrate.
 */
fun Modifier.metroRiseDrag(
    state: MetroRisingPageState,
    swipe: MetroSwipeState? = null,
    enabled: Boolean = true,
    swipeEnabled: Boolean = true
): Modifier = composed {
    val horizontal = swipe?.takeIf { swipeEnabled }
    val target = remember { MetroRiseTarget() }
    onSizeChanged { horizontal?.width = it.width }
        .metroLockedDrag(
            horizontal = horizontal?.let { s ->
                MetroDragAxis(onDelta = { s.onDrag(it) }, onStop = { s.onDragStopped(it) })
            },
            vertical = metroRiseAxis(target, down = state.takeIf { enabled }, up = null)
        )
}

/** Which end of a vertical gesture is being served, held for the length of that gesture. */
internal class MetroRiseTarget {
    var state: MetroRisingPageState? = null
}

/**
 * The vertical half of a rising page's gesture, which may be **two pages deep**.
 *
 * [down] is the page the element belongs to: dragging it towards the bottom of the screen puts it
 * away, and dragging up is the same page still coming out. [up] is a page stacked *above* it — a queue
 * over a player — and it takes the gesture only when there is nothing left of [down] to open, which is
 * the whole of the rule: while the player is still on its way up, an upward drag is that rise
 * continuing, and once it covers the screen the same drag is a request for what is over it. One
 * detector, so a thumb cannot start both.
 *
 * The chosen page keeps every later frame of the gesture, whichever way the finger then goes: reversing
 * mid-drag puts the page back rather than handing the movement to its neighbour half way through.
 */
internal fun metroRiseAxis(
    target: MetroRiseTarget,
    down: MetroRisingPageState?,
    up: MetroRisingPageState?
): MetroDragAxis? {
    if (down == null && up == null) return null
    return MetroDragAxis(
        onGrab = { travel ->
            val handOver = travel < 0f && up != null && (down == null || down.progress >= 1f)
            val chosen = if (handOver) up else down
            target.state = chosen
            chosen?.grab()
            chosen != null
        },
        onDelta = { delta -> target.state?.drag(delta) },
        onStop = { velocity -> target.state?.settle(velocity) }
    )
}

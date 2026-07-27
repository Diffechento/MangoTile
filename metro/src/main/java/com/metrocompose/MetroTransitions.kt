package com.metrocompose

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset

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
 */
@Composable
fun MetroBottomBar(
    visible: Boolean,
    modifier: Modifier = Modifier,
    durationMillis: Int = MetroBarRiseMillis,
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
        // `navigationBarsPadding` consumes the inset, so a caller that pads again gets no gap twice.
        Box(
            Modifier
                .background(MetroTheme.colors.bg)
                .navigationBarsPadding()
        ) {
            content()
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

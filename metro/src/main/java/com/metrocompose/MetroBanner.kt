package com.metrocompose

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay

/** How long a banner stays put once nothing has changed. */
const val MetroBannerLingerMillis = 2200

private const val DropInMillis = 200
private const val DropOutMillis = 160

/**
 * Puts a popup along the top edge of the window and leaves it there. The popup is only as tall as
 * the banner, so everything below it belongs to the page and stays touchable.
 *
 * **The window does not move**, and that is a fix rather than a simplification. The banner used to
 * arrive by animating this offset from `-height` to zero, which means a `WindowManager
 * .updateViewLayout` per frame: a binder call whose result the compositor applies on its own
 * schedule, not the app's. Measured on the device, a 200ms drop drew **12 px of a 230 px travel** —
 * the window appeared almost where it was going and then dribbled the last few pixels over twelve
 * frames. What the eye got was a jump followed by a crawl, which is exactly what "the banner lags"
 * means. The drop is a `translationY` inside the window now, which is a transform on a layer and
 * costs nothing.
 */
private object TopEdge : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset = IntOffset.Zero
}

/**
 * The strip WP8 drops over the top of whatever is on screen to tell you something changed — volume,
 * ringer mode, a call ending. It is not a dialog: nothing is dimmed, nothing is blocked, and it
 * leaves by itself.
 *
 * Lives in a `Popup` the size of the banner itself, so it covers the status bar area and any overlay
 * the app has of its own while the rest of the screen carries on as normal: the popup is not focusable
 * and not in the way, so the keys and taps that caused the banner keep going where they were going, and
 * the page underneath stays usable for the two seconds the strip is up.
 *
 * [onHide] fires once [lingerMillis] has passed with [visible] still true and nothing having moved; drive [visible] from that. Restart the wait by changing [resetKey] — pressing volume again
 * while the banner is up should give you the full time again, not the remainder of the last wait.
 */
@Composable
fun MetroTopBanner(
    visible: Boolean,
    onHide: () -> Unit,
    resetKey: Any? = Unit,
    lingerMillis: Int = MetroBannerLingerMillis,
    content: @Composable ColumnScope.() -> Unit
) {
    val transition = remember { MutableTransitionState(false) }
    transition.targetState = visible

    LaunchedEffect(visible, resetKey) {
        if (!visible) return@LaunchedEffect
        // Answers to the system animation scale like everything else here, so "remove animations"
        // in Accessibility does not leave a banner sitting there for its full wait.
        val scale = coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
        if (scale <= 0f) return@LaunchedEffect
        delay((lingerMillis * scale).toLong())
        onHide()
    }

    // How far in it is, 0..1.
    val progress by animateFloatAsState(
        targetValue = if (transition.targetState) 1f else 0f,
        animationSpec = tween(if (transition.targetState) DropInMillis else DropOutMillis),
        label = "banner"
    )

    if (!visible && progress <= 0f) return

    Popup(
        popupPositionProvider = TopEdge,
        properties = PopupProperties(focusable = false, clippingEnabled = false)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = progress }
                // Over the status bar, not under it: the background comes first in the chain and
                // the inset after, so the strip reaches the top edge of the screen while its content
                // still starts below the clock. The phone's banner owned the whole top of the screen,
                // and a band that stops short of it leaves a stripe of whatever page is underneath
                // above the banner — which reads as the banner having been positioned wrongly rather
                // than as deference to the clock. The system's own icons draw over any app window, so
                // they stay legible on top of it.
                .background(MetroTheme.colors.bg)
                .statusBarsPadding()
                // Clipped *after* the inset, so the band the drop happens in starts under the clock.
                // This is the other half of the fix: while the whole box was sliding, the rows at the
                // bottom of it — a track title, an artist — passed through the status bar on their way
                // down and were drawn under the clock and the wifi icon for the length of the
                // animation. It read as the banner arriving in the wrong place and then correcting
                // itself. Nothing may enter that band except the strip's own colour.
                .clipToBounds()
        ) {
            Column(
                Modifier
                    // The drop, as a transform on the content rather than a move of the window.
                    // Measured from the content's own height, so the first frame is exactly one
                    // banner above its place however tall the banner turns out to be.
                    .graphicsLayer { translationY = -(1f - progress) * size.height }
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                content = content
            )
        }
    }
}

/**
 * WP8's volume banner: the caption, a speaker, the level as a number, and a bar under all of it —
 * plus room for whatever is playing.
 *
 * Android has no way to suppress its own volume panel, so this only replaces it if the app consumes
 * the volume keys before the system sees them (an activity's `onKeyDown` returning true). That means
 * it appears while the app is in front and the system's own panel is what you get from the lock
 * screen — which is the honest limit of styling something the OS owns.
 *
 * [level] is 0..1 and drives the bar; [value] is the number the phone shows beside it, usually the
 * raw stream step rather than a percentage, because that is the number the buttons actually move.
 * [media] is the now-playing row: on the phone the banner grows to carry the track and its transport
 * whenever music is playing, which is most of why nobody there ever needed to reach for a widget.
 */
@Composable
fun MetroVolumeBanner(
    visible: Boolean,
    level: Float,
    onHide: () -> Unit,
    value: String? = null,
    label: String = "volume",
    muted: Boolean = false,
    resetKey: Any? = level,
    media: (@Composable () -> Unit)? = null
) {
    val colors = MetroTheme.colors
    MetroTopBanner(visible = visible, onHide = onHide, resetKey = resetKey) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MetroLineIcon(
                icon = if (muted || level <= 0f) MetroIcon.SpeakerMuted else MetroIcon.Speaker,
                color = colors.fg,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(14.dp))
            Text(
                text = label,
                color = colors.subtle,
                fontFamily = MetroSemilight,
                fontSize = 13.sp,
                letterSpacing = 2.sp,
                modifier = Modifier.weight(1f)
            )
            if (value != null) {
                Text(
                    text = value,
                    color = colors.fg,
                    fontFamily = MetroLight,
                    fontSize = 30.sp
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        MetroProgressBar(progress = level.coerceIn(0f, 1f))
        if (media != null) {
            Spacer(Modifier.height(14.dp))
            media()
        }
    }
}

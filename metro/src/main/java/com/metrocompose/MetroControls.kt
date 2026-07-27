package com.metrocompose

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Indeterminate progress, WP8-style: five accent dots stream left→right. Each dot's
 * position is eased (smoothstep), so dots bunch up and move slowly near the edges and
 * spread out and race through the middle — the signature "inertia" look.
 */
@Composable
fun MetroProgressDots(modifier: Modifier = Modifier, color: Color = MetroTheme.colors.accent) {
    val transition = rememberInfiniteTransition(label = "dots")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)),
        label = "phase"
    )
    Canvas(modifier.fillMaxWidth().height(18.dp)) {
        val n = 5
        val r = 3.dp.toPx()
        for (i in 0 until n) {
            val local = (phase + i * 0.09f) % 1f
            // smoothstep: slow (compressed) near 0 and 1, fast (spread) through the middle
            val eased = local * local * (3f - 2f * local)
            drawCircle(color, r, Offset(eased * size.width, size.height / 2f))
        }
    }
}

/** Indeterminate progress ring: dots orbiting with a fading tail. */
@Composable
fun MetroProgressRing(modifier: Modifier = Modifier, color: Color = MetroTheme.colors.accent) {
    val transition = rememberInfiniteTransition(label = "ring")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "angle"
    )
    Canvas(modifier.size(46.dp)) {
        val n = 5
        val dot = 3.dp.toPx()
        val radius = size.minDimension / 2f - dot - 2.dp.toPx()
        val cx = size.width / 2f
        val cy = size.height / 2f
        for (i in 0 until n) {
            val a = (angle + i * (360f / n)) * (PI.toFloat() / 180f)
            val x = cx + radius * cos(a)
            val y = cy + radius * sin(a)
            val alpha = 0.25f + 0.75f * i / (n - 1)
            drawCircle(color.copy(alpha = alpha), dot, Offset(x, y))
        }
    }
}

/**
 * Determinate progress: a thin flat bar. Deliberately not interactive — for playback
 * position under a mini-player, downloads, and the like. For a draggable one use [MetroSlider].
 */
@Composable
fun MetroProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MetroTheme.colors.accent,
    trackColor: Color = MetroTheme.colors.dim,
    height: Dp = 3.dp
) {
    val frac = progress.coerceIn(0f, 1f)
    Box(modifier.fillMaxWidth().height(height).background(trackColor)) {
        Box(Modifier.fillMaxWidth(frac).height(height).background(color))
    }
}

/**
 * Flat WP8 text box: inverted fill, accent border on focus.
 *
 * [autoFocus] takes the focus — and with it the keyboard — as soon as the box appears. For a box that
 * only exists because the user just asked for it, a search field opened from a header say, making them
 * tap it as well is one tap too many.
 */
@Composable
fun MetroTextBox(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    autoFocus: Boolean = false
) {
    val colors = MetroTheme.colors
    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    if (autoFocus) {
        LaunchedEffect(focusRequester) { focusRequester.requestFocus() }
    }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = colors.bg, fontFamily = MetroRegular, fontSize = 20.sp),
        cursorBrush = SolidColor(colors.accent),
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .border(2.dp, if (focused) colors.accent else colors.subtle)
            .background(colors.fg)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) {
                    Text(placeholder, color = colors.subtle, fontFamily = MetroRegular, fontSize = 20.sp)
                }
                inner()
            }
        }
    )
}

/**
 * A [MetroTextBox] that offers the values already in use.
 *
 * For the fields where free text is necessary but agreement matters more — a genre, a tag, a folder
 * name. Typing one of those from memory is how a library ends up with "Electro", "electro" and
 * "Electronic" as three different things: nothing was wrong with any keystroke, the user simply could
 * not see what the other tracks say. Showing them turns the field into a choice that can still be
 * overridden, which is the only version that works for something the app cannot enumerate up front.
 *
 * The list is a popup below the box rather than a row of items in the layout, so offering something
 * does not push the buttons under the field down the screen, and it is deliberately *not* focusable:
 * the cursor stays in the field and the keyboard stays up, so the suggestions are an offer and not a
 * mode you have to leave. Prefix matches come first — typing "ele" wants "Electro" above "Vocal
 * electro" — and picking one puts the list away until something is typed again.
 */
@Composable
fun MetroSuggestBox(
    value: String,
    onValueChange: (String) -> Unit,
    suggestions: List<String>,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    maxSuggestions: Int = 6,
    autoFocus: Boolean = false
) {
    val colors = MetroTheme.colors
    val density = LocalDensity.current
    var boxWidth by remember { mutableStateOf(0) }
    var boxHeight by remember { mutableStateOf(0) }
    // Picked, so stop offering. Without this a value that is a prefix of another ("Rock" of "Rock &
    // Roll") keeps the list open over the field it just filled.
    var picked by remember { mutableStateOf(true) }

    val matches = remember(value, suggestions, picked, maxSuggestions) {
        if (picked) {
            emptyList()
        } else {
            val query = value.trim()
            suggestions
                .filter { !it.equals(query, ignoreCase = true) }
                .filter { query.isEmpty() || it.contains(query, ignoreCase = true) }
                .sortedBy { if (it.startsWith(query, ignoreCase = true)) 0 else 1 }
                .take(maxSuggestions)
        }
    }

    Box(modifier) {
        MetroTextBox(
            value = value,
            onValueChange = {
                picked = false
                onValueChange(it)
            },
            placeholder = placeholder,
            autoFocus = autoFocus,
            modifier = Modifier.onSizeChanged {
                boxWidth = it.width
                boxHeight = it.height
            }
        )
        if (matches.isNotEmpty()) {
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(0, boxHeight),
                properties = PopupProperties(focusable = false)
            ) {
                Column(
                    Modifier
                        .width(with(density) { boxWidth.toDp() })
                        .background(colors.bg)
                        .border(2.dp, colors.subtle)
                ) {
                    matches.forEach { suggestion ->
                        Text(
                            text = suggestion,
                            color = colors.fg,
                            fontFamily = MetroRegular,
                            fontSize = 18.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    picked = true
                                    onValueChange(suggestion)
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Minimal WP8 slider: thin track, accent fill, rectangular thumb. Tap or drag.
 *
 * [onValueChange] fires continuously while dragging; [onValueChangeFinished] fires once the
 * finger lifts. A media scrubber wants to preview on the former and seek on the latter, so it
 * isn't asking the player to seek on every pixel. [secondaryValue] draws a dimmer fill behind
 * the main one — buffered position, for instance.
 *
 * [trackHeight], [thumbSize] and [height] exist because the same control appears at two weights in
 * WP8: a chunky one for settings, and a hairline for a now-playing position bar, where the artwork
 * is supposed to dominate and the bar hugs the bottom of the cover — shrink [height] to close that
 * gap, at the cost of the touch target. A null [thumbSize] drops the thumb entirely: the fill alone
 * says where playback is, and there is no block riding along the artwork's edge. Dragging still
 * works, since the whole bar is the target.
 */
@Composable
fun MetroSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    secondaryValue: Float = 0f,
    onValueChangeFinished: (() -> Unit)? = null,
    trackHeight: Dp = 4.dp,
    thumbSize: DpSize? = DpSize(10.dp, 28.dp),
    height: Dp = 40.dp
) {
    val colors = MetroTheme.colors
    var widthPx by remember { mutableStateOf(1) }
    val frac = value.coerceIn(0f, 1f)
    val secondaryFrac = secondaryValue.coerceIn(0f, 1f)

    // Gesture lambdas are captured once by pointerInput(Unit); route them through
    // rememberUpdatedState so a recomposed callback isn't stale.
    val change by rememberUpdatedState(onValueChange)
    val finished by rememberUpdatedState(onValueChangeFinished)

    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    change((offset.x / widthPx).coerceIn(0f, 1f))
                    finished?.invoke()
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = { finished?.invoke() },
                    onDragCancel = { finished?.invoke() }
                ) { pointer, _ ->
                    change((pointer.position.x / widthPx).coerceIn(0f, 1f))
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(Modifier.fillMaxWidth().height(trackHeight).background(colors.subtle))
        if (secondaryFrac > 0f) {
            Box(Modifier.fillMaxWidth(secondaryFrac).height(trackHeight).background(colors.dim))
        }
        Box(Modifier.fillMaxWidth(frac).height(trackHeight).background(colors.accent))
        if (thumbSize != null) {
            Box(Modifier.fillMaxWidth(frac).height(height), contentAlignment = Alignment.CenterEnd) {
                Box(Modifier.size(thumbSize).background(colors.fg))
            }
        }
    }
}

/** Square WP8 check box with a label. */
@Composable
fun MetroCheckBox(
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    val colors = MetroTheme.colors
    Row(
        modifier.clickable { onChange(!checked) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(28.dp)
                .border(2.dp, if (checked) colors.accent else colors.fg)
                .background(if (checked) colors.accent else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            if (checked) Text("✓", color = colors.fg, fontFamily = MetroRegular, fontSize = 18.sp)
        }
        Spacer(Modifier.width(14.dp))
        Text(label, color = colors.fg, fontFamily = MetroRegular, fontSize = 20.sp)
    }
}

/** Round WP8 radio button with a label. */
@Composable
fun MetroRadio(
    selected: Boolean,
    onSelect: () -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    val colors = MetroTheme.colors
    Row(
        modifier.clickable { onSelect() }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(28.dp).border(2.dp, if (selected) colors.accent else colors.fg, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (selected) Box(Modifier.size(14.dp).background(colors.accent, CircleShape))
        }
        Spacer(Modifier.width(14.dp))
        Text(label, color = colors.fg, fontFamily = MetroRegular, fontSize = 20.sp)
    }
}

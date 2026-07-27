package com.metrocompose

import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Standard page frame: small uppercase overline + big light-weight title, then content.
 * Every screen uses this so headers stay identical.
 *
 * The page's surface fills the window and its *content* is what clears the navigation bar, so the
 * background reaches the bottom edge of the screen while the last row of a list does not end up
 * under the gesture pill. Nothing further down has to think about it: `navigationBarsPadding`
 * consumes the inset, so an [AppBar] or a list inside that pads again gets no second gap.
 */
@Composable
fun MetroPage(
    overline: String,
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = MetroTheme.colors
    // Inside a [MetroBackdrop] the app has already painted something behind this page, and painting
    // the theme's flat colour over it is what makes one screen a gradient and the next one black.
    val opaque = !LocalMetroHasBackdrop.current
    Column(
        modifier
            .fillMaxSize()
            .then(if (opaque) Modifier.background(colors.bg) else Modifier)
            .navigationBarsPadding()
            .padding(top = 52.dp)
    ) {
        Text(
            text = overline,
            color = colors.fg,
            fontFamily = MetroSemilight,
            fontSize = 13.sp,
            letterSpacing = 2.sp,
            modifier = Modifier.padding(start = 24.dp)
        )
        MetroPageTitle(title)
        content()
    }
}

/**
 * The gap a scrollable needs at its end so its last row clears the gesture pill — put this at the
 * bottom of a list whose *surface* runs all the way to the bottom edge of the screen.
 *
 * There are two right answers to the navigation bar and which one applies depends on whether the
 * content scrolls. Fixed content is inset, as [MetroPage] insets it: it has nowhere to go. A list is
 * inset *inside its own scroll* instead — the rows travel under the pill, where they are visible and
 * merely not tappable, and the end of the list still stops above it. Inset a list from the outside
 * and it stops dead a finger's width above the bottom of the screen with bare background under it,
 * which is what "the interface is cut off by a strip" describes: nothing explains where the content
 * went, because the thing that took the space is invisible.
 *
 * Consumption does the arithmetic. Inside a [MetroPage] the inset has already been taken and this is
 * just [extra]; inside a [MetroPanorama]'s section, where the page reaches the bottom edge, it is the
 * navigation bar plus [extra].
 */
@Composable
fun MetroBottomInset(extra: Dp = 0.dp) {
    Spacer(
        Modifier
            .fillMaxWidth()
            // Padding outside the height, so the two add up rather than the height winning.
            .navigationBarsPadding()
            .height(extra)
    )
}

/** The candidate sizes a page title is allowed to take, largest first. */
private val TitleSizes = listOf(52.sp, 46.sp, 40.sp, 34.sp, 30.sp, 26.sp)

/**
 * The big page title, at the largest of [TitleSizes] that actually fits the window.
 *
 * A fixed 52sp is right for "settings" and wrong for "Узница Совести", which simply ran off the edge
 * with its last letters cut in half. The panorama's giant title is *meant* to bleed — that is the
 * WP8 move — but a page title is a label for what you are looking at, and a label you cannot read is
 * a bug. Shrinking keeps the type as big as the name allows instead of choosing one size for the
 * shortest word in the app.
 *
 * Measured rather than guessed from character counts: Selawik is proportional, and "Ш" and "i" are
 * not the same problem. Below the smallest size it ellipsises, because at that point the name is
 * longer than any type could show.
 */
@Composable
private fun MetroPageTitle(title: String) {
    val colors = MetroTheme.colors
    val measurer = rememberTextMeasurer()

    BoxWithConstraints(Modifier.padding(start = 22.dp, end = 16.dp, top = 2.dp, bottom = 22.dp)) {
        val available = constraints.maxWidth
        val size = remember(title, available, measurer) {
            TitleSizes.firstOrNull { candidate ->
                measurer.measure(
                    text = AnnotatedString(title),
                    style = TextStyle(fontFamily = MetroLight, fontSize = candidate),
                    maxLines = 1,
                    softWrap = false
                ).size.width <= available
            }
        }
        Text(
            text = title,
            color = colors.fg,
            fontFamily = MetroLight,
            fontSize = size ?: TitleSizes.last(),
            maxLines = 1,
            softWrap = false,
            overflow = if (size == null) TextOverflow.Ellipsis else TextOverflow.Visible
        )
    }
}

/** WP8 button: bordered rectangle, transparent (or accent) fill, inverts to white on press. */
@Composable
fun MetroButton(
    text: String,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    onClick: () -> Unit
) {
    val colors = MetroTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val border = if (filled) colors.accent else colors.fg
    val bg = when {
        pressed -> colors.fg
        filled -> colors.accent
        else -> Color.Transparent
    }
    val fg = if (pressed) colors.bg else colors.fg

    Box(
        modifier
            .metroTilt(interaction)
            .border(2.dp, if (pressed) colors.fg else border)
            .background(bg)
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(horizontal = 26.dp, vertical = 11.dp)
    ) {
        Text(text, color = fg, fontFamily = MetroRegular, fontSize = 20.sp)
    }
}

/**
 * The iconic WP8 toggle switch. A single [progress] (0=off, 1=on) drives everything, so
 * the accent fill grows from the left BEHIND the thumb as it slides, and the border color
 * fades in step — nothing snaps when the thumb reaches the edge.
 */
@Composable
fun MetroToggle(
    checked: Boolean,
    modifier: Modifier = Modifier,
    onChange: (Boolean) -> Unit
) {
    val colors = MetroTheme.colors
    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
        label = "toggle"
    )

    val innerW = 52.dp // track width (56) minus the 2dp border on each side
    val thumbW = 12.dp

    Box(
        modifier
            .size(width = 56.dp, height = 26.dp)
            .border(2.dp, lerp(colors.subtle, colors.accent, progress))
            .clickable { onChange(!checked) }
            .padding(2.dp) // stay inside the border
    ) {
        // Accent fill: width tracks progress, so it fills in from the left behind the thumb.
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .width(innerW * progress)
                .background(colors.accent)
        )
        // Thumb: rides the same progress, slightly leading the fill.
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset(x = (innerW - thumbW) * progress)
                .width(thumbW)
                .height(16.dp)
                .background(colors.fg)
        )
    }
}

/**
 * A flat tile of arbitrary size: solid color, optional glyph top-left, label bottom-left.
 *
 * Glyph and label stay white regardless of theme — a tile is a colored surface, and WP8
 * always drew tile content in white on top of it.
 */
@Composable
fun Tile(
    label: String,
    color: Color,
    w: Dp,
    h: Dp,
    glyph: String? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val interaction = remember { MutableInteractionSource() }
    // Scale glyph, label and padding to the tile's shorter side so the icon never
    // collides with the label on small tiles.
    val side = minOf(w, h).value
    val glyphSize = (side * 0.34f).coerceIn(18f, 52f).sp
    val labelSize = (side * 0.13f).coerceIn(12f, 17f).sp
    val pad = (side * 0.10f).coerceIn(6f, 12f).dp

    Box(
        modifier
            .size(w, h)
            .metroTilt(interaction)
            .background(color)
            .clickable(interactionSource = interaction, indication = null) { onClick() }
    ) {
        if (glyph != null) {
            Text(
                text = glyph,
                color = Metro.Fg,
                fontFamily = MetroLight,
                fontSize = glyphSize,
                maxLines = 1,
                modifier = Modifier.align(Alignment.TopStart).padding(pad)
            )
        }
        Text(
            text = label,
            color = Metro.Fg,
            fontFamily = MetroRegular,
            fontSize = labelSize,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(pad)
        )
    }
}

/**
 * LongListSelector-style row: primary line + optional gray secondary line.
 *
 * Pass [onLongClick] to hang a [MetroContextMenu] off the row — holding a list item is how
 * WP8 exposed per-item actions.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ListRow(
    primary: String,
    secondary: String? = null,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit = {}
) {
    val colors = MetroTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .combinedClickable(onLongClick = onLongClick, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        Text(primary, color = colors.fg, fontFamily = MetroRegular, fontSize = 25.sp)
        if (secondary != null) {
            Text(secondary, color = colors.subtle, fontFamily = MetroRegular, fontSize = 15.sp)
        }
    }
}

/** Settings row: title + On/Off state on the left, toggle on the right. */
@Composable
fun SettingRow(
    title: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    onChange: (Boolean) -> Unit
) {
    val colors = MetroTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = colors.fg, fontFamily = MetroRegular, fontSize = 22.sp)
            Text(
                text = if (checked) "On" else "Off",
                color = colors.subtle,
                fontFamily = MetroRegular,
                fontSize = 14.sp
            )
        }
        MetroToggle(checked, onChange = onChange)
    }
}

/**
 * Pivot header row: selected title bright, others dim; tap to switch. Scrolls sideways.
 *
 * Headers only — it does not host content. For the full swipe-paged control see [MetroPivot].
 */
@Composable
fun Pivot(
    titles: List<String>,
    selected: Int,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit
) {
    val colors = MetroTheme.colors
    Row(
        modifier
            .horizontalScroll(rememberScrollState())
            .padding(start = 24.dp)
    ) {
        titles.forEachIndexed { i, t ->
            Text(
                text = t,
                color = if (i == selected) colors.fg else colors.dim,
                fontFamily = MetroLight,
                fontSize = 38.sp,
                modifier = Modifier
                    .padding(end = 22.dp)
                    .clickable { onSelect(i) }
            )
        }
    }
}

/** A single circular app-bar button with a glyph and small caption. */
@Composable
fun AppBarButton(
    glyph: String,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val colors = MetroTheme.colors
    val tint = if (enabled) colors.fg else colors.dim
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(48.dp)
                .border(2.dp, tint, CircleShape)
                .clickable(enabled = enabled) { onClick() },
            contentAlignment = Alignment.Center
        ) {
            CenteredGlyph(glyph, tint, 20.sp, Modifier.matchParentSize())
        }
        Spacer(Modifier.height(5.dp))
        Text(label, color = colors.subtle, fontFamily = MetroRegular, fontSize = 11.sp)
    }
}

/**
 * Draws one glyph centred on its **ink**, not on its layout box.
 *
 * A `Text` inside a centred `Box` lines up the advance width and the line height, which is not
 * the same thing as lining up what you see. The transport symbols aren't in Selawik, so each
 * arrives from a different fallback font with its own side bearings and cap height — centre
 * them as text and ⏭ sits visibly left of the circle while everything drifts low.
 *
 * `Paint.getTextBounds` reports the tight ink rectangle relative to the pen position, so
 * offsetting by its centre puts the visible mark exactly in the middle whatever font ends up
 * supplying the glyph.
 */
@Composable
fun CenteredGlyph(
    glyph: String,
    color: Color,
    fontSize: TextUnit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val fontSizePx = with(LocalDensity.current) { fontSize.toPx() }
    val argb = color.toArgb()

    val paint = remember(context, fontSizePx, argb) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            // Resources.getFont is API 26, which is this library's minSdk. Symbols missing from
            // Selawik fall back through Paint the same way they would in a Text.
            typeface = runCatching { context.resources.getFont(R.font.selawik_regular) }.getOrNull()
            textSize = fontSizePx
            this.color = argb
        }
    }
    val ink = remember(paint, glyph) {
        Rect().also { paint.getTextBounds(glyph, 0, glyph.length, it) }
    }

    Canvas(modifier) {
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawText(
                glyph,
                size.width / 2f - ink.exactCenterX(),
                size.height / 2f - ink.exactCenterY(),
                paint
            )
        }
    }
}

/**
 * A transport control: a glyph with no caption, optionally inside a ring.
 *
 * Both weights are on the WP8 now-playing screen at once. Play and skip get a ring — pass
 * [ringSize] — because they are the buttons you aim at without looking; the toggles beside the
 * artwork are bare marks, so the artwork stays the loudest thing on the screen. Neither carries a
 * caption: that is the [AppBarButton] idiom, which the player pointedly does not use.
 *
 * [active] is for toggles that are currently on (shuffle, repeat, favourite): WP8 tints those
 * with the accent rather than swapping the glyph.
 *
 * [contentDescription] is not optional in spirit: with the caption gone, it is the only thing a
 * screen reader has to go on.
 */
@Composable
fun TransportButton(
    glyph: String,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    glyphSize: TextUnit = 26.sp,
    touchSize: Dp = 56.dp,
    enabled: Boolean = true,
    active: Boolean = false,
    ringSize: Dp? = null,
    onClick: () -> Unit
) {
    val colors = MetroTheme.colors
    val tint = when {
        !enabled -> colors.dim
        active -> colors.accent
        else -> colors.fg
    }
    Box(
        modifier
            .size(touchSize)
            .clickable(enabled = enabled, onClickLabel = contentDescription) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (ringSize != null) {
            Box(
                Modifier.size(ringSize).border(2.dp, tint, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                CenteredGlyph(glyph, tint, glyphSize, Modifier.matchParentSize())
            }
        } else {
            CenteredGlyph(glyph, tint, glyphSize, Modifier.matchParentSize())
        }
    }
}

/**
 * [TransportButton] carrying a drawn [MetroIcon] instead of a typed glyph — the right choice for
 * the toggles, whose symbols no font renders the way WP8 draws them.
 */
@Composable
fun TransportButton(
    icon: MetroIcon,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    iconSize: Dp = 22.dp,
    touchSize: Dp = 56.dp,
    enabled: Boolean = true,
    active: Boolean = false,
    ringSize: Dp? = null,
    onClick: () -> Unit
) {
    val colors = MetroTheme.colors
    val tint = when {
        !enabled -> colors.dim
        active -> colors.accent
        else -> colors.fg
    }
    Box(
        modifier
            .size(touchSize)
            .clickable(enabled = enabled, onClickLabel = contentDescription) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (ringSize != null) {
            Box(
                Modifier.size(ringSize).border(2.dp, tint, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                MetroLineIcon(icon, tint, Modifier.size(iconSize))
            }
        } else {
            MetroLineIcon(icon, tint, Modifier.size(iconSize))
        }
    }
}

/**
 * Bottom app bar: a row of circular buttons, WP8-style.
 *
 * Also the right frame for a row of ringed [TransportButton]s — same even spread across the
 * width, just without the captions.
 */
@Composable
fun AppBar(modifier: Modifier = Modifier, buttons: @Composable () -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            // Clears the gesture bar when the app bar is the last thing on a full-bleed screen, and
            // costs nothing inside a [MetroPage], which has already consumed the inset.
            .navigationBarsPadding()
            .padding(horizontal = 40.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        buttons()
    }
}

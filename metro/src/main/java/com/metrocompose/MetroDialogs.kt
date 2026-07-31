package com.metrocompose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/** Scrim opacity shared by every modal surface here. */
private const val ScrimAlpha = 0.72f

/**
 * Context menu timings and metrics, measured off a Lumia frame by frame.
 *
 * The strip reaches the item's edges in about four frames, and only then does the sheet start to
 * unroll, taking about seven more — most of that travel in the first four with a soft landing
 * after, so a decelerating curve rather than a spring. Folding back is quicker than opening,
 * which is the usual WP8 asymmetry.
 *
 * Held a third above those raw frame counts, which is where it was judged to read right on a
 * modern screen: the phone's own 60 Hz panel and its motion blur do some of the softening for it.
 */
private const val StripSpreadMillis = 170
private const val StripRetractMillis = 120
private const val MenuUnfoldMillis = 275
private const val MenuFoldMillis = 180
private val StripThickness = 3.dp
private val ItemHeight = 54.dp
private val SheetPadding = 8.dp

/**
 * How solid a context menu's sheet is. Not quite: the page stays faintly legible through it, which is
 * what says the menu was laid over where you were rather than having replaced it. The phone's own sheet
 * is flat and opaque — pass `sheetAlpha = 1f` for that.
 */
private const val MenuSheetAlpha = 0.9f

/**
 * Pins a popup's content to the window's top-left rather than to its anchor, so the content can
 * cover the screen and place its own children.
 *
 * Where those children go then has to be worked out in screen coordinates: the popup is a separate
 * window, so `positionInRoot`/`positionInWindow` inside it measure a different space than the same
 * calls in the page behind it, and mixing the two put the menu tens of dp away from the row it
 * belonged to. `positionOnScreen()` is the one space both windows agree on.
 */
private object WindowOriginPosition : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset = IntOffset.Zero
}

/**
 * WP8 context menu: hold an item, everything else dims, and a flat list of actions unfolds
 * next to it.
 *
 * [content] is the anchor and is always drawn; the menu is an overlay on top. Position the
 * long-press yourself — usually `Modifier.combinedClickable(onLongClick = { expanded = true })`
 * on whatever is inside [content].
 *
 *   var open by remember { mutableStateOf(false) }
 *   MetroContextMenu(open, listOf("play", "add to playlist", "delete"),
 *       onSelect = { i -> open = false; act(i) }, onDismiss = { open = false }) {
 *       ListRow(track.title, track.artist, modifier = Modifier.combinedClickable(
 *           onLongClick = { open = true }, onClick = { play() }))
 *   }
 *
 * Actions in [disabledItems] are greyed and inert. WP8 shows an action that does not apply rather
 * than hiding it — the menu keeps the same shape every time you open it, so you learn where each
 * entry is, and a greyed one tells you *why* nothing happens where hiding it would leave you
 * wondering whether you remembered the menu wrong.
 *
 * [selectedItem] draws one entry in accent, for a menu that is choosing between states rather than
 * offering actions — the arrangements of a [MetroLongList] are held this way. A menu of four ways to
 * sort that says nothing about which one you are looking at makes the user pick one to find out.
 *
 * The sheet is as wide as its widest label, and never narrower than the anchor. A row therefore gets
 * the full-width sheet it always had, while something small — a group header's letter tile — gets a
 * menu you can read instead of a 44dp column of wrapped words.
 *
 * [sheetAlpha] leaves the page faintly readable through the sheet, so the menu is something laid over
 * where you were rather than a new screen that replaced it. Pass 1f for the phone's own flat opaque
 * sheet. The labels stay fully opaque either way — the paper is translucent, the ink is not.
 */
@Composable
fun MetroContextMenu(
    expanded: Boolean,
    items: List<String>,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    disabledItems: Set<Int> = emptySet(),
    selectedItem: Int? = null,
    sheetAlpha: Float = MenuSheetAlpha,
    content: @Composable () -> Unit
) {
    val colors = MetroTheme.colors
    val density = LocalDensity.current

    var anchorOnScreen by remember { mutableStateOf(Offset.Zero) }
    var anchorSizePx by remember { mutableStateOf(IntSize.Zero) }
    var popupOnScreen by remember { mutableStateOf(Offset.Zero) }

    // Where the finger went down inside the anchor. The strip grows out from exactly there, so
    // the menu looks like it was pulled out of the spot that was touched.
    var pressXPx by remember { mutableStateOf<Float?>(null) }

    // The anchor wraps the content, so [modifier] belongs to it — a row in a list still needs
    // to be able to take a weight or a size from its parent.
    Box(
        modifier
            .onGloballyPositioned {
                val onScreen = it.positionOnScreen()
                if (onScreen.isSpecified) anchorOnScreen = onScreen
                anchorSizePx = it.size
            }
            // Observes only: the down event is never consumed, so the row's own click and
            // long-click still see it.
            .pointerInput(Unit) {
                awaitEachGesture {
                    pressXPx = awaitFirstDown(requireUnconsumed = false).position.x
                }
            }
    ) {
        content()
    }

    // Two phases, in order: the strip spreads from the touch point to the item's edges, and only
    // once it has reached them does the sheet unroll out of it. Reversed on the way out.
    val spread = remember { Animatable(0f) }
    val unfold = remember { Animatable(0f) }

    LaunchedEffect(expanded) {
        if (expanded) {
            spread.snapTo(0f)
            unfold.snapTo(0f)
            spread.animateTo(1f, tween(StripSpreadMillis, easing = LinearOutSlowInEasing))
            unfold.animateTo(1f, tween(MenuUnfoldMillis, easing = LinearOutSlowInEasing))
        } else {
            unfold.animateTo(0f, tween(MenuFoldMillis, easing = FastOutLinearInEasing))
            spread.animateTo(0f, tween(StripRetractMillis, easing = FastOutLinearInEasing))
        }
    }

    val scrim by animateFloatAsState(
        targetValue = if (expanded) ScrimAlpha else 0f,
        animationSpec = tween(StripSpreadMillis + MenuUnfoldMillis),
        label = "scrim"
    )

    // Keep the popup mounted until the strip has retracted, or it would vanish mid-animation.
    if (expanded || spread.value > 0f) {
        Popup(
            popupPositionProvider = WindowOriginPosition,
            properties = PopupProperties(focusable = true),
            onDismissRequest = onDismiss
        ) {
            BoxWithConstraints(
                Modifier.fillMaxSize().onGloballyPositioned {
                    val onScreen = it.positionOnScreen()
                    if (onScreen.isSpecified) popupOnScreen = onScreen
                }
            ) {
                // Anchor position translated into this popup's own space.
                val anchorTop = with(density) { (anchorOnScreen.y - popupOnScreen.y).toDp() }
                val anchorLeft = with(density) { (anchorOnScreen.x - popupOnScreen.x).toDp() }
                val anchorWidth = with(density) { anchorSizePx.width.toDp() }
                val anchorHeight = with(density) { anchorSizePx.height.toDp() }
                val anchorBottom = anchorTop + anchorHeight
                val sheetHeight = ItemHeight * items.size + SheetPadding * 2

                // Below the item by default; above it when there isn't room below.
                val downward = anchorBottom + sheetHeight <= maxHeight
                val sheetTop = if (downward) anchorBottom else (anchorTop - sheetHeight)
                // The strip sits on the item, along the edge the sheet will come out of.
                val stripTop = if (downward) {
                    anchorBottom - StripThickness
                } else {
                    anchorTop
                }

                val pressX = with(density) { (pressXPx ?: (anchorSizePx.width / 2f)).toDp() }
                val origin = anchorLeft + pressX
                val stripLeft = origin + (anchorLeft - origin) * spread.value
                val stripRight = origin + (anchorLeft + anchorWidth - origin) * spread.value

                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = scrim))
                        .clickable(indication = null, interactionSource = null) { onDismiss() }
                )

                // The held item lights up first — it is the subject of the menu, and it stays
                // readable under the strip while the sheet is still on its way.
                Box(
                    Modifier
                        .offset(x = anchorLeft, y = anchorTop)
                        .size(anchorWidth, anchorHeight)
                        .background(colors.fg.copy(alpha = 0.10f * spread.value))
                )

                // The sheet is the inverse of the page — a light card on a dark theme — and it
                // squashes with the unfold, labels included, so it reads as one flat thing being
                // unrolled rather than a box that faded in.
                if (unfold.value > 0f) {
                    Column(
                        Modifier
                            .offset(x = anchorLeft, y = sheetTop)
                            // The widest label decides the width, and the invisible spacer below puts
                            // the anchor's own width into that maximum — so a full-width row keeps the
                            // full-width sheet it has always had, and a letter tile gets a sheet wide
                            // enough to read rather than one 44dp column of broken words.
                            .width(IntrinsicSize.Max)
                            .graphicsLayer {
                                transformOrigin =
                                    TransformOrigin(0.5f, if (downward) 0f else 1f)
                                scaleY = unfold.value
                            }
                            .background(colors.fg.copy(alpha = sheetAlpha))
                            .padding(vertical = SheetPadding)
                    ) {
                        Spacer(Modifier.width(anchorWidth).height(0.dp))
                        items.forEachIndexed { i, label ->
                            val usable = i !in disabledItems
                            Text(
                                text = label,
                                color = when {
                                    !usable -> colors.bg.copy(alpha = 0.35f)
                                    i == selectedItem -> colors.accent
                                    else -> colors.bg
                                },
                                fontFamily = MetroRegular,
                                fontSize = 22.sp,
                                maxLines = 1,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = usable) { onSelect(i) }
                                    .padding(horizontal = 20.dp, vertical = 13.dp)
                            )
                        }
                    }
                }

                // Drawn last so it stays visible along the sheet's leading edge.
                Box(
                    Modifier
                        .offset(x = stripLeft, y = stripTop)
                        .size((stripRight - stripLeft).coerceAtLeast(0.dp), StripThickness)
                        .background(colors.fg)
                )
            }
        }
    }
}

/**
 * WP8 message box: a panel that drops in from the top over a dimmed screen, with a bold title,
 * a body, and one or two flat buttons. Pass null for [dismiss] to get a single-button alert.
 */
@Composable
fun MetroMessageBox(
    visible: Boolean,
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirm: String = "ok",
    dismiss: String? = "cancel"
) {
    MetroModalPanel(visible = visible, onDismiss = onDismiss, title = title) {
        Text(
            text = message,
            color = MetroTheme.colors.fg,
            fontFamily = MetroRegular,
            fontSize = 19.sp,
            modifier = Modifier.padding(bottom = 26.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetroButton(confirm, onClick = onConfirm)
            if (dismiss != null) MetroButton(dismiss, onClick = onDismiss)
        }
    }
}

/**
 * The message box's text-entry sibling — "create playlist", "rename", and so on. [onConfirm]
 * receives the edited text; it is not called when the field is blank.
 */
@Composable
fun MetroInputBox(
    visible: Boolean,
    title: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    initial: String = "",
    placeholder: String = "",
    confirm: String = "ok",
    dismiss: String = "cancel"
) {
    // Reset the draft each time the box is opened, so a cancelled edit doesn't linger.
    var text by remember(visible, initial) { mutableStateOf(initial) }

    MetroModalPanel(visible = visible, onDismiss = onDismiss, title = title) {
        MetroTextBox(text, { text = it }, placeholder = placeholder)
        Spacer(Modifier.height(26.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetroButton(confirm) { if (text.isNotBlank()) onConfirm(text.trim()) }
            MetroButton(dismiss, onClick = onDismiss)
        }
    }
}

/**
 * WP8's list picker: a modal panel offering one choice out of many. The list scrolls when it
 * has to, so it copes with "add to one of forty playlists" as well as with three options.
 */
@Composable
fun MetroListBox(
    visible: Boolean,
    title: String,
    items: List<String>,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    emptyText: String = "nothing here yet"
) {
    val colors = MetroTheme.colors
    MetroModalPanel(visible = visible, onDismiss = onDismiss, title = title) {
        if (items.isEmpty()) {
            Text(
                text = emptyText,
                color = colors.dim,
                fontFamily = MetroRegular,
                fontSize = 18.sp,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                itemsIndexed(items) { index, label ->
                    Text(
                        text = label,
                        color = colors.fg,
                        fontFamily = MetroRegular,
                        fontSize = 22.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(index) }
                            .padding(vertical = 12.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        MetroButton("cancel", onClick = onDismiss)
    }
}

/** Shared chrome for [MetroMessageBox] and [MetroInputBox]: scrim, drop-in panel, title. */
@Composable
private fun MetroModalPanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    title: String,
    body: @Composable ColumnScope.() -> Unit
) {
    val colors = MetroTheme.colors
    val transition = remember { MutableTransitionState(false) }
    transition.targetState = visible

    if (transition.currentState || transition.targetState) {
        Popup(properties = PopupProperties(focusable = true), onDismissRequest = onDismiss) {
            Box(Modifier.fillMaxSize()) {
                AnimatedVisibility(
                    visibleState = transition,
                    enter = fadeIn(tween(120)),
                    exit = fadeOut(tween(120))
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = ScrimAlpha))
                            .clickable(indication = null, interactionSource = null) { onDismiss() }
                    )
                }
                AnimatedVisibility(
                    visibleState = transition,
                    enter = slideInVertically(tween(200)) { -it } + fadeIn(tween(200)),
                    exit = slideOutVertically(tween(160)) { -it } + fadeOut(tween(160)),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(colors.bg)
                            .padding(start = 24.dp, end = 24.dp, top = 40.dp, bottom = 26.dp)
                    ) {
                        Text(
                            text = title,
                            color = colors.fg,
                            fontFamily = MetroSemilight,
                            fontSize = 28.sp,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        body()
                    }
                }
            }
        }
    }
}

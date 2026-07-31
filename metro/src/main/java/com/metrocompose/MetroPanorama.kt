package com.metrocompose

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** How much wider than the viewport the background layer is drawn. */
private const val BackgroundScale = 1.4f

/** Pan cap, as a fraction of the layer's width — must stay under the scale's overhang. */
private const val BackgroundMaxShift = 0.18f

/**
 * Above this fling speed (px/s) the panorama commits to the next section instead of settling on
 * the nearest one. Low on purpose: a short, deliberate flick should move on even though the finger
 * barely travelled, which is how the phone behaves.
 */
private const val CommitVelocity = 220f

/** How long the settle takes once the finger is off. */
private const val SettleMillis = 420

/** How dim the header of a section you are not on is drawn. */
private const val AwayHeaderAlpha = 0.42f

/** Smallest gap between two copies of a wrapping panorama's title. */
private val TitleRepeatGap = 64.dp

/**
 * How the space above and below the giant title is spent, and why it is this little.
 *
 * The title clears the status bar by *inset* rather than by a hand-picked number, so a phone with a
 * tall bar or a cutout does not overlap it and one with a short bar does not pay for the worst case;
 * [PanoramaTitleTop] is only the breathing room under that. Both are deliberately small: a panorama's
 * height is spent on the section under the title, and the phone's own panorama sets the title close
 * under the clock with the section header tucked in beneath it.
 *
 * The text itself is measured with `includeFontPadding = false` — Android's extra line padding around
 * a 108sp line is tens of dp of nothing that reads as a layout decision nobody made.
 */
private val PanoramaTitleTop = 6.dp
private val PanoramaTitleGap = 6.dp
private val PanoramaHeaderGap = 12.dp

/** No leading of Android's own around the big text; the gaps above are the whole spacing. */
private val TightText = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))

/** The giant title's size, and its own tight style. */
private val PanoramaTitleSize = 108.sp
private val TightTitle = TextStyle(
    fontSize = PanoramaTitleSize,
    platformStyle = PlatformTextStyle(includeFontPadding = false)
)

/**
 * The blank the *font* reserves above the capitals and below the baseline of a 108sp line, measured
 * off the screen: 30dp over the letters and 16dp under them, out of a 131dp line box holding 77dp of
 * ink. It is trimmed by [trimVertically] rather than by a line height, because a line height cannot
 * take it away — `lineHeight` shrinks the *leading* between lines, and this is the ascent and descent
 * themselves. Asking for less than them simply gets the ascent and descent back, which is a whole
 * round spent proving.
 *
 * Left as generous numbers rather than exact ones: they are Selawik's at this size, and a hair of
 * slack costs a few pixels where a hair too far clips a descender.
 */
private val TitleInkTop = 30.dp
private val TitleInkBottom = 16.dp

/**
 * Shortens a node's *layout* by [top] and [bottom] and lifts its content into the space, so the thing
 * above it and the thing below it both close in on what is actually drawn.
 *
 * Nothing is clipped: the content is placed at a negative offset, which draws outside the node's
 * bounds, and neither the panorama's column nor the title's own box clips.
 */
private fun Modifier.trimVertically(top: Dp, bottom: Dp) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val trimmed = (placeable.height - (top + bottom).roundToPx()).coerceAtLeast(0)
    layout(placeable.width, trimmed) {
        placeable.place(0, -top.roundToPx())
    }
}

/** The title block, which either gives its height up to the sections or does not. */
@Composable
private fun CollapsingTitle(
    collapsing: Boolean,
    collapse: MetroCollapse,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = if (collapsing) Modifier.metroCollapsingHeader(collapse, overhang = TitleInkBottom) else Modifier,
        content = content
    )
}

/**
 * Where each section begins, as a scroll position. Sections put themselves in as they are placed
 * and take themselves out when they leave, so the set always describes what is actually on screen.
 */
internal class PanoramaStops {

    private val offsets = mutableStateMapOf<Any, Float>()

    fun put(id: Any, offset: Float) {
        offsets[id] = offset
    }

    fun remove(id: Any) {
        offsets.remove(id)
    }

    /** Sorted and rebased, so the first section's stop is scroll position 0. */
    fun scrollStops(): List<Float> {
        if (offsets.isEmpty()) return emptyList()
        val sorted = offsets.values.sorted()
        val first = sorted.first()
        return sorted.map { it - first }
    }
}

/** Set by [MetroPanorama] while it wants its sections to register; null when snapping is off. */
internal val LocalPanoramaStops = staticCompositionLocalOf<PanoramaStops?> { null }

/**
 * Settles the panorama on a section boundary rather than wherever the finger let go — the WP8
 * panorama never rests halfway between two headers.
 */
private class SnapToSections(
    private val stops: () -> List<Float>,
    private val scroll: ScrollState
) : FlingBehavior {

    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        val landings = stops()
        if (landings.size < 2) return initialVelocity

        val from = scroll.value.toFloat()
        // A flick commits to the neighbour it was thrown at however short it was; a slow drag
        // settles on whichever section is nearest. Without the first rule a gentle but deliberate
        // swipe springs back, which reads as the phone having ignored it.
        val target = when {
            initialVelocity > CommitVelocity ->
                landings.firstOrNull { it > from + 1f } ?: landings.last()
            initialVelocity < -CommitVelocity ->
                landings.lastOrNull { it < from - 1f } ?: landings.first()
            else -> landings.minByOrNull { abs(it - from) } ?: from
        }.coerceIn(0f, scroll.maxValue.toFloat())

        var placed = from
        animate(
            initialValue = from,
            targetValue = target,
            initialVelocity = initialVelocity,
            animationSpec = tween(SettleMillis, easing = LinearOutSlowInEasing)
        ) { value, _ ->
            placed += scrollBy(value - placed)
        }
        return 0f
    }
}

/**
 * WP8-style panorama with real multi-layer parallax.
 *
 * Three layers share one horizontal scroll but move at different speeds:
 *   - background  -> slowest ([backgroundParallax])
 *   - big title   -> medium  ([titleParallax])
 *   - sections    -> full speed (the foreground the finger actually drags)
 *
 * Sections are composed eagerly (a plain `horizontalScroll`, not a lazy row) — a panorama is
 * a handful of sections by design, and the parallax needs an absolute scroll offset that a
 * lazy row can't give. Put a `LazyColumn` INSIDE a section if its content is long. Note that
 * children of the scrolling row are measured with an infinite max width, so give such a list
 * an explicit `Modifier.width(...)`; `fillMaxWidth()` has nothing to fill.
 *
 * With [snap] on (the default) letting go settles the panorama on a section boundary, so it can't
 * come to rest halfway between two headers. Turn it off for a panorama whose sections are wider
 * than the viewport, where the middle of a section would otherwise be unreachable.
 */
@Composable
fun MetroPanorama(
    title: String,
    modifier: Modifier = Modifier,
    overline: String? = null,
    titleParallax: Float = 0.5f,
    backgroundParallax: Float = 0.35f,
    background: (@Composable BoxScope.() -> Unit)? = null,
    snap: Boolean = true,
    sectionPadding: Dp = 26.dp,
    titleTopPadding: Dp = PanoramaTitleTop,
    titleGap: Dp = PanoramaTitleGap,
    collapsingTitle: Boolean = true,
    sections: @Composable RowScope.() -> Unit
) {
    val colors = MetroTheme.colors
    val scroll = rememberScrollState()
    val stops = remember { PanoramaStops() }
    val fling = remember(scroll) { SnapToSections({ stops.scrollStops() }, scroll) }
    val collapse = rememberMetroCollapse()

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .then(if (collapsingTitle) Modifier.metroCollapseOnScroll(collapse) else Modifier)
            .then(if (LocalMetroHasBackdrop.current) Modifier else Modifier.background(colors.bg))
    ) {
        // Layer 1 — background, slowest.
        // Fill the viewport EXACTLY (matchParentSize — reliable, unlike maxWidth here), then
        // widen it via scaleX and pan by a clamped amount that's a fraction of the layer's
        // own size. Because the pan is capped below the scale's overhang, the viewport is always
        // covered — no black — while still parallaxing with scroll.
        if (background != null) {
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        scaleX = BackgroundScale
                        val maxShift = BackgroundMaxShift * size.width
                        translationX = (-backgroundParallax * scroll.value).coerceIn(-maxShift, maxShift)
                    },
                content = background
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                // The backdrop behind this fills the window; the content is what clears the
                // navigation bar, so the parallax runs to the bottom edge of the screen and the
                // last row of a section still stops above the gesture pill.
                .navigationBarsPadding()
                .statusBarsPadding()
                .clipToBounds()
                .padding(top = titleTopPadding, bottom = 32.dp)
        ) {
            // The name of the place, which gives up its height to the section once you start reading
            // one — see [MetroCollapse]. Overline and title go together: a lone overline left above
            // the sections would be a label with nothing under it.
            Column(if (collapsingTitle) Modifier.metroCollapsingHeader(collapse, overhang = TitleInkBottom) else Modifier) {
                if (overline != null) {
                    Text(
                        text = overline,
                        color = colors.fg,
                        fontFamily = MetroSemilight,
                        fontSize = 14.sp,
                        letterSpacing = 2.sp,
                        style = TightText,
                        modifier = Modifier.padding(start = 26.dp, bottom = 2.dp)
                    )
                }

                // Layer 2 — the giant title, medium speed, allowed to bleed off the edge.
                Text(
                    text = title,
                    color = colors.fg,
                    fontFamily = MetroLight,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Visible,
                    style = TightTitle,
                    modifier = Modifier
                        .trimVertically(TitleInkTop, TitleInkBottom)
                        .padding(start = 22.dp)
                        .graphicsLayer { translationX = -scroll.value * titleParallax }
                )
            }

            Spacer(Modifier.height(titleGap))

            // Layer 3 — sections, the full-speed foreground that drives the scroll.
            //
            // Two rows, not one: sections report where they start by asking for their offset
            // inside their parent, and the parent has to be a layout that carries no scroll
            // modifier of its own or that offset comes back with the scroll already subtracted.
            CompositionLocalProvider(LocalPanoramaStops provides stops.takeIf { snap }) {
                Row(
                    Modifier
                        .horizontalScroll(scroll, flingBehavior = fling.takeIf { snap })
                        .padding(start = sectionPadding)
                ) {
                    Row { sections() }
                }
            }
        }
    }
}

/**
 * One section of the panorama, as data.
 *
 * The list-taking [MetroPanorama] needs its sections as values rather than as composed children: it
 * only composes the two or three that are near the viewport, it places them itself so it can loop
 * around, and an app that lets the user reorder its sections just reorders this list.
 *
 * [key] identifies the section across reorderings — give it something stable if [title] is not.
 * [headerPadding] indents the header alone, for content that brings its own gutter.
 */
class MetroPanoramaSection(
    val title: String,
    val key: Any = title,
    val headerPadding: Dp = 0.dp,
    /**
     * Makes the header itself a control. Null leaves it as a label.
     *
     * The header is the obvious place to hang "search this section" from: it is the one thing on the
     * section that names what the list contains, it is already the widest target on screen, and a
     * separate search button would be a second element competing with it.
     */
    val onHeaderClick: (() -> Unit)? = null,
    val content: @Composable ColumnScope.() -> Unit
)

/**
 * The WP8 panorama proper: circular, lazy, and showing you what comes next.
 *
 * Differences from the [sections]-as-a-lambda overload, all of them things the real one does:
 *
 *  - **It wraps.** Past the last section comes the first again, in both directions, so nothing is
 *    ever more than one swipe away — which is what makes it reasonable to put a screen the user
 *    reaches often, like settings, at the far end.
 *  - **It only composes what is near.** A section three away is not in the composition at all, so a
 *    panorama can carry a dozen lists without paying for them at launch.
 *  - **The next header leans in, and only the header.** Headers are their own layer, [sectionPeek]
 *    closer together than the pages are wide, so the beginning of the next section's name always
 *    shows past the current one — dimmer the further out it is — while its *content* is still
 *    entirely off screen. Attaching the header to its page instead is the obvious implementation and
 *    the wrong one: the header can then only lean in by dragging a sliver of the next section's list
 *    on screen with it.
 *
 * Sections share one width, which is what lets the wrap arithmetic be exact; a section that needs a
 * different one wants the other overload. Keep the *number* of them stable while the panorama is on
 * screen: with wrapping, a page number means a section modulo the count, so inserting one under the
 * user's feet renames every page and the panorama appears to jump to somewhere else. Decide whether
 * a section exists on something that cannot change mid-session — an API level, a setting — rather
 * than on whether its data has finished loading. Content is laid out in a [ColumnScope] whose width is the
 * section's, so `fillMaxWidth()` means something here and a list can take `weight(1f)` for height.
 *
 * The looping is seamless because both parallax layers are made periodic over one cycle: the
 * backdrop is drawn twice and its speed nudged so a full cycle moves it a whole number of windows,
 * and the title is repeated at a spacing that divides its own travel. The backdrop lambda is
 * therefore composed once per copy — keep it cheap, or hand it a bitmap you already hold.
 */
@Composable
fun MetroPanorama(
    title: String,
    sections: List<MetroPanoramaSection>,
    modifier: Modifier = Modifier,
    overline: String? = null,
    titleParallax: Float = 0.5f,
    backgroundParallax: Float = 0.35f,
    background: (@Composable BoxScope.() -> Unit)? = null,
    wrap: Boolean = true,
    initialSection: Int = 0,
    sectionPadding: Dp = 26.dp,
    sectionPeek: Dp = 96.dp,
    headerFontSize: TextUnit = 48.sp,
    titleTopPadding: Dp = PanoramaTitleTop,
    titleGap: Dp = PanoramaTitleGap,
    headerGap: Dp = PanoramaHeaderGap,
    collapsingTitle: Boolean = true,
    /**
     * Whether the backdrop may be drawn twice to make the loop seamless. True for a drawing that
     * ends where it begins — a gradient, a pattern — which is what lets it scroll for ever. Pass
     * false for a photograph or an album cover: two copies of one of those meet in a hard line, and
     * a single wider copy panned inside its own overhang is what it wants instead.
     */
    backgroundTiles: Boolean = true
) {
    val colors = MetroTheme.colors
    val count = sections.size
    val wrapping = wrap && count > 1

    // With wrapping there is no first or last page: start in the middle of a huge range, on the
    // section the caller asked for, and read the page number modulo the section count.
    val pager = rememberPagerState(
        initialPage = if (wrapping) {
            val middle = Int.MAX_VALUE / 2
            middle - middle.mod(count) + initialSection.coerceIn(0, count - 1)
        } else {
            initialSection.coerceIn(0, (count - 1).coerceAtLeast(0))
        }
    ) {
        if (wrapping) Int.MAX_VALUE else count
    }

    val collapse = rememberMetroCollapse()

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .then(if (collapsingTitle) Modifier.metroCollapseOnScroll(collapse) else Modifier)
            .then(if (LocalMetroHasBackdrop.current) Modifier else Modifier.background(colors.bg))
    ) {
        if (count == 0) return@BoxWithConstraints

        val density = LocalDensity.current
        // A section's content is as wide as the window less the gutter, so nothing of the next
        // section's *content* is ever on screen. Only its header leans in, and that is a separate
        // layer moving at its own speed — see below.
        val sectionWidth = (maxWidth - sectionPadding).coerceAtLeast(120.dp)
        val sectionWidthPx = with(density) { sectionWidth.toPx() }
        val viewportPx = with(density) { maxWidth.toPx() }
        val cyclePx = sectionWidthPx * count
        // How far apart the headers sit. Less than a section's width by the peek, which is exactly
        // what makes the next one show at the right edge while its content is still off-screen.
        val headerSpacingPx = (viewportPx - with(density) { sectionPeek.toPx() })
            .coerceAtLeast(1f)

        // How far the foreground has travelled inside one cycle, in pixels. Everything periodic is
        // built on this rather than on the raw page number: multiplied out, a page index near
        // Int.MAX_VALUE / 2 leaves a float with no fractional precision at all.
        fun travelled(): Float {
            val within = pager.currentPage.mod(count) + pager.currentPageOffsetFraction
            return (within * sectionWidthPx).mod(cyclePx)
        }

        // Layer 1 — background, slowest.
        if (background != null) {
            if (wrapping && backgroundTiles) {
                // Round the speed so one cycle is a whole number of windows, then two copies
                // side by side hide the seam: the jump home lands exactly on a copy boundary.
                val windows = ((cyclePx * backgroundParallax) / viewportPx).roundToInt().coerceAtLeast(1)
                val speed = windows * viewportPx / cyclePx
                repeat(2) { copy ->
                    Box(
                        Modifier
                            .matchParentSize()
                            .graphicsLayer {
                                translationX = copy * size.width - (travelled() * speed).mod(size.width)
                            },
                        content = background
                    )
                }
            } else if (wrapping) {
                // A picture cannot be tiled. Two copies of a gradient that starts and ends on the
                // same colour join invisibly; two copies of an album cover join in a hard vertical
                // line through whatever is in it, which is what "the artwork is cropped crookedly"
                // is — the same photograph twice, cut where the copies meet.
                //
                // So it is one copy, wider than the window, panned inside its own overhang: nothing
                // to join. Wider by *layout* rather than by scaleX, because a stretched face is the
                // other way to make a picture look wrong; the backdrop's own crop takes care of it.
                //
                // The pan runs out and back over one circuit instead of one way. A capped pan is not
                // periodic, and the panorama's position is: a shift that grows with travel would be
                // at its far end just before the wrap and at zero just after it, so going round would
                // jerk the backdrop back once per lap. Out-and-back is worth the reversal.
                Box(
                    Modifier
                        .fillMaxHeight()
                        .width(maxWidth * BackgroundScale)
                        .graphicsLayer {
                            val maxShift = BackgroundMaxShift * size.width
                            val phase = (travelled() / cyclePx).coerceIn(0f, 1f)
                            translationX = -(1f - abs(2f * phase - 1f)) * maxShift
                        },
                    content = background
                )
            } else {
                // Fill the viewport exactly, widen via scaleX, and pan by a capped fraction of the
                // layer's own size so the viewport is always covered — no black at either end.
                Box(
                    Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            scaleX = BackgroundScale
                            val maxShift = BackgroundMaxShift * size.width
                            translationX =
                                (-backgroundParallax * travelled()).coerceIn(-maxShift, maxShift)
                        },
                    content = background
                )
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                // Nothing is inset here, and that is deliberate. A section's content is a *list*,
                // and a list answers for the navigation bar inside its own scroll — the rows run to
                // the bottom edge of the screen and pass under the gesture pill, while the end of
                // the list still stops above it (see [MetroBottomInset], which [MetroLongList]
                // applies for you). Insetting the whole column instead is the obvious version and
                // looks wrong: the list stops dead a finger's width above the bottom of the screen,
                // a row sliced in half, with bare background underneath and nothing to explain it.
                //
                // The *top* is inset, because the title has to clear the status bar and only the
                // window knows how tall that is here.
                .statusBarsPadding()
                // And clipped there, so a title rolling away disappears behind the status bar rather
                // than sliding across the clock: what collapses is placed above its own bounds, and
                // this is the edge it should vanish at.
                .clipToBounds()
                .padding(top = titleTopPadding)
        ) {
            // The name of the place, which gives up its height to the section once you start reading
            // one — see [MetroCollapse]. Overline and title go together: a lone overline left above
            // the sections would be a label with nothing under it.
            CollapsingTitle(collapsingTitle, collapse) {
            if (overline != null) {
                Text(
                    text = overline,
                    color = colors.fg,
                    fontFamily = MetroSemilight,
                    fontSize = 14.sp,
                    letterSpacing = 2.sp,
                    style = TightText,
                    modifier = Modifier.padding(start = 26.dp, bottom = 2.dp)
                )
            }

            // Layer 2 — the giant title. Repeated when the panorama wraps, at a spacing that
            // divides the distance it travels in one cycle, so coming home is invisible. The
            // repetition is not a workaround: the phone's circular panorama reads the same way,
            // the title marching past again as you keep going round.
            var titleWidth by remember { mutableIntStateOf(0) }
            val gapPx = with(density) { TitleRepeatGap.toPx() }
            val period = if (wrapping && titleWidth > 0) {
                val travel = cyclePx * titleParallax
                val repeats = floor(travel / (titleWidth + gapPx)).toInt().coerceAtLeast(1)
                travel / repeats
            } else {
                0f
            }
            val copies = if (period > 0f) ceil(viewportPx / period).toInt() + 1 else 1

            Box(
                Modifier
                    .trimVertically(TitleInkTop, TitleInkBottom)
                    .padding(start = 22.dp)
                    .graphicsLayer {
                        val moved = travelled() * titleParallax
                        translationX = if (period > 0f) -moved.mod(period) else -moved
                    }
            ) {
                repeat(copies) { copy ->
                    Text(
                        text = title,
                        color = colors.fg,
                        fontFamily = MetroLight,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Visible,
                        style = TightTitle,
                        modifier = Modifier
                            // Measured at its natural width, not at the window's: a title wider
                            // than the screen is the norm here, and measuring it clamped feeds a
                            // too-small width back into the period, which then clamps it further —
                            // the copies converge on top of each other in a couple of frames.
                            .wrapContentWidth(align = Alignment.Start, unbounded = true)
                            .onSizeChanged { titleWidth = it.width }
                            // Spaced by exactly one period, so a jump of one period is invisible.
                            .graphicsLayer { translationX = copy * period }
                    )
                }
            }
            }

            Spacer(Modifier.height(titleGap))

            // Layer 3 — the section headers, on their own and slower than what they name.
            //
            // They are not part of the pages. A header that travels with its content can only lean
            // into the current screen by dragging that content in with it, and a sliver of the next
            // section's list hanging off the right edge is not what the phone does — there, the
            // headers are their own layer between the giant title and the content, moving at their
            // own speed. That speed falls out of the geometry: the headers are [sectionPeek] closer
            // together than the pages are wide, so one page of scrolling moves them one page less
            // the peek, and the next one is always showing by exactly that much.
            Box(Modifier.fillMaxWidth().clipToBounds()) {
                val settled = pager.currentPage
                // Farthest first, so a header longer than the spacing is overlapped by the one you
                // are actually on rather than the other way round.
                for (page in (settled - 1..settled + 2).sortedByDescending { abs(it - settled) }) {
                    val section = sections[page.mod(count)]
                    val indent = with(density) { (sectionPadding + section.headerPadding).toPx() }
                    Text(
                        text = section.title,
                        color = colors.fg,
                        fontFamily = MetroSemilight,
                        fontSize = headerFontSize,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Visible,
                        style = TightText,
                        modifier = Modifier
                            // Natural width; the layout box would otherwise clamp a long header to
                            // the window and the ellipsis would appear before the peek does.
                            .wrapContentWidth(align = Alignment.Start, unbounded = true)
                            .then(
                                section.onHeaderClick?.let { click ->
                                    Modifier.clickable { click() }
                                } ?: Modifier
                            )
                            .graphicsLayer {
                                // Read here rather than in composition, so a drag costs a layer
                                // update instead of a recomposition per frame.
                                //
                                // The page distance is taken as an Int subtraction *first*. Both
                                // page numbers sit near Int.MAX_VALUE / 2 when the panorama wraps,
                                // and a float that large has no room left for a fractional part:
                                // subtracting them as floats gives exactly zero, which silently
                                // made every header full strength.
                                val away = (page - settled) - pager.currentPageOffsetFraction
                                translationX = away * headerSpacingPx + indent
                                alpha = 1f - abs(away).coerceIn(0f, 1f) * (1f - AwayHeaderAlpha)
                            }
                    )
                }
            }

            Spacer(Modifier.height(headerGap))

            // Layer 4 — the content, full width, one section per page. A pager gives the snapping,
            // the laziness and the endless page range for nothing.
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = sectionPadding),
                pageSize = PageSize.Fill,
                beyondViewportPageCount = 1,
                flingBehavior = PagerDefaults.flingBehavior(
                    state = pager,
                    // A brisk decay, so a flick carries the panorama about as far as the finger
                    // actually threw it. The default coasts, which on a page-snapping surface turns
                    // a small movement into a committed change of section.
                    decayAnimationSpec = exponentialDecay(frictionMultiplier = 2.5f),
                    snapAnimationSpec = tween(SettleMillis, easing = LinearOutSlowInEasing),
                    // Half a page, which is the deliberate reading: a section changes when you have
                    // dragged most of the way there. This was 0.3 for a while on the theory that a
                    // short flick should still commit, and the result was a panorama that changed
                    // section on any stray movement — which is worse than one that needs convincing,
                    // because the accidental change costs you your place.
                    snapPositionalThreshold = 0.5f
                )
            ) { page ->
                Column(Modifier.fillMaxSize()) {
                    sections[page.mod(count)].content(this)
                }
            }
        }
    }
}

/**
 * One panorama section: semilight header + content, with trailing gap to the next.
 *
 * Content is laid out in a [ColumnScope], so a long section can hand its list
 * `Modifier.weight(1f)` and have it fill the height left under the header — which is what
 * makes a `LazyColumn` inside a section work. Give that list an explicit width; the
 * scrolling row measures its children with an unbounded one.
 *
 * A section also reports where it starts, so its panorama can settle on it. That is all it takes
 * part in — the snapping decision, the animation and the fling all belong to [MetroPanorama].
 *
 * [headerPadding] indents the header alone. It exists for the case where the content brings its own
 * gutter — a [MetroLongList], a column of [ListRow]s — which would otherwise sit a row's worth of
 * padding to the right of the header naming it. Set the panorama's `sectionPadding` small and this
 * to the row padding, and header and rows line up on the same edge.
 */
@Composable
fun PanoramaSection(
    title: String,
    modifier: Modifier = Modifier,
    headerPadding: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val stops = LocalPanoramaStops.current
    val id = remember { Any() }
    DisposableEffect(stops, id) {
        onDispose { stops?.remove(id) }
    }

    Column(
        modifier
            .onPlaced { stops?.put(id, it.positionInParent().x) }
            .padding(end = 56.dp)
    ) {
        Text(
            text = title,
            color = MetroTheme.colors.fg,
            fontFamily = MetroSemilight,
            fontSize = 42.sp,
            modifier = Modifier.padding(start = headerPadding, bottom = 18.dp)
        )
        content()
    }
}

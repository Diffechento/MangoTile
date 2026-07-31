package com.metrocompose

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.launch

/** Breathing space after the last row, on top of whatever the navigation bar needs. */
private val ListEndGap = 20.dp

/**
 * One alphabetical bucket of a [MetroLongList] — the shape a list grouped by letter takes.
 *
 * The general form of a group is a *label*, not a letter, since a list can be arranged by anything
 * that has runs in it; see [MetroListSort].
 */
data class MetroListGroup<T>(val letter: Char, val items: List<T>)

/**
 * Normalizes a string to its jump-list bucket: 'A'..'Z' or 'А'..'Я' for letters, '#' for everything
 * else — digits, symbols, and scripts this doesn't have an alphabet for. The usual argument for
 * `group` in [MetroLongList].
 *
 * Cyrillic gets its own buckets rather than being swept into '#' with the symbols, which is what a
 * Latin-only version does to a Russian library: one enormous group under a heading that says
 * nothing. Ё is filed under Е, as a Russian phone book does — a separate bucket for it would hold
 * two artists and sit at the end of the alphabet, where nobody looks for them.
 */
fun metroGroupChar(text: String): Char {
    val c = text.trim().firstOrNull()?.uppercaseChar() ?: return '#'
    return when {
        c in 'A'..'Z' -> c
        c == 'Ё' -> 'Е'
        c in 'А'..'Я' -> c
        else -> '#'
    }
}

/**
 * A way of arranging a [MetroLongList]: the order the rows go in, what the header above each run of
 * them says, and — when the groups form a domain, as letters do — the alphabet to zoom out to.
 *
 * Sorting is data here rather than a flag, so a screen can hold several arrangements of one list and
 * hand it whichever is in force. [name] is what a menu of them shows, which is how [MetroLongList]
 * offers the others on a hold of a group header: alphabetical is the WP8 default, but "by date
 * added" or "by length" is the same list read another way, not another list.
 *
 *   val byLength = MetroListSort<Track>(
 *       name = "length",
 *       comparator = compareBy { it.durationMs },
 *       header = { if (it.durationMs < 120_000) "under 2 min" else "2 min and over" }
 *   )
 *
 * [header] is called per row, and **consecutive** rows with the same label form one group. Runs
 * rather than buckets on purpose: a [header] that does not follow [comparator] is a mistake in the
 * caller, and a repeated header shows it, where collecting scattered rows into one bucket would
 * quietly move rows out of the order the comparator asked for. A label of `""` draws no header at
 * all, which is what an arrangement with nothing useful to say per run wants.
 *
 * The label is drawn as it is given. WP8's own group headers are lowercase, which is what
 * [alphabetical] produces.
 *
 * **Every arrangement zooms out.** A tap on a header opens the whole set of groups over the screen and
 * jumps to the one picked — the alphabet for [alphabetical], the months for a list by date, the bands
 * for a list by length. That is what the header does in WP8 and what it must keep doing here: an
 * arrangement whose header answered a tap with nothing would be a control that works on one setting
 * and is dead on the rest.
 *
 * [jumpDomain] is only about what that screen shows *besides* what is there: given the labels the list
 * has, it answers with the whole domain, and the ones with nothing in them are dimmed and inert, as
 * WP8 dims the letters you own no artists under. Null — the default — means the groups themselves, in
 * the order the list has them, which is the right answer whenever the domain is open-ended: there is no
 * "whole set of months", only the months your files were added in.
 *
 * The screen lays single-character labels out as the phone's grid of squares and words as a column of
 * blocks, so a domain may be either.
 *
 * Hold a sort in a `remember`: the list re-sorts when the object changes, so a fresh one per
 * recomposition would sort the whole list on every frame.
 */
@Immutable
class MetroListSort<T>(
    val name: String,
    val comparator: Comparator<T>,
    val header: (T) -> String,
    val jumpDomain: ((Set<String>) -> List<String>)? = null
) {
    companion object {
        /**
         * The A–Z (and А–Я) arrangement, with the zoom-out grid over it: rows filed under the first
         * letter of [text], and ordered by that text within their letter.
         *
         * This is what the `group`-taking [MetroLongList] does, spelled as a sort so it can sit in a
         * menu beside the others.
         */
        fun <T> alphabetical(name: String = "name", text: (T) -> String): MetroListSort<T> =
            MetroListSort(
                name = name,
                comparator = compareBy<T> { metroGroupChar(text(it)) }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { text(it).trim() },
                header = { metroGroupChar(text(it)).lowercaseChar().toString() },
                jumpDomain = { present -> jumpAlphabet(present) }
            )
    }
}

/**
 * Which alphabets the jump grid shows: '#' always, then whichever scripts the list actually uses —
 * and Latin as the fallback when it uses none, so an empty list still shows a grid.
 *
 * Offering every alphabet unconditionally would be sixty tiles for a library of English albums.
 * Bucket order matches this, because '#' (35) sorts below 'a' (97) which sorts below 'а' (1072).
 */
private fun jumpAlphabet(available: Set<String>): List<String> {
    val letters = available.mapNotNull { it.singleOrNull() }
    val cyrillic = letters.any { it in 'а'..'я' }
    val latin = letters.any { it in 'a'..'z' } || !cyrillic
    return buildList {
        add("#")
        if (latin) ('a'..'z').forEach { add(it.toString()) }
        if (cyrillic) ('а'..'я').forEach { add(it.toString()) }
    }
}

/** A run of rows sharing one header label — what the list is actually built out of. */
private data class LabelledGroup<T>(val label: String, val items: List<T>)

/**
 * WP8's LongListSelector: a lazy list broken into groups, each introduced by a square accent
 * header. Tapping the header zooms out to the jump grid — the whole alphabet at once, with empty
 * buckets dimmed and unclickable — and picking a letter there jumps straight to that group.
 *
 * Only visible rows are composed, so this scales to a full music library. Grouping is computed
 * once per [items] identity, not per frame.
 *
 *   MetroLongList(
 *       items = songs,
 *       key = { it.id },
 *       group = { metroGroupChar(it.title) }
 *   ) { song -> ListRow(song.title, song.artist) }
 *
 * [filledGroupHeaders] can drop the accent square and leave the bare letter. Over a list of text
 * rows the filled tile is the WP8 look, but over rows that are themselves coloured tiles it turns
 * into a second competing block of accent, which is what the letter is meant to be introducing.
 *
 * This is the alphabetical form. The [MetroListSort]-taking overload is the same list arranged by
 * anything else — by date, by length, by how often a song has been played — and can offer those
 * arrangements on a hold of a group header.
 */
@Composable
fun <T> MetroLongList(
    items: List<T>,
    key: (T) -> Any,
    group: (T) -> Char,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    filledGroupHeaders: Boolean = true,
    row: @Composable (T) -> Unit
) {
    // Grouping by letter and leaving the rows alone inside each group, which is what this list has
    // always done: the sort is stable, so a row keeps the place the caller gave it.
    val sort = remember(group) {
        MetroListSort<T>(
            name = "",
            comparator = compareBy { group(it) },
            header = { group(it).lowercaseChar().toString() },
            jumpDomain = { present -> jumpAlphabet(present) }
        )
    }
    MetroLongList(
        items = items,
        key = key,
        sort = sort,
        modifier = modifier,
        state = state,
        filledGroupHeaders = filledGroupHeaders,
        row = row
    )
}

/**
 * The LongListSelector arranged by anything: [sort] decides the order of the rows and what the
 * header above each run of them says. See [MetroListSort].
 *
 * A **tap** on a header zooms out over the screen, whatever the arrangement: the alphabet by name, the
 * months by date, the bands by length. A **hold** unrolls [sorts] out of the header itself — the WP8
 * context menu, the same one a held row gets, so the menu comes out of the thing you pressed with the
 * arrangement in force in accent — and [onSortSelected] gets the index picked; the caller keeps the
 * choice, so it can be remembered across launches. The two gestures stay what they are on every
 * arrangement — a tap that meant "zoom out" under one heading and "choose an arrangement" under the
 * next would teach the user one thing and then do another.
 *
 *   MetroLongList(
 *       items = songs, key = { it.id },
 *       sort = sorts[current], sorts = sorts,
 *       onSortSelected = { current = it }
 *   ) { song -> ListRow(song.title, song.artist) }
 */
@Composable
fun <T> MetroLongList(
    items: List<T>,
    key: (T) -> Any,
    sort: MetroListSort<T>,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    filledGroupHeaders: Boolean = true,
    sorts: List<MetroListSort<T>> = emptyList(),
    onSortSelected: (Int) -> Unit = {},
    row: @Composable (T) -> Unit
) {
    val scope = rememberCoroutineScope()
    var jumpOpen by remember { mutableStateOf(false) }
    // Which header is holding the menu open. The menu unrolls out of its own header, so it belongs to
    // one of them rather than to the list — two headers are on screen at once as often as not.
    var sortMenuFor by remember { mutableStateOf<Int?>(null) }

    val groups = remember(items, sort) {
        val ordered = items.sortedWith(sort.comparator)
        buildList {
            var run = mutableListOf<T>()
            var label: String? = null
            ordered.forEach { item ->
                val itemLabel = sort.header(item)
                if (itemLabel != label) {
                    if (label != null) add(LabelledGroup(label!!, run))
                    label = itemLabel
                    run = mutableListOf()
                }
                run.add(item)
            }
            if (label != null) add(LabelledGroup(label!!, run))
        }
    }

    // The lazy index of each group's header, so the jump grid can scroll straight to it. A group
    // with no header of its own is one item shorter, and a grid pointed at the row after it would
    // land a screenful away on a long list.
    val headerIndices = remember(groups) {
        var index = 0
        groups.map { g -> index.also { index += g.items.size + if (g.label.isEmpty()) 0 else 1 } }
    }

    // What the zoom-out shows: the whole domain where the arrangement has one — the alphabet, with the
    // letters you own nothing under dimmed — and otherwise the groups themselves. There is no set of
    // all months, only the months the files were added in, so an open-ended arrangement zooms out over
    // what it has rather than over nothing.
    val jumpLabels = remember(groups, sort) {
        val present = groups.map { it.label }.filter { it.isNotEmpty() }
        sort.jumpDomain?.invoke(present.toSet()) ?: present.distinct()
    }
    val openGrid = if (jumpLabels.isEmpty()) null else ({ jumpOpen = true })
    val selectedSort = sorts.indexOfFirst { it.name == sort.name }.takeIf { it >= 0 }

    Box(modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = state) {
            groups.forEachIndexed { index, g ->
                if (g.label.isNotEmpty()) {
                    // The index is in the key because a header label is only unique when the
                    // arrangement's runs are: a `header` that disagrees with its comparator repeats
                    // one, and two lazy items under one key is a crash rather than a wrong heading.
                    item(key = "metro-group-$index-${g.label}", contentType = "header") {
                        // The gap around the header is outside the menu's anchor on purpose: the
                        // anchor is what lights up under the sheet and what the strip spreads across,
                        // and padding inside it would light up a block bigger than the tile.
                        Box(Modifier.padding(start = 24.dp, top = 14.dp, bottom = 6.dp)) {
                            if (sorts.isEmpty()) {
                                GroupHeader(
                                    label = g.label,
                                    enabled = true,
                                    filled = filledGroupHeaders,
                                    onClick = openGrid
                                )
                            } else {
                                MetroContextMenu(
                                    expanded = sortMenuFor == index,
                                    items = sorts.map { it.name },
                                    selectedItem = selectedSort,
                                    onSelect = { picked ->
                                        sortMenuFor = null
                                        onSortSelected(picked)
                                    },
                                    onDismiss = { sortMenuFor = null }
                                ) {
                                    GroupHeader(
                                        label = g.label,
                                        enabled = true,
                                        filled = filledGroupHeaders,
                                        onClick = openGrid,
                                        onLongClick = { sortMenuFor = index }
                                    )
                                }
                            }
                        }
                    }
                }
                items(g.items, key = key, contentType = { "row" }) { item -> row(item) }
            }
            // The navigation bar, taken inside the scroll rather than around it, so the rows reach
            // the bottom edge of the screen and the last one can still be scrolled clear of the
            // gesture pill. Zero inside a [MetroPage], which has already consumed the inset.
            item(key = "metro-bottom-inset", contentType = "inset") {
                MetroBottomInset(extra = ListEndGap)
            }
        }

        // In a popup rather than over the list, because the alphabet is a whole-screen thing on
        // the phone and the list is not always a whole screen wide — inside a panorama section it
        // is a column of about 320dp, and five 58dp tiles in a row do not fit in that.
        val grid = remember { MutableTransitionState(false) }
        grid.targetState = jumpOpen
        if (jumpLabels.isNotEmpty() && (grid.currentState || grid.targetState)) {
            Popup(
                properties = PopupProperties(focusable = true),
                onDismissRequest = { jumpOpen = false }
            ) {
                AnimatedVisibility(
                    visibleState = grid,
                    enter = fadeIn(tween(140)) + scaleIn(tween(140), initialScale = 0.85f),
                    exit = fadeOut(tween(140)) + scaleOut(tween(140), targetScale = 0.85f)
                ) {
                    JumpGrid(
                        labels = jumpLabels,
                        available = groups.map { it.label }.toSet(),
                        onPick = { label ->
                            val i = groups.indexOfFirst { it.label == label }
                            if (i >= 0) scope.launch { state.scrollToItem(headerIndices[i]) }
                            jumpOpen = false
                        },
                        onDismiss = { jumpOpen = false }
                    )
                }
            }
        }
    }

    if (jumpOpen) BackHandler { jumpOpen = false }
}

/**
 * The accent square carrying a group's label — also the handle that opens the jump grid, and, where
 * a list offers several arrangements, the one that opens their menu.
 *
 * Unfilled ([filled] false) it keeps the same footprint and the same tap target, so a list that
 * opts out of the square still scrolls and jumps identically; only the block of accent goes.
 *
 * A label of one character is the letter tile proper: a square, with the text at half its height. A
 * word ("july 2026", "10+ plays") keeps the square's height and grows sideways instead, at a smaller
 * size — a month at the letter's 22sp fills the width of a panorama section on its own.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupHeader(
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    tileSize: Dp = 44.dp,
    filled: Boolean = true,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null
) {
    val colors = MetroTheme.colors
    val single = label.length == 1
    Box(
        modifier
            .then(
                if (single) {
                    Modifier.size(tileSize)
                } else {
                    Modifier.height(tileSize).widthIn(min = tileSize)
                }
            )
            .then(
                if (filled) {
                    Modifier.background(
                        if (enabled) colors.accent else colors.dim.copy(alpha = 0.3f)
                    )
                } else {
                    Modifier
                }
            )
            // Nothing to press is left unpressable rather than given a no-op: an arrangement with no
            // domain and no menu behind it has no handle, and a tile that lights up under a thumb
            // and then does nothing is worse than one that does not answer at all.
            .then(
                if (onClick == null && onLongClick == null) {
                    Modifier
                } else {
                    Modifier.combinedClickable(
                        enabled = enabled,
                        onLongClick = onLongClick,
                        onClick = { onClick?.invoke() }
                    )
                }
            )
            .then(if (single || !filled) Modifier else Modifier.padding(horizontal = 10.dp)),
        contentAlignment = if (filled) Alignment.Center else Alignment.CenterStart
    ) {
        Text(
            text = label,
            color = when {
                !enabled -> colors.subtle
                filled -> Metro.Fg
                else -> colors.fg
            },
            fontFamily = MetroSemilight,
            fontSize = headerFontSize(tileSize, single)
        )
    }
}

/** Half the tile for a letter, and a size that lets a word fit beside its neighbours. */
private fun headerFontSize(tileSize: Dp, single: Boolean): TextUnit =
    if (single) (tileSize.value * 0.5f).sp else (tileSize.value * 0.41f).sp

/**
 * The zoomed-out set of groups. Empty buckets are dimmed and inert, exactly like WP8.
 *
 * Letters get the phone's grid: five big tiles to a row, or seven smaller ones once Cyrillic doubles
 * the alphabet, because sixty tiles five to a row are twice the height of a screen. The tile is
 * whatever the width allows, up to the phone's 58dp.
 *
 * Words ("july 2026", "10+ plays") cannot be squares, so they are a column of blocks the width of
 * their own text — the same shape they have as headings in the list, which is what makes the zoom
 * read as the same list pulled back rather than as another screen. It scrolls, because a library
 * added to over four years has fifty months in it.
 */
@Composable
private fun JumpGrid(
    labels: List<String>,
    available: Set<String>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MetroTheme.colors
    val squares = labels.all { it.length == 1 }
    val columns = if (labels.size > 32) 7 else 5

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            // Not quite solid: the list you zoomed out of stays faintly there behind the groups, so
            // this reads as the same page pulled back rather than as a different screen you were sent
            // to. WP8's own zoom-out was opaque; on a phone-sized library of four buckets, opaque is
            // four blocks floating in a void with nothing to say where they came from.
            .background(colors.bg.copy(alpha = ZoomOutAlpha))
            .clickable(indication = null, interactionSource = null) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        val gap = 8.dp
        if (squares) {
            val tile = ((maxWidth - 40.dp - gap * (columns - 1)) / columns).coerceIn(28.dp, 58.dp)
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(gap)
            ) {
                labels.chunked(columns).forEach { rowLetters ->
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        rowLetters.forEach { letter ->
                            GroupHeader(
                                label = letter,
                                enabled = letter in available,
                                tileSize = tile,
                                onClick = { onPick(letter) }
                            )
                        }
                    }
                }
            }
        } else {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(gap)
            ) {
                labels.forEach { label ->
                    GroupHeader(
                        label = label,
                        enabled = label in available,
                        tileSize = WordTileHeight,
                        onClick = { onPick(label) }
                    )
                }
            }
        }
    }
}

/** A word block in the zoom-out, a shade taller than the heading it stands for. */
private val WordTileHeight = 52.dp

/** How solid the zoom-out is over the list it came from. */
private const val ZoomOutAlpha = 0.86f

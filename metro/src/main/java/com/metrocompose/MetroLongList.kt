package com.metrocompose

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.launch

/** Breathing space after the last row, on top of whatever the navigation bar needs. */
private val ListEndGap = 20.dp

/** One alphabetical bucket of a [MetroLongList]. */
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
 * Which alphabets the jump grid shows: '#' always, then whichever scripts the list actually uses —
 * and Latin as the fallback when it uses none, so an empty list still shows a grid.
 *
 * Offering every alphabet unconditionally would be sixty tiles for a library of English albums.
 * Bucket order matches this, because '#' (35) sorts below 'A' (65) which sorts below 'А' (1040).
 */
private fun jumpAlphabet(available: Set<Char>): List<Char> {
    val cyrillic = available.any { it in 'А'..'Я' }
    val latin = available.any { it in 'A'..'Z' } || !cyrillic
    return buildList {
        add('#')
        if (latin) addAll('A'..'Z')
        if (cyrillic) addAll('А'..'Я')
    }
}

/**
 * WP8's LongListSelector: a lazy list broken into alphabetical groups, each introduced by a
 * square accent letter tile. Tapping a letter zooms out to the jump grid — the whole alphabet
 * at once, with empty buckets dimmed and unclickable — and picking a letter there jumps
 * straight to that group.
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
    val scope = rememberCoroutineScope()
    var jumpOpen by remember { mutableStateOf(false) }

    val groups = remember(items) {
        items.groupBy(group)
            .toList()
            .sortedBy { it.first }
            .map { (letter, groupItems) -> MetroListGroup(letter, groupItems) }
    }

    // The lazy index of each group's header tile, so the jump grid can scroll straight to it.
    val headerIndices = remember(groups) {
        var index = 0
        groups.map { g -> index.also { index += 1 + g.items.size } }
    }

    Box(modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = state) {
            groups.forEach { g ->
                item(key = "metro-group-${g.letter}", contentType = "header") {
                    LetterTile(
                        letter = g.letter,
                        enabled = true,
                        modifier = Modifier.padding(start = 24.dp, top = 14.dp, bottom = 6.dp),
                        filled = filledGroupHeaders
                    ) { jumpOpen = true }
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
        if (grid.currentState || grid.targetState) {
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
                        available = groups.map { it.letter }.toSet(),
                        onPick = { letter ->
                            val i = groups.indexOfFirst { it.letter == letter }
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
 * The accent square carrying a group's letter — also the handle that opens the jump grid.
 *
 * Unfilled ([filled] false) it keeps the same footprint and the same tap target, so a list that
 * opts out of the square still scrolls and jumps identically; only the block of accent goes.
 */
@Composable
private fun LetterTile(
    letter: Char,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    tileSize: Dp = 44.dp,
    filled: Boolean = true,
    onClick: () -> Unit
) {
    val colors = MetroTheme.colors
    Box(
        modifier
            .size(tileSize)
            .then(
                if (filled) {
                    Modifier.background(
                        if (enabled) colors.accent else colors.dim.copy(alpha = 0.3f)
                    )
                } else {
                    Modifier
                }
            )
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = if (filled) Alignment.Center else Alignment.CenterStart
    ) {
        Text(
            text = letter.lowercaseChar().toString(),
            color = when {
                !enabled -> colors.subtle
                filled -> Metro.Fg
                else -> colors.fg
            },
            fontFamily = MetroSemilight,
            fontSize = (tileSize.value * 0.5f).sp
        )
    }
}

/**
 * The zoomed-out alphabet. Empty buckets are dimmed and inert, exactly like WP8.
 *
 * The grid sizes itself: a Latin-only list gets the phone's five big tiles, and a list that also
 * needs Cyrillic gets seven smaller ones, because sixty tiles five to a row are twice the height of
 * a screen. The tile is whatever the width allows, up to the phone's 58dp.
 */
@Composable
private fun JumpGrid(
    available: Set<Char>,
    onPick: (Char) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MetroTheme.colors
    val alphabet = remember(available) { jumpAlphabet(available) }
    val columns = if (alphabet.size > 32) 7 else 5

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(colors.bg)
            .clickable(indication = null, interactionSource = null) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        val gap = 8.dp
        val tile = ((maxWidth - 40.dp - gap * (columns - 1)) / columns).coerceIn(28.dp, 58.dp)
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(gap)
        ) {
            alphabet.chunked(columns).forEach { rowLetters ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    rowLetters.forEach { letter ->
                        LetterTile(
                            letter = letter,
                            enabled = letter in available,
                            tileSize = tile
                        ) { onPick(letter) }
                    }
                }
            }
        }
    }
}

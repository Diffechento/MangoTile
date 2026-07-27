package com.metrocompose.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrocompose.MetroBackStack
import com.metrocompose.MetroButton
import com.metrocompose.MetroCheckBox
import com.metrocompose.MetroPage
import com.metrocompose.MetroRegular
import com.metrocompose.MetroSemilight
import com.metrocompose.MetroTextBox
import com.metrocompose.MetroTheme

/**
 * [MetroBackStack] itself, rather than the turnstile it drives.
 *
 * Push a few levels and the interesting parts show up: `popTo` unwinds to a destination already on
 * the stack in one go, `replaceAll` throws the whole thing away for a new root (what a sign-out
 * does), and `canGoBack` is what the host's system-Back handler reads.
 *
 * The checkbox and the text box are here to prove the other half of the host: each level keeps its
 * own `rememberSaveable` state and its own scroll offset while it is covered, and gets them back on
 * the way out — type something in, go two deeper, come back.
 */
@Composable
fun BackStackScreen(level: Int, nav: MetroBackStack<Nav>) {
    val colors = MetroTheme.colors
    var note by rememberSaveable { mutableStateOf("") }
    var ticked by rememberSaveable { mutableStateOf(false) }

    MetroPage("NAVIGATION", "level $level") {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp)
        ) {
            // The stack's public surface: what is on top, how deep it is, and whether Back has
            // anywhere to go. The entries themselves are the host's business, not the screen's.
            Text(
                "current  ${nav.current.label()}",
                color = colors.accent, fontFamily = MetroRegular, fontSize = 17.sp
            )
            Text(
                "size ${nav.size}   ·   canGoBack ${nav.canGoBack}",
                color = colors.subtle, fontFamily = MetroRegular, fontSize = 14.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
            )

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MetroButton("push level ${level + 1}", filled = true) {
                    nav.push(Nav.Deep(level + 1))
                }
                MetroButton("pop") { nav.pop() }
                MetroButton("popTo(level 1)") { nav.popTo(Nav.Deep(1)) }
                MetroButton("popTo(start)") { nav.popTo(Nav.Start) }
                MetroButton("replaceAll(start)") { nav.replaceAll(Nav.Start) }
            }

            Spacer(Modifier.height(28.dp))
            Text(
                "per-destination state",
                color = colors.subtle, fontFamily = MetroSemilight, fontSize = 22.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                "Type here, push two levels, come back. MetroNavHost keeps each destination's " +
                    "saveable state while it is covered and drops it when it leaves the stack.",
                color = colors.dim, fontFamily = MetroRegular, fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            MetroTextBox(note, { note = it }, placeholder = "a note that should survive")
            Spacer(Modifier.height(10.dp))
            MetroCheckBox(ticked, { ticked = it }, "and a checkbox")

            Spacer(Modifier.height(24.dp))
            // Enough content to scroll, so the retained scroll offset is visible too.
            repeat(12) { i ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .padding(bottom = 6.dp)
                        // Transparent rather than colors.bg on the off rows, so a MetroBackdrop
                        // still shows through between the banded ones.
                        .background(if (i % 2 == 0) colors.accent.copy(alpha = 0.20f) else Color.Transparent)
                ) {
                    Text(
                        "row ${i + 1} — scroll down, push, come back",
                        color = colors.subtle, fontFamily = MetroRegular, fontSize = 14.sp,
                        modifier = Modifier.padding(start = 10.dp, top = 10.dp)
                    )
                }
            }
        }
    }
}

/** Short name for a destination. */
private fun Nav.label(): String = when (this) {
    is Nav.Deep -> "level $level"
    is Nav.Song -> "song · $title"
    else -> this::class.simpleName?.lowercase() ?: "?"
}

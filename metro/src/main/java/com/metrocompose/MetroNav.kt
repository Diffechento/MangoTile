@file:OptIn(ExperimentalSharedTransitionApi::class, ExperimentalAnimationApi::class)

package com.metrocompose

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/**
 * A minimal navigation stack: a list of destinations of your own type, usually a sealed
 * interface so a destination can carry typed arguments.
 *
 * Reads of [current] are observable, so a composable that renders it recomposes on navigation.
 */
@Stable
class MetroBackStack<T : Any> private constructor(entries: List<T>) {

    constructor(initial: T) : this(listOf(initial))

    private val entries = mutableStateListOf<T>().also { it.addAll(entries) }

    /** The destination on top of the stack — what should be on screen. */
    val current: T get() = entries.last()

    val size: Int get() = entries.size

    val canGoBack: Boolean get() = entries.size > 1

    /** True when the last movement was a [pop], so transitions can play in reverse. */
    var isPopping: Boolean by mutableStateOf(false)
        private set

    fun push(destination: T) {
        isPopping = false
        entries.add(destination)
    }

    /** Returns false when already at the root, so a caller can fall through to system back. */
    fun pop(): Boolean {
        if (!canGoBack) return false
        isPopping = true
        entries.removeAt(entries.lastIndex)
        return true
    }

    /** Clears the stack down to a single destination — for "go home" style jumps. */
    fun replaceAll(destination: T) {
        isPopping = false
        entries.clear()
        entries.add(destination)
    }

    /** Pops until [destination] is on top, or does nothing if it isn't in the stack. */
    fun popTo(destination: T): Boolean {
        val index = entries.lastIndexOf(destination)
        if (index < 0 || index == entries.lastIndex) return false
        isPopping = true
        while (entries.lastIndex > index) entries.removeAt(entries.lastIndex)
        return true
    }

    internal fun snapshot(): List<T> = entries.toList()

    internal companion object {
        fun <T : Any> of(entries: List<T>): MetroBackStack<T> = MetroBackStack(entries)
    }
}

/** Remembers a back stack across recomposition. State is lost on process death. */
@Composable
fun <T : Any> rememberMetroBackStack(initial: T): MetroBackStack<T> =
    remember { MetroBackStack(initial) }

/**
 * Remembers a back stack that also survives configuration change and process death.
 *
 * Supply a round-trip encoding for a destination — for a sealed interface that's usually a
 * short tag plus an id:
 *
 *   rememberMetroBackStack(
 *       initial = Screen.Collection,
 *       save = { it.encode() },
 *       restore = { Screen.decode(it) }
 *   )
 */
@Composable
fun <T : Any> rememberMetroBackStack(
    initial: T,
    save: (T) -> String,
    restore: (String) -> T
): MetroBackStack<T> {
    val saver = remember(save, restore) {
        listSaver<MetroBackStack<T>, String>(
            save = { stack -> stack.snapshot().map(save) },
            restore = { encoded -> MetroBackStack.of(encoded.map(restore)) }
        )
    }
    return rememberSaveable(saver = saver) { MetroBackStack(initial) }
}

/** The [SharedTransitionScope] installed by [MetroNavHost]; null outside one. */
val LocalMetroSharedScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

/** The current destination's [AnimatedVisibilityScope]; null outside a [MetroNavHost]. */
val LocalMetroAnimScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Navigation host: renders the top of [backStack], animates between destinations with the
 * turnstile, and wires up system Back.
 *
 * It also installs the two scopes continuum needs, so screens can call [metroContinuum]
 * without having them threaded through as parameters.
 *
 *   val nav = rememberMetroBackStack<Screen>(Screen.Collection)
 *   MetroNavHost(nav) { screen ->
 *       when (screen) {
 *           Screen.Collection -> CollectionScreen(onOpen = nav::push)
 *           is Screen.Album -> AlbumScreen(screen.id)
 *       }
 *   }
 */
@Composable
fun <T : Any> MetroNavHost(
    backStack: MetroBackStack<T>,
    modifier: Modifier = Modifier,
    handleBack: Boolean = true,
    transition: AnimatedContentTransitionScope<T>.(popping: Boolean) -> ContentTransform =
        { popping -> metroTurnstile(reverse = popping) },
    key: (T) -> Any = { it.toString() },
    content: @Composable (T) -> Unit
) {
    if (handleBack) {
        BackHandler(enabled = backStack.canGoBack) { backStack.pop() }
    }

    // Each destination keeps its own scroll offsets, pager pages and `rememberSaveable` state while
    // it is off the stack's top, and gets them back when you come back to it. Without this the page
    // underneath is simply disposed by the AnimatedContent and rebuilt from its initial values — go
    // into settings from the far end of a panorama, change something, press Back, and you land on
    // the first section instead of where you left. [key] decides what counts as "the same
    // destination"; it has to be something saveable, hence `toString` by default.
    val stateHolder = rememberSaveableStateHolder()

    // What is no longer on the stack is not coming back, so its saved state goes with it. Doing
    // this on the way out rather than never is the difference between a back stack and a leak.
    val live = backStack.snapshot().map(key)
    val seen = remember { mutableStateListOf<Any>() }
    LaunchedEffect(live) {
        seen.filterNot { it in live }.forEach { gone ->
            stateHolder.removeState(gone)
            seen.remove(gone)
        }
        live.forEach { if (it !in seen) seen.add(it) }
    }

    // Driven explicitly rather than by the plain `AnimatedContent(targetState)` overload, because the
    // host needs to know *outside* the pages whether the turn is still running — see [metroFrozen].
    val turn = updateTransition(targetState = backStack.current, label = "metro-nav")

    SharedTransitionLayout(modifier.metroFrozen(turn.isRunning)) {
        turn.AnimatedContent(
            transitionSpec = { transition(backStack.isPopping) }
        ) { destination ->
            CompositionLocalProvider(
                LocalMetroSharedScope provides this@SharedTransitionLayout,
                LocalMetroAnimScope provides this@AnimatedContent
            ) {
                stateHolder.SaveableStateProvider(key(destination)) {
                    content(destination)
                }
            }
        }
    }
}

/**
 * Takes the screen's touches away for as long as a page is turning.
 *
 * A page transition is not a still picture with two layers: both pages are composed and both are
 * hit-testable, and an element flying between them under [metroContinuum] is drawn in the
 * transition's own overlay, **above** everything, with its flight aimed at where its twin is *now*.
 * Move either page while that is in the air — scroll the list it came out of, scroll the page it is
 * flying into, swipe the panorama to another section — and the aim is recomputed every frame, so the
 * picture chases a target the finger is dragging and crosses the screen over the top of the page,
 * the status bar included. Measured before this existed: tapping an album and scrolling the album
 * page while its cover was still on its way put the cover in the top-left corner over the clock.
 *
 * The cause is the screen moving under the flight, so that is what this removes. It is also what a
 * Windows Phone does — a page that is turning does not answer — and it costs a quarter of a second
 * during which the only thing anyone could usefully do is push a second copy of the page they have
 * just asked for.
 *
 * It consumes on the [PointerEventPass.Initial] pass, which is dispatched parent-first, so the down
 * is already spent by the time the pages inside see it. That is not the same as not delivering it:
 * a `draggable` still receives the down (it waits for one with `requireUnconsumed = false`) and then
 * cancels when the slop detector finds the change consumed, which is the same arbitration the edge
 * scrubber leans on. Only the surfaces *inside* the host are affected — a bar or a rising page that
 * is a sibling of [MetroNavHost] keeps working, which is why the mini player under one stays live.
 */
private fun Modifier.metroFrozen(frozen: Boolean): Modifier =
    if (!frozen) this else this.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
            }
        }
    }

/**
 * Continuum: mark an element with a [key], use the same key on the next screen, and it flows
 * from one to the other instead of cross-fading. This is the WP8 move where a tapped list
 * item's title grows into the next page's header.
 *
 *   Text(album.title, modifier = Modifier.metroContinuum("album-title-${album.id}"))
 *
 * **A key must be unique among the elements composed at the same time.** It identifies one
 * thing moving between screens, not a category. Give two live elements the same key — the same
 * album listed in two sections of one page, say — and they are treated as the same object and
 * laid out on top of each other, which looks like overlapping text rather than an error. If a
 * list can show an item twice, leave continuum off it.
 *
 * A no-op outside a [MetroNavHost], so shared components stay usable anywhere.
 *
 * **The flight is a tween of [durationMillis], not the runtime's default spring, and it is the same
 * number the page turn takes.** A spring aims at wherever the far element is at this instant and
 * keeps re-aiming, which is right for something being carried by a finger and wrong for something
 * crossing between two pages: it has no end of its own, so while anything moves the target — a list
 * scrolling, a header collapsing — the picture follows it around the screen instead of landing. A
 * tween ends when it says it will, and ending exactly when the turn does is what lets [MetroNavHost]
 * hold the screen still for precisely as long as there is something in the air. Pass the same
 * duration here as to `metroTurnstile` if you change one.
 */
@Composable
fun Modifier.metroContinuum(key: Any, durationMillis: Int = MetroTurnstileMillis): Modifier {
    val shared = LocalMetroSharedScope.current ?: return this
    val anim = LocalMetroAnimScope.current ?: return this
    val bounds = remember(durationMillis) { BoundsTransform { _, _ -> tween(durationMillis) } }
    return with(shared) {
        this@metroContinuum.sharedBounds(
            sharedContentState = rememberSharedContentState(key = key),
            animatedVisibilityScope = anim,
            boundsTransform = bounds
        )
    }
}

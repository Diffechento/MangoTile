# Changelog

## 1.0.2

**Gestures continue instead of restarting.** The one defect behind all of this: `animate(from, to,
initialVelocity, spec)` accepts a velocity and a `tween` *ignores* it — only springs and decays read
it — so every settle in the framework threw away the finger's speed at the moment the finger lifted
and began again on its easing curve's own starting slope. A discontinuity in speed at exactly the
instant the hand is judging the result is what gets reported as motion feeling rough, and it was in
the swipe, the dismiss, the collapsing header and the panorama's snap alike.

Everything that follows a drag now ends on `MetroSettleSpring`, handed the velocity the gesture left
behind, and it is **critically damped**: a spring that cannot overshoot is an ease-out, so nothing
bounces and the WP8 character is unchanged. The visible difference is confined to the first few
frames — which are the ones that were wrong.

- `MetroSettleSpring` and `MetroSnapSpring` are the vocabulary, with `metroSettleSpring(threshold)`
  for anything measured in 0f..1f rather than in pixels. `visibilityThreshold` is a pixel or half of
  one on all of them: the runtime default (0.01) is for fractions and leaves a spring creeping
  invisibly for hundreds of milliseconds, during which a scrolling surface still calls itself
  scrolling — see the next entry for what that costs.
- The panorama's snap was the worst of it — a deliberately brisk decay brought a flick almost to a
  halt and 420ms of `tween` then took the remainder from rest, so a thrown section arrived twice.

**A pager that is still animating takes the next gesture whichever way it goes**, so the invisible
tail of a snap is not cosmetic. While `isScrollInProgress` is true, Compose skips the touch slop for
that surface — `shouldAwaitTouchSlop = { !startDragImmediately() }`, deliberately, so a moving list
can be caught — and the slop is the *only* place the orientation lock arbitrates direction. The
panorama therefore swallowed vertical drags for as long as its snap ran.

Measured on a Pixel 7, a 120ms flick with the runtime default threshold: the pager reported itself
scrolling for **780ms**, roughly half of it after the panorama had visibly stopped, and a vertical
flick landing in that window dragged it **0.50 of a section sideways and changed section** while the
list under the finger did not move. The same gesture 800ms later moved the panorama by exactly zero.
`MetroSnapSpring` now carries the pixel threshold `PagerDefaults.flingBehavior` puts on its own
default snap; the window is 520ms, of which the finger owns 120, and it now ends when the movement
stops being visible rather than hundreds of milliseconds later. What remains is catching a panorama
that is genuinely still gliding, which is the behaviour that was asked for.
- A **committed** swipe leaves on a linear tween whose length is derived from the finger's own speed,
  because linear is the only easing whose first frame can be made to match the hand exactly, and
  because content leaving the screen should hold its speed rather than decelerate at the edge.

**One detector for both axes** — `Modifier.metroDrag(swipe, dismiss)`. Stacking `metroSwipe` and
`metroDismissDown` on one element does not work as well as it reads: each waits for the touch slop in
its own direction and neither knows the other is there, so a drag 30° off the horizontal crosses both
thresholds and the element goes sideways and downward at once — or the first detector to claim the
pointer wins and the gesture that fires is not the one that was made. On a player, where sideways is
"next track" and downward is "put this away", that is the whole gesture surface behaving
unpredictably at the angles a thumb actually produces. The axis is now locked once, to whichever
component was larger when the slop was crossed, and every later frame goes to that axis alone. An
axis with nothing to drive is left unconsumed, so a list underneath still scrolls; anything a child
consumes first ends the gesture at once.

**A rising page a finger can hold anywhere** — `MetroRisingPageState`, `rememberMetroRisingPage`,
`Modifier.metroRiseDrag`, and a `MetroRisingPage` overload that takes the state. With a boolean, the
pull and the rise are two different movements: the strip is nudged under the thumb, a threshold
decides, and a canned animation then plays from wherever the page happens to be. The hand feels one
thing and the eye then watches another. Here the pull *is* the rise — `progress` is the page's
position and the drag writes it directly — and letting go only finishes a movement already underway,
from the speed the hand had. The app's own boolean stays the truth for taps, Back and widgets; the
drag reports back through `onOpenChange`. The boolean overload is untouched.

**A page dragged between its neighbours** — `MetroPageSwipeState`, `rememberMetroPageSwipe`,
`Modifier.metroPageSwipe`, and a `metroRiseDrag` overload that takes the pager so a player can be paged
sideways and pushed away downward through one detector. `MetroSwipeState` moves a page under the finger
and then either brings it home or throws it out and flies a replacement in; either way the change is a
separate movement the hand is no longer part of. Here the neighbours are laid out beside the current
page and everything travels together, so letting go finishes a movement already made and what arrives
is what was visibly coming.

**Pages are addressed by an index the caller owns, and that is the whole design.** State that a gesture
commits to does not arrive in the same frame — a media session answers a skip a frame or several later —
so anything that finishes the animation and *then* resets the offset shows one frame of the wrong page.
Ask `offsetForSlot(-1/0/+1)` where to draw each page; the caller reports its index and the indices either
side every composition, a committed gesture remembers the index it asked for *and* the direction, and
when that index arrives the offset is given back exactly one slot in a `SideEffect` — before the frame is
drawn. Slots rather than arithmetic on the index, because "the next page" is not always the index plus
one: a queue on repeat answers the last track's next with the first one. There is a timeout for the page
that never comes, so a refused skip cannot leave the pages sitting off-centre with nothing in flight.

**A row of a long list is picked up by holding it** — `MetroReorderState`, `rememberMetroReorder`,
`Modifier.metroReorderRow`. The hold lifts the row under the finger, dragging carries it a place at a
time, and the list creeps when the row is held against either end. Let go without having moved it and
`onHeldStill` fires, so the same press still opens the context menu a hold has always opened: one
gesture, two outcomes, decided by whether the hand moved, and nothing drawn on screen to explain it.

Three things in it are the whole of why it works, and each was a defect first:

- **The detector is keyed on the state, never on the row's index.** The index is exactly what a
  committed step changes, so keying on it tears the handler down mid-drag — and the coroutine that
  would have put the row back down is cancelled with it, leaving the row lifted over its neighbours
  with no finger holding it. `drop()` is in a `finally` for the same reason.
- **The movement is read before the pointer is consumed.** `positionChange()` answers zero for a
  change that is already consumed, which turns a whole drag into a row that lifts and then refuses to
  move — with every event still arriving, so nothing in the log looks wrong.
- **Events are taken on the `Initial` pass.** The row's own clickable is *inside* this node and the
  Main pass asks the innermost node first, so it would see the release before this could consume it
  and a row put down would also be a row tapped. Consuming on Initial also takes the drag off the
  list, which would otherwise scroll under the finger.

Displacement is one number, not two: a step gives a row's height back the moment it commits, because
the caller's list has reordered and the row's own slot has moved by exactly that. Keeping a second
running total and adding the two draws the row a row lower per step. Moves are committed *as they
happen* rather than at the end, so the caller's order is the only one in play — which means a list
behind an asynchronous player has to keep a local copy and send the command, rather than waiting to be
told what it already knows.

**A row is swiped out of a list** — `MetroRowDismissState`, `rememberMetroRowDismiss`,
`Modifier.metroRowDismiss`. Not `MetroSwipeState`, which is a *page* moving between its neighbours and
brings a replacement in from the far edge, and not `MetroDismissState`, which is a page pushed away
along the other axis: here the row itself leaves and the list closes over the gap. Either direction
removes, because a row has no meaning attached to left or right and insisting on one of them makes half
of every attempt fail silently.

It goes through the same one-axis-at-a-time detector as everything else, which is what makes it safe in a
list that is *also* scrolled and *also* has rows held and dragged in it: a vertical drag is dropped
unconsumed so the list still scrolls, a hold is left alone because this only claims the pointer once the
slop has been crossed sideways, and a lifted row consumes on the Initial pass so a reorder in progress is
never also a dismissal. `progress` is a fraction of the *commit point* rather than of the row's width, so
a reveal that fades in with it is at full strength exactly where letting go would remove the row instead
of a third of the way to it. A committed row is seen off the edge at the finger's own speed and
`onDismiss` fires when it gets there; the offset goes back to zero at that moment, so a row that is *not*
removed comes back rather than sitting invisibly off screen.

**A rising page can be stacked over another rising page** — `metroRiseDrag(state, pager, upward = …)`.
An upward drag on a page that has nowhere left to rise is a request for the page above it (a queue over
a player); while it is still on its way up, the same drag is that rise continuing. One detector decides,
by the sign of the travel that crossed the slop, and the chosen page keeps every later frame of the
gesture — so reversing mid-drag puts it back rather than handing the movement to its neighbour. An axis
may now decline a gesture (`MetroDragAxis.onGrab` answers a Boolean), which leaves the pointer
unconsumed exactly as a null axis does: a surface that is only half live must not swallow the direction
it has nothing to do with.

`fromHeight = 0.dp` is now the second, honest case for `rememberMetroRisingPage`: a page that comes out
of the bottom edge of the screen rather than out of a strip has no strip's navigation inset to inherit,
and adding it left a band of the page's own top showing along the bottom edge for the last frames of
every drop.

**Two more icons** — `MetroIcon.ChevronUp` and `ChevronDown`, for the mark that says there is a page
above or below this one. Wide and shallow rather than at 45°: an arrow reads as a button that takes you
somewhere, and this is the edge of something you can pull.

**Content follows the finger, and resists where resistance is an answer.**

- A swipe tracks the hand exactly and gives progressively less as it approaches what it may travel.
  `canGoNext`/`canGoPrevious` say what is reachable and are asked *while the finger is down*: the end
  of a queue answers a swipe by barely moving, instead of a full flight out of the frame and a track
  that never changes. They are asked again before a commit, so a hard flick at the end of the queue is
  not a track change either.
- `MetroDismissState` followed the finger at a flat half speed for its whole travel, on the reasoning
  that a page tracking the finger exactly promises a dismissal it might not perform. True of the end
  of the gesture and wrong at the start, where halving everything reads as dragging through treacle.
  It is one-to-one as far as the decision is still open and resists past that, so the threshold is
  told to the hand by feel. `followFraction` now defaults to 1f, and `maxTravelFraction` is where the
  resistance asymptotes.

## 1.0.1

**A long list can be arranged by anything, not only by its initial letter.** `MetroListSort` is that
arrangement as data — a name, a comparator, and what the header above each run of rows says — so a
screen can hold several and hand the list whichever is in force. `MetroListSort.alphabetical` is the
A–Z one this list has always had, spelled that way so it can sit in a menu beside "by date added" or
"by length".

- A group header is a **label**, not a character. One character is the letter tile exactly as before;
  a word ("july 2026", "10+ plays") keeps the tile's height and grows sideways at a smaller size. An
  empty label draws no header, which is a flat list with the arrangement still doing the ordering.
- Groups are **runs**: consecutive rows sharing a label. A header that disagrees with its comparator
  therefore shows a repeated heading rather than quietly moving rows out of the order the comparator
  asked for.
- **Every arrangement zooms out.** A tap on a header still opens the whole set of groups over the
  screen and jumps to the one picked — the alphabet by name, the months by date, the bands by length.
  Letters keep the phone's grid of squares; words are a column of blocks the width of their own text,
  and it scrolls, because a library added to over four years has fifty months in it.
- `jumpDomain` now only says what that screen shows *besides* what is there: given the labels present,
  it answers with the whole domain and the empty ones are dimmed and inert. Null — the default — means
  the groups themselves, which is the right answer for an open-ended arrangement: there is no set of
  all months, only the months your files were added in.
- Pass `sorts` and a **hold** on a header unrolls them out of that header — `MetroContextMenu`, the
  same menu a held row gets, so the arrangements come out of the thing you pressed — and
  `onSortSelected` gets the index picked. Tap and hold keep their meanings on every arrangement: a tap
  that meant "zoom out" under one heading and "choose an arrangement" under the next would teach the
  user one thing and then do another.

The `group: (T) -> Char` overload is untouched and behaves as it did, rows included — it delegates to
the same implementation with a stable sort by letter.

**A header can roll away with the list under it** — `MetroCollapse`, `Modifier.metroCollapsingHeader`
and `Modifier.metroCollapseOnScroll`. It gives up its *height*, so what follows moves up and grows into
the space rather than being covered by it: a list under it gets taller, which is the only reason to take
the space at all. The panorama's title does this by default (`collapsingTitle = false` to stop it) and any
page can do it in three lines — one state, one modifier on the header, one on an ancestor of the list.

- The header goes **first**, before the list scrolls, and comes back **last**, once the list has nothing
  left to give. A header that waited for the list to hit its end would need a second gesture to reappear.
- A fling that reaches the top of the list carries on into the header, so throwing a list back to its
  start brings the header with it instead of stopping a hair short.
- Nothing is clipped by the modifier, so **clip whatever contains the header** at the edge it should
  vanish behind — the panorama clips at the status bar. `overhang` lets it travel further than its own
  height, for ink drawn outside a trimmed line box that would otherwise stay as a sliver at that edge.

**A top banner no longer swallows the screen while it is up.** It lived in a screen-sized popup, so for
the two seconds a "volume" or "added to queue" strip was showing, every tap went into a transparent
window and nothing underneath answered — which is the opposite of what `MetroTopBanner`'s own
documentation promised. The popup is now the size of the banner and the *window* is what slides in from
above, so the page below stays live.

**The panorama spends 68dp less on its own title.** Measured on a 1080x2400 screen: the first row of a
section used to start 788px down and now starts at 609px, which is a row and a half of library given
back. Four things account for it, and `titleTopPadding`, `titleGap` and `headerGap` are now parameters
if a caller wants the air back:

- **The status bar is cleared by inset, not by a hand-picked 52dp.** A phone whose bar or cutout is
  taller than that was being overlapped; one whose bar is short was paying for the worst case.
- **The blank the font reserves above the capitals and below the baseline is trimmed** — 30dp and 16dp
  of it at 108sp — by shortening the title's layout and placing the text into the space. Not by
  `lineHeight`: that shrinks the leading *between* lines, and this is the ascent and descent themselves,
  so asking for less simply gets them back. (Nothing is clipped; the text is placed at a negative offset
  and neither the column nor the title's box clips.)
- **`includeFontPadding = false`** on the title, the overline and the section headers.
- The gap under the title is 6dp instead of 28dp, and under the headers 12dp instead of 18dp. The
  phone's own panorama tucks its header under the title; the space belongs to the section.

**`MetroContextMenu` takes `selectedItem`**, drawing that entry in accent: the menu choosing between
states rather than offering actions, which is what a list's arrangements are. A menu of four ways to
sort that says nothing about which one you are looking at makes the user pick one to find out.

**The zoom-out is translucent too** — the list you pulled back from stays faintly behind its groups, so
it reads as the same page zoomed out rather than as a different screen you were sent to. WP8's own was
opaque; on a library of four buckets, opaque is four blocks floating in a void.

**A context menu's sheet is now slightly translucent** (`sheetAlpha`, 0.9 by default; pass 1f for the phone's own
flat opaque one), so the page stays faintly legible through it and the menu reads as something laid over
where you were. The labels stay fully opaque — the paper is translucent, the ink is not.

**And its sheet is as wide as its widest label, never narrower than the anchor.** A row keeps the
full-width sheet it always had; something small — a 44dp letter tile — gets a menu you can read
instead of a column of broken words.

`:sample`'s collection screen now offers three arrangements of the same forty artists — by name, by
plays and by date added — because a heading a list can be arranged by is worth showing rather than
describing: the letters, a closed set of four bands with the empty ones dimmed, and an open-ended
column of months that scrolls. The numbers behind the last two are derived from the names; there is no
library behind the sample.

Published to **Maven Central** as `io.github.diffechento:metro:1.0.1`, with sources and javadoc jars
and detached PGP signatures. 1.0.0 keeps working: every addition here is a new parameter with a
default or a new declaration, so a call written against 1.0.0 compiles unchanged.

## 1.0.0

First release. The API is considered stable from here: parameters get appended, not reordered
or renamed, so a call written against 1.0.0 keeps compiling.

**Pages and navigation** — `MetroPage` with the measured six-size title, `MetroNavHost` +
`MetroBackStack` with per-destination state retention, `metroTurnstile()`, continuum
(`Modifier.metroContinuum`), `Pivot` / `MetroPivot`.

**Panorama** — `MetroPanorama` in two forms: the lambda overload for a fixed set of sections,
and the list overload for the phone's panorama proper — circular, lazy, with the next header
peeking in and both parallax layers made periodic so the wrap is seamless. `backgroundTiles`
for backdrops that cannot be tiled.

**Lists** — `MetroLongList` (the LongListSelector, with the zoom-out jump grid over Latin and
Cyrillic), `ListRow`, `SettingRow`, `MetroBottomInset`.

**Controls** — `Tile`, `MetroButton`, `MetroToggle`, `MetroSlider`, `MetroCheckBox`,
`MetroRadio`, `MetroTextBox`, `MetroSuggestBox`, `MetroProgressBar` / `Dots` / `Ring`,
`AppBar` / `AppBarButton`, `TransportButton`, `MetroIcon` / `MetroLineIcon` / `CenteredGlyph`.

**Panels and banners** — `MetroContextMenu` with its two-beat unroll, `MetroMessageBox`,
`MetroInputBox`, `MetroBottomBar`, `MetroRisingPage`, `MetroTopBanner`, `MetroVolumeBanner`.

**Motion** — `Modifier.metroSlideIn`, `Modifier.metroGrowIn`, `MetroSwap`, `MetroCrossfade`,
`Modifier.metroSwipe`, `Modifier.metroDismissDown`.

**Theming** — `MetroTheme(accent, dark, background)`, `MetroAccents` (all twenty WP8 accents),
`LocalMetroColors`, derived greys so a light custom background cannot end up with white text.

**Widgets** — `MetroWidgetProvider` / `MetroTile` for WP8-style tiles on the Android home screen.

**Insets** — `MetroPage`, `AppBar` and `MetroBottomBar` fill the window and inset their own
content; lists carry the navigation-bar inset inside their own scroll.

Published to **Maven Central** as `io.github.diffechento:metro:1.0.0`, with sources and javadoc
jars and detached PGP signatures.

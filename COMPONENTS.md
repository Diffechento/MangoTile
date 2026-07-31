# MangoTile components

Everything the library gives you, grouped by what it is for. `io.github.diffechento:metro:1.0.0`; see
the [README](README.md) for installing it and running the sample.

Names in `code` are the public API. From 1.0.0 the rule is additive — parameters get appended with
defaults, never inserted, reordered or renamed — so anything documented here keeps working.

- [Pages, navigation and layout](#pages-navigation-and-layout)
- [Controls](#controls)
- [Panels and banners](#panels-and-banners)
- [The signature motion](#the-signature-motion)
- [Theming, backdrop, insets](#theming-backdrop-insets)
- [Home-screen widgets](#home-screen-widgets)
- [Caveats](#caveats)
- [Not there yet](#not-there-yet)

## Examples

```kotlin
MetroPanorama(title = "collection", overline = "MUSIC + VIDEOS", background = { Wallpaper() }) {
    PanoramaSection("history") {
        Tile("Daft Punk", Metro.Accent, 220.dp, 108.dp)
    }
    PanoramaSection("new") {
        Tile("Radiohead", Metro.Red, 220.dp, 108.dp)
    }
}
```

A home-screen tile is a five-line subclass:

```kotlin
class FavoritesTile : MetroWidgetProvider() {
    override fun tile(context: Context) =
        MetroTile(0xFF008A00.toInt(), "★", "favorites", targetScreen = "Start")
}
```

## Pages, navigation and layout

- **`MetroPage`** — the standard header: small overline over a huge Segoe-Light title. The title
  takes the largest of six sizes that actually fits the window, measured rather than guessed, so a
  long name reads at 40sp instead of running off the edge with its last letters cut in half.
- **`MetroNavHost` + `MetroBackStack`** — a real back stack with typed destinations, system Back,
  and the turnstile applied automatically (reversed when popping). Each destination keeps its
  scroll offsets, pager pages and `rememberSaveable` state while it is covered and gets them back
  on the way out, and drops them when it leaves the stack.
- **`Pivot`** — the header strip on its own; **`MetroPivot`** wires the headers to a swipeable pager.
- **`MetroPanorama`** — real three-layer parallax. Background, title and content all move at
  different speeds as you drag, and letting go settles on a section boundary rather than wherever
  your finger stopped. Drop your own background into a slot.

  Hand it a `List<MetroPanoramaSection>` instead of a lambda and you get the phone's panorama
  proper: **circular** (past the last section comes the first, so nothing is ever more than one
  swipe away — which is what makes it reasonable to keep settings at the far end), **lazy** (only
  the sections near the viewport are composed, so a dozen lists cost a screenful), and **showing
  you what is next** — sections sit a `sectionPeek` narrower than the window so the beginning of
  the next header always leans in, dimmer the further out it is. Both parallax layers are made
  periodic over one cycle so the loop is seamless. `backgroundTiles = false` for a backdrop that
  cannot be tiled — a photograph, an album cover: two copies of one of those meet in a hard
  vertical line, so it is drawn once, wider than the window, and panned inside its own overhang
  instead. Keep the list's *size* stable while it is on screen; page numbers are read modulo the
  section count, so inserting a section under the user renumbers every page.
- **`MetroLongList`** — the LongListSelector: alphabetical groups plus the zoom-out jump grid.
  `filledGroupHeaders = false` drops the accent square and leaves the bare letter, for lists whose
  rows are themselves tiles. The grid opens in a popup over the whole screen, so the list itself
  need not be screen-wide — one inside a panorama section still gets a full alphabet.
  `metroGroupChar` buckets Latin **and** Cyrillic, and the grid shows whichever alphabets the list
  actually uses (both, and it switches to seven narrower tiles so sixty letters still fit).
- **`MetroListSort`** — the same list arranged by anything else: a name, a comparator, and the label
  the header above each run of rows carries ("july 2026", "10+ plays"; `""` for no header at all).
  `MetroListSort.alphabetical` is the A–Z arrangement written as one of these. Every arrangement zooms
  out on a tap — letters as the grid of squares, words as a scrolling column of blocks — and
  `jumpDomain` is only how a closed domain (the alphabet, four length bands) gets its empty buckets
  shown dimmed; the default is the groups the list actually has. Pass a list of arrangements as
  `sorts` and a **hold** on a header unrolls them out of it as a `MetroContextMenu`, the one in force
  in accent.
- **`ListRow`, `SettingRow`** — long-list and settings rows.
- **`MetroBottomInset`** — the gap a list needs at its end so its last row clears the gesture pill.
- **`MetroCollapse`** — a header that rolls away with the list under it and comes back when that list is
  dragged past its top. It gives up its height, so the list grows into the space; `metroCollapsingHeader`
  on the header, `metroCollapseOnScroll` on an ancestor of the list, and a clip on whatever edge it
  should vanish behind. The panorama's title uses it (`collapsingTitle`).

## Controls

- **`Tile`** — flat coloured tile, any size, glyph plus label.
- **`MetroButton`** — bordered, inverts on press; `filled` accent variant.
- **`MetroToggle`** — the WP8 switch with a sliding thumb.
- **`MetroSlider`** (settings weight by default, `trackHeight`/`thumbSize` for a now-playing
  hairline), **`MetroProgressBar`**, **`MetroProgressDots`**, **`MetroProgressRing`**,
  **`MetroTextBox`**, **`MetroCheckBox`**, **`MetroRadio`**.
- **`MetroSuggestBox`** — a text box that offers the values already in use, in a popup under it
  that keeps the keyboard up. For fields where free text is necessary but agreement matters more:
  a genre typed from memory is how a library ends up with "Electro", "electro" and "Electronic" as
  three different things.
- **`AppBar` / `AppBarButton`** — the bottom bar with round buttons.
- **`TransportButton`** — media transport, no caption: ringed (`ringSize`) for play and skip, bare
  for the toggles, accent-tinted while a toggle is on.
- **`MetroIcon` / `MetroLineIcon`** — the player's icon set drawn as paths, not typed: star
  (outline and solid), shuffle, repeat, repeat-one, speaker and muted, and the transport shapes at
  one shared height. Android's font fallback renders ♥ ⇄ ↻ ❚❚ at whatever weight and ink height it
  likes, which is not a look you can build on.
- **`CenteredGlyph`** — one glyph centred on its ink rather than its layout box, for symbols that
  arrive from a fallback font with their own side bearings.

## Panels and banners

- **`MetroContextMenu`**, **`MetroMessageBox`**, **`MetroInputBox`** — long-press menu and modal
  panels. `disabledItems` greys an action that does not apply instead of hiding it, so the menu
  keeps the same shape every time you open it. The context menu opens in two beats, as the phone
  does: the held item lights up and a hairline spreads from the point you touched to the item's
  edges, and only once it gets there does the sheet unroll out of it — labels squashing with the
  sheet, so it reads as one flat thing being unrolled. `selectedItem` puts one entry in accent, for a
  menu choosing between states rather than offering actions. The sheet is as wide as its widest label
  and never narrower than the anchor, so it works held off a full-width row or off a 44dp tile.
- **`MetroBottomBar`** — a strip along the bottom that comes and goes, handing its space back *as*
  it leaves rather than all at once at the end. **`MetroRisingPage`** is its other half: a page
  that comes up out of that strip and drops back into it.
- **`MetroTopBanner`** — the strip the phone drops over the top of everything to say something
  changed, and which leaves by itself. It covers the status bar rather than starting under it, and
  the system's own clock and icons still draw over any window, so they stay readable.
  **`MetroVolumeBanner`** is the volume one built on it: speaker, level, bar, and a slot for what
  is playing. Android has no way to suppress its own volume panel, so this replaces it only if the
  activity consumes the volume keys first (`onKeyDown` returning true) — inside your app you get
  this, on the lock screen you get Android's.

## The signature motion

- **`Modifier.metroSlideIn(key)`** — flies an element in from one edge when `key` changes,
  travelling a whole element-width from off-screen and decelerating into place. What the player's
  artwork does when the track changes, measured off a Lumia doing it. Unclipped on purpose: the
  incoming cover arriving over its neighbours is the effect.
- **`Modifier.metroGrowIn(key)`** — the other reopening: squeezed flat against an edge, then
  stretched back out. Both ignore the first composition, so neither fights the page transition.
- **`MetroSwap(target, delayMillis)`** — turns words over in place in the phone's three beats: fade
  out, a moment with the line **empty**, then fade in. Not a cross-fade — the empty beat is what
  makes it read as words being replaced rather than blurring into each other. Stagger the delay
  down a stack of lines and they turn over in sequence, which is how the phone swaps artist, album
  and track name.
- **`MetroCrossfade`** — turns one picture into another without a blank beat between them.
  `MetroSwap`'s opposite number, for backdrops: the outgoing image stays until the incoming one has
  faded up over it, both drifting slightly so the change has a direction. A blank frame in a
  full-bleed image is a black flash, which is what a track change looks like without this.
- **Continuum** — tap a list item and its title *flows* into the next screen's big header, then
  flows back on Back. Put `Modifier.metroContinuum(key)` on both elements; `MetroNavHost` provides
  the scopes. The key names one object moving between screens, so two elements alive at the same
  time under one key are laid out on top of each other. Only works between destinations of that
  host — an overlay such as a `MetroRisingPage` is outside it and has nothing to pair with.
- **`Modifier.metroSwipe` + `rememberMetroSwipe`** — swipe sideways to move to the next or previous
  item. Committed swipes are seen through rather than undone: the content carries on out of the
  frame at the speed the finger left it and the next arrives from the far edge. Read `state.offset`
  in a `graphicsLayer` and let layers follow at different fractions for a sense of depth.
- **`Modifier.metroDismissDown` + `rememberMetroDismiss`** — push a page down to send it away, the
  counterpart of `MetroRisingPage`. The page follows the finger at a damped fraction of its speed
  and the callback fires as the finger lifts, so the dismissal and the drop overlap into one
  movement.
- **`metroTurnstile()`** — a page-entry transition for `AnimatedContent`.

## Theming, backdrop, insets

**`MetroTheme(accent, dark, background) { }`** installs a palette through `LocalMetroColors`; read
it back with `MetroTheme.colors`. `MetroAccents` is the list of twenty accents WP8 shipped.
`background` goes past what the phone allowed, for an app that wants to offer its own: give it a
colour and the greys and the text colour are derived from how light it is, so a pale background
cannot end up with white text on it. Skip the wrapper entirely and everything falls back to the
dark palette, so it is opt-in.

**`MetroBackdrop { yourBackground() }`** puts one background behind every page in the subtree.
Pages inside it leave their own flat background off, so it stays put while pages come and go over
it instead of each screen painting its own wallpaper.

**Insets** — `MetroPage`, `AppBar` and `MetroBottomBar` fill the window and inset their own
*content*, so backgrounds reach the bottom edge of the screen while nothing you press ends up
under the gesture pill. Do not wrap them in `navigationBarsPadding()`; that is what leaves a strip
of bare background under a full-bleed backdrop. The circular `MetroPanorama` is the exception,
because its sections are lists: it does not inset them, and the list carries the inset inside its
own scroll (`MetroBottomInset`, applied for you by `MetroLongList`) so rows reach the bottom edge
and the last one still stops above the pill. Ask for genuinely transparent system bars in your
activity — `enableEdgeToEdge()`'s default puts a scrim behind the navigation bar, which reads as a
pale strip on a dark page.

## Home-screen widgets

**`MetroWidgetProvider`** — an abstract base for WP8-style tiles on the actual Android home screen.
Subclass it, return a `MetroTile(color, glyph, label, target)`, register one receiver. It handles
rendering, tap-to-open, and optional forced refresh via `AlarmManager` for "live" tiles.

## Caveats

- **The font.** [Selawik](https://github.com/microsoft/Selawik), Microsoft's open Segoe substitute
  with matched metrics, is bundled in `res/font` in three weights — light, semilight and regular,
  about 44 KB each, so ~130 KB of any APK built on this. None of them is subset; trimming them to
  the glyphs you actually draw would reclaim most of that. Note the Reserved Font Name if you do:
  the result may not be called Selawik.
- **Square tile corners.** On Android 11 and below, widget tiles are perfectly square. On Android
  12+ the launcher force-rounds every widget and apps cannot turn that off — a third-party launcher
  with corner radius 0 is the only way back to sharp corners.
- **"Live" tiles.** The system refreshes widgets at most every 30 minutes. The live tile beats that
  with a self-rescheduling alarm, but Android throttles it when the screen is off. Truly frequent
  updates want WorkManager or a foreground service.
- **Continuum and shared-element transitions are fiddly.** They work, and you should still expect
  to tune the easing to your own taste.
- **`metroTilt` still uses the deprecated `composed {}`** rather than `Modifier.Node`.

## Not there yet

Subset the font. A minified release build of the sample. The `Modifier.Node` rewrite of
`metroTilt`. A circular panorama that allows sections of differing widths — today it needs one
fixed width, because the wrap arithmetic works off a page size rather than measured offsets. More
controls: a search box, date and time pickers.

# Changelog

## 1.0.1 (not published — mavenLocal only)

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

**Its sheet is now slightly translucent** (`sheetAlpha`, 0.9 by default; pass 1f for the phone's own
flat opaque one), so the page stays faintly legible through it and the menu reads as something laid over
where you were. The labels stay fully opaque — the paper is translucent, the ink is not.

**And its sheet is as wide as its widest label, never narrower than the anchor.** A row keeps the
full-width sheet it always had; something small — a 44dp letter tile — gets a menu you can read
instead of a column of broken words.

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

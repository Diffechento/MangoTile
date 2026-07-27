# Changelog

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

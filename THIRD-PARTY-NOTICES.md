# Third-party notices

MetroCompose itself is under the MIT licence — see [LICENSE](LICENSE). The material below belongs
to other people and keeps its own terms.

## Bundled fonts — Selawik

This library bundles the Selawik typeface in three weights, as
`metro/src/main/res/font/selawik_light.ttf`, `selawik_semilight.ttf` and `selawik_regular.ttf`.
Anything built on MetroCompose therefore ships these fonts too.

> Copyright 2015, Microsoft Corporation (www.microsoft.com), with Reserved Font Name Selawik.
> All Rights Reserved. Selawik is a trademark of Microsoft Corporation in the United States
> and/or other countries.

Selawik is licensed under the **SIL Open Font License, Version 1.1**, whose full text — as shipped
by the Selawik project — is reproduced in [licenses/Selawik-OFL-1.1.txt](licenses/Selawik-OFL-1.1.txt).
Upstream: <https://github.com/microsoft/Selawik>

The OFL explicitly permits bundling the font with software under any licence, including MIT, so
long as the copyright notice and the licence travel with it. Two things it does require:

- the licence file stays with the fonts, which is what `licenses/` is for;
- the **Reserved Font Name** is respected. Subsetting these files to the glyphs you actually draw
  is a modification, and the result may not be called Selawik.

## Dependencies

Resolved from Maven at build time rather than vendored here. Listed for attribution only.

| Dependency | Licence | Copyright |
| --- | --- | --- |
| `androidx.compose.*` | Apache-2.0 | The Android Open Source Project |
| `androidx.activity` | Apache-2.0 | The Android Open Source Project |
| `androidx.core` | Apache-2.0 | The Android Open Source Project |
| `org.jetbrains.kotlin.*` | Apache-2.0 | JetBrains s.r.o. and contributors |

Apache-2.0 imposes no conditions on a work that merely depends on it beyond keeping its notices,
so an MIT library over an Apache-2.0 stack is unremarkable.

## Trademarks

MetroCompose is not affiliated with Microsoft. "Windows Phone", "Metro" and "Segoe" are
Microsoft's. This project is an homage to a design language, written from scratch.

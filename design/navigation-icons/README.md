# Navigation icons

24 × 24 monochrome SVGs; `currentColor` controls the foreground color.

| Entry | Unselected | Selected |
| --- | --- | --- |
| Home | `home-outlined.svg` (new) | `home-filled.svg` (MIUIX Home) |
| Bookshelf | `bookshelf-outlined.svg` (MIUIX Notes) | `bookshelf-filled.svg` (MIUIX NotesFill) |
| Profile | `profile-outlined.svg` (MIUIX Contacts) | `profile-filled.svg` (new) |

The source geometry comes from **MIUIX 0.9.4 Regular**. Home outlined uses an inset,
rounded stroke of the original silhouette to preserve its optical bounds. Profile
filled keeps the original outer head and torso contours and removes their interior
cutouts. The stock exports are normalized to a 24 × 24 viewBox.

Run `python tools/generate_navigation_icons.py` from the repository root after editing
the SVGs. This regenerates the two custom Android VectorDrawables and the SVG previews.
The app uses MIUIX directly for the four stock variants.

`preview.svg` compares both states. Open `preview-animated.svg` in a browser to see
a looping crossfade; it respects `prefers-reduced-motion`. `preview-fill-steps.svg`
shows five deterministic transition levels. Android does not play SVG CSS animations,
so the liquid glass bar uses a persistent Painter to crossfade the outlined/filled
native vectors. Painter nodes remain mounted at both endpoints. Pager/drag progress is
blended continuously during press/release, without switching sources at a threshold.
The other navigation bar styles switch their vector with the selected page.

## Attribution

Derived from [compose-miuix-ui/miuix](https://github.com/compose-miuix-ui/miuix),
`miuix-icons/src/commonMain/kotlin/top/yukonga/miuix/kmp/icon/extended/`:
Home.kt, Contacts.kt, Notes.kt, NotesFill.kt.

Copyright 2025–2026, compose-miuix-ui contributors. Licensed under Apache-2.0;
see [LICENSE-MIUIX](LICENSE-MIUIX). The Home outlined and Profile filled derivatives
were added for JMComicX. Other project code retains the repository license.

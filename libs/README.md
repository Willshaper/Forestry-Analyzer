# libs/

The build compiles against a few third-party jars that are **not** committed to this
repository (they're other people's code, and keeping them out keeps the repo small).

Drop the following jars into this folder before building. The versions below are what the
project is currently built against; newer compatible versions usually work too.

| Jar | Needed for | Source |
| --- | --- | --- |
| `Forestry-4.11.26.jar` | **Required.** The `forestry.api.*` genetics API the whole mod reads. | Forestry (GTNH) |
| `chromatictooltips-1.0.18.jar` | Compile-time only. The mixin that hides the normal tooltip targets ChromaticTooltips. | ChromaticTooltips |
| `unimixins-all-1.7.10-0.1.23.jar` | Compile-time only. Mixin runtime + the gtnhmixins late-mixin API. | UniMixins |

At **runtime** only Forestry is required. ChromaticTooltips and UniMixins are optional —
the tooltip-hiding mixin only applies when ChromaticTooltips is actually present.

The simplest way to get exact-matching jars is to copy them out of your GT New Horizons
instance's `mods/` folder.

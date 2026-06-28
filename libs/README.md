# libs/

The build compiles against a few third-party jars that aren't committed here. They're other
people's code, and keeping them out of the repo keeps it small.

Drop these jars into this folder before building. The versions listed are what the project is
built against; newer compatible versions usually work too.

| Jar | Needed for | Source |
| --- | --- | --- |
| `Forestry-4.11.26.jar` | Required. The `forestry.api.*` genetics API the mod reads. | Forestry (GTNH) |
| `chromatictooltips-1.0.18.jar` | Compile only. The tooltip-hiding mixin targets ChromaticTooltips. | ChromaticTooltips |
| `unimixins-all-1.7.10-0.1.23.jar` | Compile only. Mixin runtime plus the gtnhmixins late-mixin API. | UniMixins |

At runtime only Forestry is required. ChromaticTooltips and UniMixins are optional; the
tooltip-hiding mixin applies only when ChromaticTooltips is present.

The easiest way to get matching jars is to copy them out of your GT New Horizons instance's
`mods/` folder.

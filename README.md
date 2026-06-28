# Forestry Beealyzer as a Tooltip

A Minecraft **1.7.10** Forge client mod that shows a Forestry individual's full genome as a
compact table when you hover its item in a GUI — no need to put the bee/tree in an analyzer
first. Built for the GT New Horizons pack.

Hover a Forestry **bee** or **tree** item and hold the show key (default `Alt`), and a panel
appears with the active/inactive allele for every chromosome, laid out and labelled to match
Forestry's own Beealyzer / Treealyzer.

## Features

- **Bee and tree genomes** in a 3-column table (Trait / Active / Inactive), with curated row
  order and labels matching Forestry's analyzers.
- **Homozygous vs heterozygous** colouring: pure alleles in green, hybrid in red.
- **Pure-bred badge** when every chromosome is homozygous.
- **Breeding mutations** — both directions, colour-coded: *Breeds into* (what this species can
  produce) and *Made from* (what produces it), each with the breeding chance and any special
  conditions (e.g. "Requires &lt;block&gt; as a foundation").
- **Works over NotEnoughItems** — shows the panel for items in the NEI item panel too.
- **Hides the normal tooltip** while the panel is up (via a ChromaticTooltips mixin, with a
  mod-agnostic fallback) so the two don't overlap.
- **Copy raw NBT** of the hovered/held item to the clipboard.
- **Configurable** colours, panel anchor/offset/scale, which rows show, and more.

## Controls

All keys are rebindable in **Options → Controls → Bee NBT Viewer**:

| Action | Default |
| --- | --- |
| Show panel (hold) | `Alt` |
| Toggle panel permanently on/off | `O` |
| Toggle mutation hints | `M` |
| Copy raw NBT to clipboard | `N` |

Set `display.alwaysShow = true` in the config to skip the show key entirely.

## Configuration

A Forge config is generated at `config/beenbtviewer.cfg` on first run:

- `colors` — border, divider, background, match/mismatch, and text colours as ARGB hex.
- `display` — `alwaysShow`, `hideNormalTooltip`, `showPureBadge`, `showMutations`,
  `maxMutations`, `anchor` (SLOT / TOPLEFT / TOPRIGHT / BOTTOMLEFT / BOTTOMRIGHT),
  `offsetX`, `offsetY`, `scale`, and `hiddenRows`.

## Requirements

- Minecraft **1.7.10** with Forge `10.13.4.1614`
- **Forestry** (required)
- **NotEnoughItems** — optional (enables NEI item-panel support)
- **ChromaticTooltips** + **UniMixins** — optional (enables clean hiding of the normal tooltip)

## Building

This project uses [anatawa12's ForgeGradle 1.2](https://github.com/anatawa12/ForgeGradle/),
which requires **JDK 8** to build. The Gradle wrapper (Gradle 8.13) is included.

1. Put the dependency jars in `libs/` — see [libs/README.md](libs/README.md).
2. If your default JDK isn't 8, copy `gradle.properties.example` to `gradle.properties` and
   point `org.gradle.java.home` at a JDK 8 install.
3. Build:

   ```sh
   ./gradlew build
   ```

The reobfuscated mod jar lands in `build/libs/`.

## Credits

- Mod by **Whiterose**.
- Reads the **Forestry** genetics API. Integrates with **NotEnoughItems**,
  **ChromaticTooltips**, and **UniMixins** when present.

## License

[MIT](LICENSE).

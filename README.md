# Forestry Beealyzer as a Tooltip

A Minecraft 1.7.10 Forge client mod for the GT New Horizons pack. Hover a Forestry bee or tree
item in a GUI and hold a key (Alt by default); a panel shows its full genome the way Forestry's
Beealyzer and Treealyzer would, without putting the item in an analyzer first.

Each chromosome gets a row with its active and inactive allele. Pure (homozygous) alleles show
green, hybrid (heterozygous) ones show red.

## Features

- Bee and tree genomes in a three-column table (Trait / Active / Inactive). Row order and labels
  follow Forestry's own analyzers.
- For bees, the Temp and Humidity rows also show the species' preferred climate in parentheses
  (`Temp (Hot)`, `Humidity (Arid)`), like the analyzed Beealyzer.
- Header icons after `Trait`: a six-pointed star for a princess's or queen's stock (gold for
  pristine, gray for ignoble), and a green diamond when the bee is pure-bred (every chromosome
  homozygous).
- Breeding mutations in both directions, color-coded. "Breeds into" (aqua) lists what this
  species can produce; "Made from" (gold) lists what produces it. Each line shows the breeding
  chance and any special requirement, such as "Requires &lt;block&gt; as a foundation".
- Panel support over the NotEnoughItems item panel, not just real container slots.
- The normal tooltip hides while the panel is up, so the two don't overlap. This uses a
  ChromaticTooltips mixin, with a plain Forge fallback when CT isn't installed.
- Copy an item's raw NBT to the clipboard.
- Config for colors, panel position and scale, which rows to show, and more.

## Controls

Every key is rebindable under Options, Controls, "Bee NBT Viewer":

| Action | Default |
| --- | --- |
| Show panel (hold) | Alt |
| Toggle panel on permanently | O |
| Toggle mutation hints | M |
| Copy raw NBT | N |

To skip the show key, set `display.alwaysShow = true`. The mutation-hints toggle (M) is
remembered across restarts.

## Configuration

The config is written to `config/beenbtviewer.cfg` on first launch.

`colors` holds the border, divider, background, match/mismatch, and text colors as ARGB hex.
`display` holds `alwaysShow`, `hideNormalTooltip`, `showPureBadge`, `showMutations`,
`maxMutations`, `anchor` (SLOT, TOPLEFT, TOPRIGHT, BOTTOMLEFT, or BOTTOMRIGHT), `offsetX`,
`offsetY`, `scale`, and `hiddenRows`.

## Requirements

Minecraft 1.7.10 with Forge 10.13.4.1614. Forestry is required. NotEnoughItems,
ChromaticTooltips, and UniMixins are optional: NEI adds item-panel support, and CT with
UniMixins lets the mod hide the normal tooltip while the panel shows.

## Building

The project uses anatawa12's ForgeGradle 1.2, which needs JDK 8. The Gradle 8.13 wrapper is
included.

1. Put the dependency jars in `libs/`. See [libs/README.md](libs/README.md) for the list.
2. If your default JDK isn't 8, copy `gradle.properties.example` to `gradle.properties` and set
   `org.gradle.java.home` to a JDK 8 path.
3. Run `./gradlew build`.

The reobfuscated jar lands in `build/libs/`.

## Credits

Original idea and code by Whiterose. All later updates by Willshaper. The mod reads Forestry's
genetics API and integrates with NotEnoughItems, ChromaticTooltips, and UniMixins when they're
present.

## License

[MIT](LICENSE).

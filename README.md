# Music Discs From Folder

A Fabric mod for Minecraft that scans a folder of music on your computer and
turns every song into a findable, playable jukebox disc — with a procedurally
tinted icon based on each song's album art.

> Personal singleplayer mod, actively being built and tested. This README
> gets updated as the mod changes.

## What it does

- Scans a folder you choose (`.mp3`, `.flac`, `.wav`, `.m4a`, `.ogg`, `.opus`)
  and converts each track to an in-game jukebox disc.
- Every disc uses the same pixel-art vinyl icon; only the label colour
  changes, picked from the most visually dominant colour in that song's
  embedded cover art.
- Discs can be found while exploring (abandoned mineshafts, End city
  treasure, strongholds, dungeons, buried treasure) or given directly via
  `/give`.
- An in-game "Music Discs" menu (button on the title screen) lets you:
  - browse for your music folder (native OS picker),
  - rescan & convert,
  - enable/disable songs globally ("Edit Library"),
  - enable/disable songs per-world ("Edit Per-World Discs").
- **Singleplayer / "Open to LAN" only, by design** — a dedicated server has
  no single folder that makes sense as "the server's music."

## Requirements

- Minecraft **26.2**, Fabric Loader `>=0.19.3`, Fabric API `0.158.0+26.2`
- **Java 25**
- **ffmpeg** installed and on `PATH` (used for audio conversion and cover
  art extraction)
- Gradle (no wrapper committed — run plain `gradle`, not `./gradlew`)

## Running it

```bash
gradle runClient
```

First launch scans your configured Music folder and converts every track —
for a large library this can take a while (ffmpeg does real work per file).
Config lives in `run/config/musicdiscs.json` (folder path, ffmpeg path, loot
chance, allowed extensions).

## Status / known issues

- Newly added songs need a **game restart** before they become usable items
  (Minecraft freezes item registration at boot). Toggling an already-known
  song's enabled state does not need a restart.
- The generated resource pack (icons/sounds/item names) is auto-enabled on
  boot; no manual step needed.
- Existing (not brand-new) worlds may occasionally need one manual
  `/reload` if a disc you already have doesn't make sound the first time.

## Architecture notes (for future work)

- `jukebox_song` is a Minecraft **datapack-loaded registry**, not something
  registerable directly in Java. The generated jukebox_song JSON is written
  straight into this mod's own resource root (via Fabric's `ModContainer`),
  which is already merged into the game's "vanilla" data the same way any
  mod's static `assets/`/`data/` files are — including during the
  world-creation preview screen, which loads only vanilla + built-in mod
  data before any world folder exists. An earlier per-world datapack-copy
  approach could not reach that step and crashed on world creation.
- `.jukeboxPlayable(ResourceKey<JukeboxSong>)` (a real registry entry) is
  required, not just an in-memory `Holder` with the right data — actual
  jukebox playback resolves the sound to play via `registry.getId(song)`,
  which is **identity-based**, so it only returns a valid id for objects
  that are genuinely registered.
- ffmpeg conversion uses `-vn` (no video) — source files with embedded cover
  art otherwise get muxed into the `.ogg` with an extra video stream, which
  Minecraft's sound engine silently refuses to play.
- 26.2 renamed `ResourceLocation` → `Identifier`, replaced `GuiGraphics`
  with `GuiGraphicsExtractor` (`render()` → `extractRenderState()`), and
  introduced major.minor pack formats (`pack.mcmeta` needs `min_format`/
  `max_format`, not the old `pack_format`, once the major version exceeds
  `PackFormat.lastPreMinorVersion(type)`).

## Project layout

```
src/main/java/com/musicdiscs/       -- common code (client + server)
src/client/java/com/musicdiscs/     -- client-only code (GUI, mixins)
src/main/resources/fabric.mod.json
```

See `CLAUDE.md` for the full original design brief and build history.

# Music Discs From Folder — project brief for Claude Code

Read this whole file before touching anything. It's a handoff from a chat
session (no terminal access there, filesystem-plugin only) that designed
and wrote this entire project blind — never compiled, never run. You have
what that session didn't: a real terminal on the user's machine. Your first
job is to get it compiling; that alone will surface real errors instead of
guesses.

## Who you're working with

The user is **not a programmer**. They are the designer/client: they set
requirements, make the subjective/creative calls (does this look right,
is this behavior what they wanted), and you implement. Explain things in
plain language when reporting progress or asking them to test something —
no assuming they know Gradle, Java, or Minecraft internals. When you hit a
build error, fix it yourself using the reference material below rather
than asking them to research it.

## What the mod does

Scans the user's Windows Music folder, turns each song into a Minecraft
"disc" item (playable in a jukebox), and gives the player a chance to find
one while exploring (abandoned mineshafts, End city treasure, strongholds,
dungeons, buried treasure). Every disc icon is the same procedurally-drawn
vinyl-record sprite; only the label circle in the middle is tinted, to the
most visually dominant colour extracted from the song's embedded album
art (colour-bucketed, weighted toward saturated/mid-brightness pixels —
deliberately not a flat pixel average, which tends to collapse to muddy
grey/brown).

In-game, a "Music Discs" button on the title screen opens a hub with:
browse-for-folder (native OS picker), rescan & convert (with a progress
screen), a searchable global enable/disable checklist ("Edit Library"),
and a per-save enable/disable checklist ("Edit Per-World Discs" — pick a
save, then the same checklist scoped to just that world; any world you
haven't customized inherits the global list automatically). There is no
artificial cap on how many discs/songs are allowed.

**Scope: singleplayer / "Open to LAN" only, by design.** A real dedicated
server doesn't scan anything (there's no single "the server's Music
folder" that makes sense) — see the `EnvType.CLIENT` check in
`MusicDiscsMod.onInitialize()`.

## Target versions

- Minecraft **26.2** (released June 16, 2026 — note Mojang moved from the
  old `1.21.x` numbering to year-based numbering in March 2026 with
  `26.1`; there is no newer `1.21.x` line anymore)
- Fabric Loom, **official Mojang mappings** (Yarn was dropped as of 26.1 —
  don't reintroduce a Yarn mappings line anywhere)
- **Java 25** required (confirmed installed and working on the user's
  machine — `java -version` shows `openjdk version "25.0.4.1"`)
- Fabric API `0.158.0+26.2` (confirmed this exact version is published and
  correct)

## Directory layout

```
build.gradle, settings.gradle, gradle.properties, README.md   -- project root
src/main/java/com/musicdiscs/                                  -- common code (runs on client AND dedicated server)
  MusicDiscsMod.java            -- ModInitializer entrypoint; orchestrates scan/convert/register/loot/pack-write
  ServerDatapackInjector.java   -- copies generated jukebox_song datapack into each world's save folder on server start; also resolves per-world loot selection into CurrentWorldContext
  config/ModConfig.java         -- musicdiscs.json (folder path, ffmpeg path, loot chance, extensions, maxDiscs safety valve)
  config/LibraryStore.java      -- musicdiscs_library.json (every known song + global enabled flag)
  config/WorldSelectionStore.java -- <save>/musicdiscs_selection.json (per-world override, optional)
  scan/DiscEntry.java           -- data model for one scanned song
  scan/MusicScanner.java        -- recursive folder walk, filters by extension
  scan/FfmpegHelper.java        -- shells out to ffprobe (metadata) and ffmpeg (ogg conversion, cover art extraction)
  scan/DiscIconRenderer.java    -- dominant-colour extraction + procedural vinyl icon drawing
  pack/GeneratedPackWriter.java -- writes the resourcepack (sounds/textures/models/lang) + datapack template (jukebox_song jsons) to disk
  item/ModItems.java            -- registers one Item per song, with default jukebox_playable component
  item/CurrentWorldContext.java -- static holder: which songIds are loot-eligible in the currently-starting world
  item/LootHooks.java           -- Fabric LootTableEvents.MODIFY hook adding a gated bonus pool of discs to 5 vanilla loot tables
src/client/java/com/musicdiscs/                                 -- client-only code (never loaded on a dedicated server)
  MusicDiscsClientMod.java      -- ClientModInitializer, deliberately minimal
  mixin/TitleScreenMixin.java   -- injects the "Music Discs" button into TitleScreen
  gui/MusicDiscsMenuScreen.java -- hub screen
  gui/NativeFolderPicker.java   -- JFileChooser wrapper (background thread + Minecraft#execute to hop back to main thread)
  gui/ScanProgressScreen.java   -- progress screen while a rescan runs on a background thread
  gui/SongListScreen.java       -- shared checklist UI (global library OR one world, decided by a nullable Path param)
  gui/WorldSelectScreen.java    -- lists saves/ folder, opens SongListScreen scoped to the chosen one
src/main/resources/fabric.mod.json
src/client/resources/musicdiscs.client.mixins.json
```

## THE CURRENT BLOCKER — fix this first

The chat session was mid-way through fixing the build.gradle/gradle.properties
when its filesystem connection to the user's machine timed out. **The files
on disk may still have the broken versions.** Read both files and make sure
they match what's below exactly — this combination is cross-checked against
FabricMC's own `fabric-example-mod` repo, branch `26.1.2`
(https://github.com/FabricMC/fabric-example-mod/blob/26.1.2/build.gradle and
.../gradle.properties), which is the authoritative reference for this
Minecraft version line.

**gradle.properties should contain:**
```
org.gradle.jvmargs=-Xmx2G
org.gradle.parallel=true
org.gradle.configuration-cache=false

minecraft_version=26.2
loader_version=0.19.3
loom_version=1.17-SNAPSHOT

mod_version=0.2.0
maven_group=com.musicdiscs
archives_base_name=musicdiscs

fabric_api_version=0.158.0+26.2
```

**build.gradle's `dependencies {}` block must be exactly:**
```groovy
dependencies {
	minecraft "com.mojang:minecraft:${project.minecraft_version}"
	implementation "net.fabricmc:fabric-loader:${project.loader_version}"
	implementation "net.fabricmc.fabric-api:fabric-api:${project.fabric_api_version}"
}
```
Note: **no `mappings ...` line at all**, and `implementation` (not
`modImplementation`) for both loader and fabric-api. This differs from
older (pre-26.1 / Yarn-era) Fabric tutorials you may know from training —
don't "fix" it back to the old pattern.

Also consider matching the reference's `java {}` block, which uses plain
`sourceCompatibility`/`targetCompatibility` rather than a `toolchain {}`
block:
```groovy
java {
	withSourcesJar()
	sourceCompatibility = JavaVersion.VERSION_25
	targetCompatibility = JavaVersion.VERSION_25
}
```
The toolchain-based version currently in the file may or may not be fine —
untested — but the sourceCompatibility form is the one actually proven
working in Fabric's own reference repo, so prefer it if the toolchain form
gives any trouble.

### Build error history (so you don't re-derive these from scratch)

1. `Plugin [id: 'fabric-loom', version: '1.17'] was not found` → `1.17`
   alone isn't a real published version; needs a full version string.
2. `Configuration 'mappings' has no dependencies` → attempted fix was
   adding `mappings loom.officialMojangMappings()`. **This was wrong** —
   see next error.
3. `Failed to find official mojang mappings for 26.2` → caused by that
   same `mappings loom.officialMojangMappings()` line. Root cause,
   confirmed against FabricMC's own reference repo: 26.1+ doesn't use an
   explicit `mappings` line at all, AND the loom version needs to be the
   `1.17-SNAPSHOT` build, not a tagged `1.17.x` dot-release (the snapshot
   has fixes for the new version-numbering scheme that hadn't reached a
   tagged release yet as of this writing). This is the fix described
   above — apply it, then try `gradle runClient` again.

If a *new* error shows up after this fix, compare against the reference
repo's files before guessing.

## Design history (so you understand *why* things are built this way)

1. Original ask: scan the Windows Music folder, turn songs into findable
   discs in mineshafts/the End.
2. First cover-art idea (pixel-mosaic compression to 16×16) was
   **explicitly superseded** — current design is the dominant-colour vinyl
   icon described above. If you see any mention of shrinking full cover
   art into the icon, that's stale; don't reintroduce it.
3. User asked for an in-game menu (folder picker + song selection) instead
   of only automatic boot-time scanning, plus per-world song selection,
   plus no artificial disc cap. All three are implemented as described
   above.
4. Per-world selection deliberately does **not** hook into vanilla's
   world-creation screen (`CreateWorldScreen`) — that's a much deeper,
   more fragile mixin target. Instead it's a standalone screen (pick an
   existing save → edit its list), which is both lower-risk and arguably
   better UX (editable anytime, not just at creation).
5. `jukebox_playable` is set as a **default item component at
   registration** (`ModItems.java`), not only granted via a loot table
   function — this was a deliberate choice so `/give` and creative-mode
   testing work immediately, at the cost of it being the single highest-risk
   API call in the project (new-in-1.21 feature). See the comment block at
   the top of `ModItems.java` for the exact fallback if
   `.jukeboxPlayable(...)` doesn't compile.
6. The generated resource pack is **not** auto-enabled programmatically —
   deliberately left as a one-time manual step in Options > Resource Packs,
   because the client-side pack-enabling API was judged too risky to guess
   blind (see comment in `MusicDiscsClientMod.java`). Feel free to attempt
   auto-enabling now that you can actually test it, if you want to remove
   that manual step.
7. **A real bug was found and fixed** during a manual code review pass
   (no compiler available yet, so this was a careful read-through): the
   first version of `SongListScreen.java` called a full
   `clearWidgets()` + `init()` rebuild from *inside* the search box's own
   `setResponder` callback — i.e. every keystroke destroyed and recreated
   the text field that was still mid-keystroke. The fixed version (current
   on disk) only tears down and re-adds the checkbox rows
   (`refreshRows()`), never the search box or other buttons. If you're
   reading an older cached copy of this file for any reason, don't revert
   this.
8. One **known, low-priority, unfixed** inconsistency: `DiscEntry.labelColor`
   only gets set when an icon is freshly generated; on a cache hit (icon
   PNG already exists from a previous run) it stays at the class default
   grey. Nothing currently reads this field, so it's cosmetically wrong
   but functionally harmless — worth fixing properly (e.g. cache the
   colour alongside the icon) only if you build a UI feature that needs
   it, such as a colour-swatch preview in the checklist screens.

## Known risk areas — ranked, with fallbacks already in the code

Fix build errors here in roughly this order if they show up; each file has
a comment block explaining the specific risk and what to try instead.

1. **`item/ModItems.java`** — `.jukeboxPlayable(songKey)` on `Item.Properties`.
   New-in-1.21 API. Fallback (using `DataComponents.JUKEBOX_PLAYABLE` +
   `JukeboxPlayable` directly) is commented in the file.
2. **`gui/SongListScreen.java`** — `Checkbox.builder(...)`. Not certain
   26.2 uses a builder here the way `Button` does. Fallback (direct
   `Checkbox` constructor + `onPress()` override) is commented in the file.
   Also uses `this.removeWidget(...)` to swap out just the checkbox rows —
   real Screen method as far as the original session knew, but unverified.
3. **`item/CurrentWorldContext.java` / `ServerDatapackInjector.java`** —
   the whole per-world loot filtering setup assumes
   `ServerLifecycleEvents.SERVER_STARTING` runs, and populates
   `CurrentWorldContext`, *before* loot tables get built for that world.
   If per-world filtering seems wrong right when a world loads, that
   ordering assumption is the first thing to check; the documented
   workaround is running `/reload` once, which should re-evaluate loot
   tables against whatever's already in `CurrentWorldContext` by then.
4. **`ServerDatapackInjector.java`** — `LevelResource.ROOT` and
   `LevelResource.DATAPACK_DIR` constant names: moderate confidence only,
   not verified against 26.2's actual `LevelResource` enum.
5. Everything else (Button, EditBox, GuiGraphics text drawing, Fabric's
   `LootTableEvents`/`ItemGroupEvents` APIs, mixin injection into
   `TitleScreen.init`) uses longer-standing, more stable API surface —
   lower risk, but still literally never compiled, so don't be shocked by
   small naming mismatches anywhere.

## Known rough edges the user has already been told about

- Existing (not brand-new) worlds may need one manual
  `/datapack enable "file/musicdiscs_generated"` + `/reload` the first
  time, for the same reason as point 3 above.
- The resource pack needs one manual enable in Options > Resource Packs
  the first time (see point 6 above).
- Registering a brand-new item only happens at game boot — so a song that
  wasn't present at launch needs a relaunch before it exists as an item,
  even after using "Rescan & Convert" mid-session. Toggling an
  *already-known* song's enabled state (global or per-world) doesn't need
  a relaunch, just a world reload / `/reload`.

## User's local environment (already set up and confirmed working)

- Windows 11 (build 26200)
- JDK 25 (Eclipse Temurin) — confirmed via `java -version`
- Gradle installed manually (no Gradle wrapper committed in this project —
  the user runs plain `gradle ...`, not `./gradlew ...`). If you want to
  generate a wrapper for more version stability, that's a reasonable
  improvement, just don't assume one exists.
- ffmpeg (a full git build, not the "essentials" build originally
  suggested — that's fine, full builds work too) extracted and on PATH,
  confirmed via `ffmpeg -version`
- The project folder itself is `C:\Users\gimpOStin\Desktop\Claude Sandbox (Mod)`

## What "done" looks like for your first pass

1. `gradle runClient` compiles and actually opens a Minecraft window,
   with no exceptions in the console during startup.
2. The title screen shows the "Music Discs" button and it opens the hub
   screen without crashing.
3. Walk through each menu screen and fix whatever the compiler/runtime
   surfaces — you have eyes on the terminal even if not on the rendered
   game window, so get as far as you can from logs/exceptions alone
   before asking the user to actually click through screens and describe
   what they see.
4. Report back to the user in plain language: what's working, what you
   fixed, and specifically what you need THEM to check by actually
   playing (visual/audio things you can't verify from a terminal).

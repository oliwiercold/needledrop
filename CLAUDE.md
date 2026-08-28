# Needledrop — project brief for Claude Code

This project was originally handed off blind (a chat session with no
terminal wrote it, never compiled, never run) as "Music Discs From
Folder". Since then it's been built, fixed, tested live against a real
Minecraft install, rebranded to **Needledrop**, and is now a working mod
with a public GitHub repo. This file is the up-to-date technical brief —
read it before touching anything. If you're picking this up in a fresh
conversation, the "Session log" section below is the important part: it's
a record of real bugs found by actually running the mod, not just reading
the code, and several of them are the kind you'd re-introduce by "fixing"
something that looks wrong but isn't.

## Who you're working with

The user is **not a programmer**. They are the designer/client: they set
requirements, make the subjective/creative calls (does this look right,
is this behavior what they wanted), and you implement. Explain things in
plain language — no assuming they know Gradle, Java, or Minecraft
internals. When you hit a build error, fix it yourself using this file
and the actual decompiled Minecraft source (see "How to look things up"
below) rather than asking them to research it. The user communicates
mostly in Polish; match that unless they switch.

**Communication habit the user has explicitly asked for:** don't say
something is "fixed" until they've actually verified it themselves,
especially anything that requires clicking through the real game (world
creation, in-game menus, audio) — you have no way to automate the actual
Minecraft window, only its logs. Report what you changed and what you
verified from logs/compilation, and be explicit about what still needs
their own testing.

## What the mod does

Scans a folder of music (defaults to the Windows Music folder) and turns
each song into a real, playable Minecraft jukebox disc. Every disc icon
is a procedurally-drawn vinyl-record sprite (dark body, groove rings,
spindle hole) — **entirely hand-drawn in code, no game-derived texture
involved** (see Session log — this was a deliberate change away from an
earlier design). Only the label circle in the middle is tinted, to the
most visually dominant colour extracted from the song's embedded album
art (colour-bucketed, weighted toward saturated/mid-brightness pixels —
deliberately not a flat pixel average, which tends to collapse to muddy
grey/brown). The 22 vanilla Minecraft discs also get a texture override
in the same procedural style (each a different stable colour), so the
whole disc shelf — vanilla and generated — looks visually consistent.

In-game, a note-icon button next to "Singleplayer" on the title screen
opens a hub with: browse-for-folder (native OS picker), rescan & convert
(with a progress screen), a searchable global enable/disable checklist
("Edit Library"), and a per-save enable/disable checklist ("Edit
Per-World Discs" — pick a save, then the same checklist scoped to just
that world; any world you haven't customized inherits the global list
automatically). There is no artificial cap on how many discs/songs are
allowed. Discs can also turn up as loot (Overworld structures, Nether
bastions/fortresses, End city treasure — per-dimension chance per
eligible chest, config `lootChanceOverworld`/`lootChanceNether`/
`lootChanceEnd`, higher in Nether/End by design — see Session log), or
be `/give`n directly.

**Scope: singleplayer / "Open to LAN" only, by design.** A real dedicated
server doesn't scan anything (there's no single "the server's Music
folder" that makes sense) — see the `EnvType.CLIENT` check in
`MusicDiscsMod.onInitialize()`.

## Target versions

- Minecraft **26.2**, Fabric Loader `0.19.3`, Fabric API `0.158.0+26.2`,
  Fabric Loom `1.17-SNAPSHOT`, **official Mojang mappings** (no Yarn line
  anywhere — dropped as of 26.1)
- **Java 25**
- `build.gradle`'s `dependencies{}` uses plain `implementation` (not
  `modImplementation`) for both loader and fabric-api, and has **no
  `mappings` line at all** — this is correct for 26.1+, don't "fix" it
  back to older Fabric-tutorial patterns from training data.

## Directory layout

```
build.gradle, settings.gradle, gradle.properties, README.md, LICENSE   -- project root
dist/musicdiscs-0.2.0.jar        -- the actual distributable jar, committed to git (build/ is gitignored)
needle.png                        -- historical icon source, superseded (see Session log) -- unused, kept around
src/main/java/com/musicdiscs/                                  -- common code (client AND dedicated server)
  MusicDiscsMod.java            -- ModInitializer entrypoint; orchestrates scan/convert/register/loot/pack-write
  ServerDatapackInjector.java   -- resolves per-world loot selection into CurrentWorldContext on server start
  mixin/ServerPacksSourceMixin.java -- see Session log: the actual fix for jukebox_song not being visible at world creation
  config/ModConfig.java         -- musicdiscs.json (folder path, ffmpeg path, loot chance, extensions, maxDiscs safety valve)
  config/LibraryStore.java      -- musicdiscs_library.json (every known song: title/artist/length + global enabled flag)
  config/WorldSelectionStore.java -- <save>/musicdiscs_selection.json (per-world override, optional)
  scan/DiscEntry.java           -- data model for one scanned song
  scan/MusicScanner.java        -- recursive folder walk, filters by extension
  scan/FfmpegHelper.java        -- shells out to ffprobe (metadata) and ffmpeg (ogg conversion, cover art extraction)
  scan/DiscIconRenderer.java    -- dominant-colour extraction + procedural vinyl icon drawing (no external texture)
  pack/GeneratedPackWriter.java -- writes the "Needledrop" resourcepack + datapack template (jukebox_song jsons) to disk
  item/ModItems.java            -- registers one Item per song, with default jukebox_playable component
  item/CurrentWorldContext.java -- static holder: which songIds are loot-eligible in the currently-starting world
  item/LootHooks.java           -- Fabric LootTableEvents.MODIFY hook adding a gated bonus pool of discs to 5 vanilla loot tables
src/client/java/com/musicdiscs/                                 -- client-only code (never loaded on a dedicated server)
  MusicDiscsClientMod.java      -- ClientModInitializer; also auto-enables the generated resource pack on CLIENT_STARTED
  mixin/TitleScreenMixin.java   -- injects the note-icon button into TitleScreen, next to Singleplayer
  gui/MusicDiscsMenuScreen.java -- hub screen
  gui/NativeFolderPicker.java   -- JFileChooser wrapper (background thread + Minecraft#execute to hop back to main thread)
  gui/ScanProgressScreen.java   -- progress screen while a rescan runs on a background thread
  gui/SongListScreen.java       -- shared checklist UI (global library OR one world, decided by a nullable Path param)
  gui/WorldSelectScreen.java    -- lists saves/ folder, opens SongListScreen scoped to the chosen one
src/main/resources/fabric.mod.json                              -- name "Needledrop", id stays "musicdiscs" (see below)
src/main/resources/musicdiscs.mixins.json                       -- registers ServerPacksSourceMixin
src/main/resources/assets/musicdiscs/icon.png                   -- mod icon (jukebox render, user-supplied)
src/main/resources/assets/musicdiscs/pack_icon.png              -- same image, used as the generated resourcepack's pack.png
src/main/resources/assets/minecraft/textures/item/music_disc_*.png -- procedural texture overrides for all 22 vanilla discs
src/client/resources/musicdiscs.client.mixins.json
```

**Naming note:** the mod is branded "Needledrop" (display name, resource
pack folder name, GitHub repo topic) but the Java package, mod id in
`fabric.mod.json`, config file names, and internal namespace all stay
`musicdiscs` — that was a deliberate choice made when rebranding, to
avoid a much larger renaming pass with no functional benefit. Don't be
surprised the two don't match; it's not a mistake to fix.

## How to look things up (this matters more than it sounds)

This mod has been broken by trusting Fabric-tutorial patterns from
training data that predate Minecraft 26.x's renames. When something
doesn't compile or doesn't behave as expected, don't guess from memory —
decompile and read the actual source:

```
gradle genSources
```

produces real, current `.java` for the exact Minecraft version this
project targets, findable under
`.gradle/loom-cache/minecraftMaven/net/minecraft/*-sources.jar`. Several
bugs in the Session log below (the jukebox_song visibility fix, the
`pushAssetPath` argument, the `exposeNamespace` root cause) were only
found this way — by reading the real implementation instead of assuming.
Also useful: `javap -p -classpath <jar> <fully.qualified.ClassName>` for
a quick method-signature check without extracting anything.

## Session log — what's been done since the original blind handoff

Newest/most-impactful first isn't followed here; this is roughly
chronological, since later fixes sometimes build on earlier ones.

1. **Got it compiling.** The original `build.gradle`/`gradle.properties`
   had a bad Loom version and a stray `mappings loom.officialMojangMappings()`
   line that doesn't apply to 26.1+. Fixed by cross-referencing FabricMC's
   own `fabric-example-mod` repo.
2. **Full 26.2 API migration**, found by decompiling and reading real
   source: `ResourceLocation`→`Identifier`, `GuiGraphics`→`GuiGraphicsExtractor`,
   `Screen.render()`→`Screen.extractRenderState()`,
   `ItemGroupEvents`→`CreativeModeTabEvents`, `LootPool.builder()`→`LootPool.lootPool()`,
   `LootTable.Builder.pool()`→`withPool()`, `ResourceKey.location()`→`ResourceKey.identifier()`,
   `Item.Properties` now needs `.setId(ResourceKey<Item>)` before construction.
3. **Silent disc audio** — root cause: embedded cover art was getting
   muxed into the output `.ogg` as a Theora video stream alongside the
   Vorbis audio, which Minecraft's decoder silently can't handle. Fixed
   with `-vn` in the `ffmpeg` conversion command.
4. **Invisible custom UI text** across every custom screen — root cause:
   `GuiGraphicsExtractor.text()` checks `ARGB.alpha(color) != 0`, and
   every colour literal in the original code was a bare 6-hex-digit value
   (e.g. `0xFFFFFF`) with an implicit zero alpha byte, so text was drawn
   with full transparency — no exception, no crash, just nothing visible.
   Fixed by prefixing every colour with `0xFF` for full alpha.
5. **The world-creation crash (jukebox_song), the big one.** Symptom:
   creating a new world hangs forever on "Preparing for world creation...".
   `jukebox_song` is a *datapack-loaded dynamic registry*, not something
   Java-registerable — `.jukeboxPlayable(key)` needs that key to actually
   resolve at server-resource-build time, including during the
   world-creation *preview* screen, which builds its data from
   `ServerPacksSource.createVanillaPackSource()` (vanilla + built-in mod
   data only — no per-world folder exists yet at that point). Three
   approaches were tried and failed before the real fix:
   - Copying generated data into the world's own datapack folder on
     `SERVER_STARTING` — too late, the preview screen runs before any
     world folder exists.
   - Hooking `LevelStorageAccess` right after world storage opens — still
     too late for the preview step specifically.
   - Writing into the mod's own jar resource root — worked in
     `gradle runClient` (where `build/resources/*` happens to be
     writable) but is fundamentally impossible for a real installed jar,
     and also broke the `jar` build task with "duplicate entry" errors
     from Loom merging main+client outputs.

   **The actual fix, `ServerPacksSourceMixin.java`:** a `@Redirect` on
   `ServerPacksSource.createVanillaPackSource()`'s final
   `VanillaPackResourcesBuilder.build(info)` call, injecting
   `builder.pushAssetPath(PackType.SERVER_DATA, dataDir)` first, where
   `dataDir` is `<gameDir>/musicdiscs_cache/datapack_template/data`
   (an external folder we control, regenerated on every boot/rescan).

   Even after this, **the crash still didn't go away** — turned out
   `pushAssetPath` alone isn't sufficient. `createVanillaPackSource()`
   calls `.exposeNamespace("minecraft")` *before* our redirect runs, and
   `VanillaPackResources.getNamespaces()` just returns that fixed set
   verbatim — it's not derived from what paths are actually pushed. The
   registry loader asks `getNamespaces()` which namespaces even exist
   before it bothers listing resources under them, so our data sat in
   the right place on disk but was never *discovered*. Confirmed by a
   direct boot-time check calling the exact same method the
   world-creation screen uses and inspecting `getNamespaces()` /
   `getResource()` on the result — the fix was adding
   `builder.exposeNamespace("musicdiscs")` alongside `pushAssetPath` in
   the same redirect. **If jukebox_song visibility ever breaks again,
   check both of these are still present together** — one without the
   other silently fails in different ways (missing files vs. files
   present but never looked for).
6. **Slow startup with a large real library (~2000 songs).** Two
   separate causes, both about doing unnecessary work every single boot
   regardless of whether the library changed:
   - `ffmpeg.readMetadata()` (an `ffprobe` subprocess) ran for *every*
     song on *every* boot, even fully-cached ones. Fixed by persisting
     `lengthSeconds` in `LibraryStore` (title/artist already were) and
     skipping `ffprobe` entirely when a song already has a cached
     `.ogg` + icon + known metadata.
   - `GeneratedPackWriter` unconditionally rewrote every per-song file
     (model.json, item.json, icon copy, ogg copy) on every boot, even
     unchanged ones. This has **not yet been fixed in this tracked
     project** — see "Known open items" below, it exists only in an
     untracked experimental copy.
7. **Icon design changed twice:**
   - Original design (pixel-mosaic full cover shrink) was superseded
     early by the current dominant-colour-vinyl approach — if you see
     any code shrinking full cover art into the icon, that's stale.
   - A later iteration added an *optional* rendering path that used a
     user-supplied image (extracted from the actual game files) as a
     texture base, recolouring only the label. **This was removed** —
     the fully procedural renderer (`DiscIconRenderer.renderBuiltinDisc`)
     is now the *only* path, specifically to avoid any dependency on a
     Mojang-derived asset in a distributed mod. If you see
     `setTemplateImagePath` or any texture-loading code in
     `DiscIconRenderer`, that's from before this change and should not
     be reintroduced.
8. **Rebrand to "Needledrop"** — new title-screen button (a manually
   two-tone-rendered `♪` character, light-blue "shadow" behind solid
   white, positioned next to Singleplayer via `TitleScreenMixin`), new
   mod icon (a jukebox-themed image, user-supplied — see below), CC0-1.0
   `LICENSE` added, end-user-facing `README.md` rewritten (distinct from
   this file, which stays developer-facing).
9. **Generated resource pack given a real identity.** It used to write
   to a folder literally named `musicdiscs_generated` — harmless
   internally, but Minecraft's Resource Packs screen shows a *folder*
   pack's title as its literal folder name (confirmed by reading
   `FolderRepositorySource.createDiscoveredFilePackInfo` — it's
   `Component.literal(folderName)`, nothing from `pack.mcmeta`), so
   users were seeing that raw internal name in the UI. Renamed the
   folder to `Needledrop`, added a real `pack.png` (same jukebox image as
   the mod icon, bundled in the mod's own jar and copied out at
   pack-write time), and gave it a proper description string. A one-time
   migration (`MusicDiscsMod.deleteRecursively`) deletes the old
   `musicdiscs_generated` folder on boot so upgrading doesn't leave a
   stale duplicate pack sitting next to the new one.
10. **Vanilla disc textures overridden to match.** All 22 vanilla music
    discs (`music_disc_13` through `music_disc_tears` — see
    `Items.java` for the full list if this ever needs regenerating) get
    a texture override at `assets/minecraft/textures/item/music_disc_*.png`
    in the mod's own resources, in the same procedural style as
    generated discs, each with its own stable colour (same
    `placeholderColor(id)` hash function used for songs with no cover
    art). This is a legitimate, standard Fabric technique — a mod's own
    `assets/minecraft/...` resources simply override vanilla's when the
    resource manager merges packs; no special API needed.
11. **Loot rebalanced, and split by dimension.** The single flat
    `lootChance` (15%) felt too rare and, since only Overworld + End
    tables were ever targeted, gave the Nether nothing at all. Replaced
    with three config values (`lootChanceOverworld`=0.35,
    `lootChanceNether`=0.5, `lootChanceEnd`=0.6 — see `ModConfig.java`)
    and `LootHooks.java` now keys a `Map<Identifier, Double>` of target
    table → chance instead of one shared value. Added the 4 bastion
    remnant tables (`BASTION_TREASURE/OTHER/BRIDGE/HOGLIN_STABLE`) and
    `NETHER_BRIDGE` (fortress) as Nether targets — previously no Nether
    structure was hooked at all. Numbers were reasoned from a rough
    estimate of chests-per-hour for an elytra-equipped player farming End
    cities specifically (the user's stated benchmark: ~15 new discs/hour
    out of a large library at that stage) rather than measured in-game —
    **treat these as a first pass and expect the user to ask for further
    tuning once they've actually played with it.**
12. **AI-tell writing style swept out of code comments and README.** The
    house style up to this point leaned hard on " -- " as an em-dash
    substitute and fairly formal doc-comment phrasing (a habit carried
    over from how this project's assistant writes by default). The user
    asked for that removed from anything a human might read: comments,
    doc comments, user-facing strings, and prose docs, so it doesn't read
    as obviously AI-written. Done via a batch of parallel agents each
    covering a slice of the source tree, then a manual pass on a few
    stragglers. This file (CLAUDE.md) was deliberately left out of that
    pass: it's read by a future Claude session, not by someone judging
    whether a human wrote it, so sanitizing its style doesn't serve the
    actual goal. If you're adding new comments here or in the code,
    there's no hard rule against dashes, just be aware the user notices
    and cares about this in anything public-facing.
13. **Jukebox block itself retextured, bundled in the mod.** The user
    wanted the jukebox to look like a vinyl record player, matching a
    third-party resource pack they'd found ("record-player.zip"). That
    pack turned out to be a specific, identifiable community creation
    (old pack_format 55, jukebox_arm/buttons/cover/record textures, a
    Hungarian lang file) with no license info, not something safe to
    redistribute bundled inside this mod. Declined to bundle it; built an
    original replacement instead, as a placeholder until the user hears
    back from that pack's author about permission. Vanilla's actual 26.2
    jukebox model is a plain `cube_top` (just `top` and `side` textures,
    no extra geometry, confirmed by reading the decompiled model JSON and
    checking the client jar for any jukebox-specific renderer class:
    there isn't one), so the whole "record player" look comes from
    texture painting alone: a vinyl-record top face (reusing the same
    label/groove-ring logic as `DiscIconRenderer`, plus a painted tonearm)
    and a plank-with-control-buttons side face. Bundled at
    `assets/minecraft/textures/block/jukebox_{top,side}.png` in the mod's
    own resources, the same override mechanism as the vanilla disc
    textures (point 10). Generated with a disposable standalone Java
    program (compiled and run directly, not part of the mod), iterated by
    reading the output PNGs back as images rather than guessing blind. If
    the user later gets permission to use the original third-party pack,
    or wants something different, this can just be swapped out.

## Known open items (not yet done, worth revisiting)

- **The per-boot resource-pack-write optimization (point 6 above) exists
  only in an untracked experimental copy**, not in this tracked project.
  The user asked for a separate, deliberately isolated folder (outside
  git) at `Desktop\Needledrop 0.2.0 (Optimized)\source` to try more
  aggressive performance work without risking the known-working build:
  parallel `ffmpeg` conversion (bounded thread pool, ~6 workers) for
  new/uncached songs, and skip-if-already-exists checks before rewriting
  any per-song resource pack file. Both were verified working (50 real
  test files: ~20s cold, ~2s warm) and later had the same icon/pack-name
  changes from points 7–10 re-applied on top by hand. **These
  optimizations have not been merged back into this tracked project** —
  if the user wants them in the real shipped mod, that's a deliberate
  follow-up to do carefully (the parallelization in particular touches
  `DiscIconRenderer`'s template-loading race — already irrelevant now
  that the template path is gone, but check for any other shared mutable
  state before parallelizing anything else).
- The mod icon and resource-pack icon (`assets/musicdiscs/icon.png` /
  `pack_icon.png`) are a user-supplied jukebox render that closely
  resembles an actual Minecraft item icon. The user was told this
  explicitly (it looks game-extracted) and chose to use it anyway,
  consistent with earlier informed risk decisions on other assets. Don't
  second-guess this without the user raising it again themselves.
- `ServerPacksSourceMixin`'s `@Redirect` target is a specific method-call
  shape inside `ServerPacksSource.createVanillaPackSource()`. This is
  the single most version-fragile piece of code in the project — a
  future Minecraft update that restructures that method (even slightly)
  will likely break the mixin. Fabric/Mixin normally fail loudly on a
  mixin target it can't find, so this should surface as a boot-time
  error rather than a silent behavior change, but if `jukebox_song`
  visibility breaks again after a version bump, this is the first place
  to check — re-read the real decompiled source rather than assuming the
  old redirect target still matches.

## Known rough edges the user has already been told about

- A world created *before* a given version of the mod may need one
  manual `/reload` the first time, for the same ordering-assumption
  reason as `CurrentWorldContext` (see `ServerDatapackInjector.java`'s
  own comments) — `enabledSongIds == null` fails *open* (everything
  enabled), so this affects correctness of *per-world filtering*, not
  whether loot/discs work at all.
- Registering a brand-new item only happens at game boot — a song that's
  new since the last restart shows up in "Edit Library" immediately but
  needs a relaunch to exist as a real, giveable/lootable item. Toggling
  an *already-known* song's enabled state doesn't need a relaunch, just
  a world reload / `/reload`.
- Loot chance is per eligible chest (not per structure, not per song) and
  does **not** scale with library size — a bigger library means more
  *variety* if a roll succeeds, not a better chance of a roll succeeding.
  As of the loot rebalance (see Session log), chance is per-dimension:
  Overworld tables (abandoned mineshaft, stronghold corridor, simple
  dungeon, buried treasure) default to 35%; Nether tables (all 4 bastion
  loot tables + nether fortress) default to 50%; End city treasure
  defaults to 60%. Config keys: `lootChanceOverworld`/`lootChanceNether`/
  `lootChanceEnd`.

## User's local environment

- Windows 11, JDK 25 (Temurin), Gradle installed manually (no wrapper —
  user runs plain `gradle ...`), ffmpeg (full git build) on PATH.
- Project folder: `C:\Users\gimpOStin\Desktop\Claude Sandbox (Mod)`.
- Distribution: `dist/musicdiscs-0.2.0.jar` is committed to git (unlike
  `build/`, which is gitignored) — this is the file the user actually
  drops into their real Modrinth-based Minecraft instance to test.
  Copies also get placed on the Desktop directly and in a version-named
  folder (`Desktop\Needledrop 0.2.0\`) on request. **After any change
  that should reach the user's real install, rebuild
  (`gradle clean build`), copy `build/libs/musicdiscs-0.2.0.jar` to all
  of the above, and say so explicitly** — the user tests in a completely
  separate Minecraft instance you have no access to, so nothing you do
  in `gradle runClient` reaches them until you copy the jar out.
- GitHub: `github.com/oliwiercold/musicdiscs`, private repo, branch
  `master`. Commit and push after verified changes; the user has not
  asked for PRs, just direct commits to master.

package com.musicdiscs;

import com.musicdiscs.config.LibraryStore;
import com.musicdiscs.config.ModConfig;
import com.musicdiscs.item.LootHooks;
import com.musicdiscs.item.ModItems;
import com.musicdiscs.pack.GeneratedPackWriter;
import com.musicdiscs.scan.DiscEntry;
import com.musicdiscs.scan.DiscIconRenderer;
import com.musicdiscs.scan.FfmpegHelper;
import com.musicdiscs.scan.MusicScanner;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.IntConsumer;

public class MusicDiscsMod implements ModInitializer {

	public static final String MODID = "musicdiscs";

	/** Every scanned+registered disc, kept around so the GUI and ServerDatapackInjector can see what exists. */
	public static List<DiscEntry> ENTRIES = new ArrayList<>();
	public static ModConfig CONFIG;
	public static LibraryStore LIBRARY;

	private static boolean itemsAlreadyRegistered = false;

	@Override
	public void onInitialize() {
		// Must happen before ANYTHING else touches AWT, including indirectly --
		// java.awt.headless is read once by GraphicsEnvironment and then cached
		// forever, and Minecraft's own asset-loading workers use ImageIO (and
		// so AWT) very early. Mod init is the earliest hook available, before
		// Minecraft itself exists.
		System.setProperty("java.awt.headless", "false");

		CONFIG = ModConfig.load();
		LIBRARY = LibraryStore.load();

		// Reading an arbitrary local folder + shelling out to ffmpeg only makes
		// sense on the physical client (singleplayer runs an integrated server
		// in the same process, so this still covers the normal use case). On a
		// real dedicated server there is no "your Music folder" to speak of, so
		// we just skip scanning there and the mod does nothing extra.
		if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) {
			System.out.println("[musicdiscs] Running on a dedicated server -- skipping local music scan.");
			return;
		}

		List<DiscEntry> entries = runScanAndConvert(CONFIG, LIBRARY, progress -> {});
		ENTRIES = entries;

		ModItems.registerAll(entries);
		itemsAlreadyRegistered = true;
		LootHooks.register(CONFIG);
		ServerDatapackInjector.register();

		logReadyMessage(entries);
	}

	/**
	 * The actual scan -> ffprobe -> ffmpeg convert -> icon render -> write
	 * generated pack pipeline, split out from onInitialize() so the in-game
	 * "Rescan" button can call it too. Safe to call repeatedly (skips
	 * already-converted files by checking the cache).
	 *
	 * IMPORTANT LIMITATION: calling this again after boot updates the
	 * generated resource pack / datapack files and LibraryStore on disk, but
	 * Minecraft freezes item registries after startup -- so a song that's
	 * brand new since the last restart will show up in the "Edit Library"
	 * list, but won't actually be a usable item until you relaunch. Songs
	 * that were already known keep working immediately.
	 */
	public static List<DiscEntry> runScanAndConvert(ModConfig config, LibraryStore library, IntConsumer progressPercent) {
		FfmpegHelper ffmpeg = new FfmpegHelper(config.ffmpegPath);
		if (!ffmpeg.isAvailable()) {
			System.err.println("[musicdiscs] Could not run ffmpeg at '" + config.ffmpegPath + "'. " +
					"Install ffmpeg and make sure it's on your PATH (or set the full path via the in-game menu), " +
					"then rescan. No discs will be generated this run.");
			return new ArrayList<>();
		}

		Path gameDir = FabricLoader.getInstance().getGameDir();
		Path cacheDir = gameDir.resolve("musicdiscs_cache");
		Path oggCacheDir = cacheDir.resolve("ogg");
		Path iconCacheDir = cacheDir.resolve("icons");
		Path resourcePackDir = gameDir.resolve("resourcepacks").resolve("musicdiscs_generated");
		Path datapackTemplateDir = cacheDir.resolve("datapack_template");
		DiscIconRenderer.setTemplateImagePath(gameDir.resolve("template_assets").resolve("disc_template.png"));

		Set<String> extensions = Set.of(config.extensions);
		List<DiscEntry> entries = MusicScanner.scan(Path.of(config.musicFolderPath), extensions, config.maxDiscs);

		Iterator<DiscEntry> it = entries.iterator();
		int done = 0;
		int total = Math.max(1, entries.size());
		while (it.hasNext()) {
			DiscEntry entry = it.next();
			ffmpeg.readMetadata(entry);

			Path oggOut = oggCacheDir.resolve(entry.songId() + ".ogg");
			if (!Files.exists(oggOut)) {
				if (!ffmpeg.convertToOgg(entry, oggOut)) {
					it.remove();
					done++;
					progressPercent.accept(done * 100 / total);
					continue;
				}
			}
			entry.convertedOgg = oggOut;

			Path iconOut = iconCacheDir.resolve(entry.itemId() + ".png");
			if (!Files.exists(iconOut)) {
				Path rawCover = ffmpeg.extractCoverArt(entry, cacheDir.resolve("raw_covers"));
				Color color = rawCover != null ? DiscIconRenderer.extractDominantColor(rawCover) : null;
				if (color == null) {
					color = DiscIconRenderer.placeholderColor(entry.id);
				} else {
					entry.hasCoverArt = true;
				}
				entry.labelColor = color;
				DiscIconRenderer.render(color, iconOut);
			} else {
				entry.hasCoverArt = true;
			}
			entry.iconPng = iconOut;

			done++;
			progressPercent.accept(done * 100 / total);
		}

		System.out.println("[musicdiscs] Converted/verified " + entries.size() + " tracks.");

		library.mergeScanResults(entries);
		GeneratedPackWriter.write(entries, resourcePackDir, datapackTemplateDir);

		return entries;
	}

	public static boolean itemsAlreadyRegistered() {
		return itemsAlreadyRegistered;
	}

	private static void logReadyMessage(List<DiscEntry> entries) {
		System.out.println("[musicdiscs] Setup complete. " + entries.size() + " discs are ready.");
	}
}

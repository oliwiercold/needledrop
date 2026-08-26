package com.musicdiscs;

import com.musicdiscs.config.WorldSelectionStore;
import com.musicdiscs.item.CurrentWorldContext;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Two jobs on every server start (this fires for the integrated singleplayer
 * server too, which is what "Open to LAN" and normal singleplayer both use):
 *
 *  1. jukebox_song is a datapack-loaded registry, not something we can
 *     register directly in Java, and it's loaded per-world rather than
 *     globally -- so we copy our generated "datapack_template" into that
 *     world's own datapacks/ folder under the name "musicdiscs_generated".
 *
 *  2. Load that world's musicdiscs_selection.json (if any) and push the
 *     resolved enabled-song-id set into CurrentWorldContext, so LootHooks
 *     knows which discs are eligible in THIS world specifically, before
 *     loot tables get built.
 *
 * KNOWN ROUGH EDGE (job 1): for a world you've already played before adding
 * this mod, Minecraft may not auto-enable a datapack that just appeared on
 * disk. If discs you find don't make sound, run in-game:
 *   /datapack enable "file/musicdiscs_generated"
 *   /reload
 * (For a brand new world, newly-present datapacks are enabled by default,
 * so this should just work.)
 */
public class ServerDatapackInjector {

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTING.register(ServerDatapackInjector::onServerStarting);
	}

	private static void onServerStarting(MinecraftServer server) {
		Path saveRoot = server.getWorldPath(LevelResource.ROOT);

		// Job 2 first -- cheap, and we want CurrentWorldContext populated
		// before anything downstream (loot table loading) reads it.
		try {
			WorldSelectionStore selection = WorldSelectionStore.load(saveRoot);
			CurrentWorldContext.set(selection.effectiveEnabledSet(MusicDiscsMod.LIBRARY));
		} catch (Exception e) {
			System.err.println("[musicdiscs] Could not resolve per-world disc selection, defaulting to all enabled: " + e.getMessage());
		}

		if (MusicDiscsMod.ENTRIES.isEmpty()) return;

		Path template = FabricLoader.getInstance().getGameDir()
				.resolve("musicdiscs_cache").resolve("datapack_template");
		if (!Files.isDirectory(template)) return;

		Path target = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve("musicdiscs_generated");

		try {
			copyRecursively(template, target);
			System.out.println("[musicdiscs] Wrote jukebox_song datapack into " + target);
		} catch (IOException e) {
			System.err.println("[musicdiscs] Could not write datapack into world folder: " + e.getMessage());
		}
	}

	private static void copyRecursively(Path source, Path target) throws IOException {
		Files.createDirectories(target);
		Files.walkFileTree(source, new SimpleFileVisitor<>() {
			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
				Files.createDirectories(target.resolve(source.relativize(dir)));
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				Files.copy(file, target.resolve(source.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
				return FileVisitResult.CONTINUE;
			}
		});
	}
}

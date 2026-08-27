package com.musicdiscs;

import com.musicdiscs.config.WorldSelectionStore;
import com.musicdiscs.item.CurrentWorldContext;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;

/**
 * On every server start (fires for the integrated singleplayer server too,
 * which is what "Open to LAN" and normal singleplayer both use): load a
 * world's musicdiscs_selection.json (if any) and push the resolved
 * enabled-song-id set into CurrentWorldContext, so LootHooks knows which
 * discs are eligible in THIS world specifically, before loot tables get
 * built.
 *
 * jukebox_song data itself is NOT handled here -- see ServerPacksSourceMixin
 * for how the generated jukebox_song datapack (written by GeneratedPackWriter)
 * gets merged into Minecraft's data, including the early world-creation
 * preview that runs before any world folder (and so this class) exists.
 */
public class ServerDatapackInjector {

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTING.register(ServerDatapackInjector::onServerStarting);
	}

	private static void onServerStarting(MinecraftServer server) {
		Path saveRoot = server.getWorldPath(LevelResource.ROOT);
		try {
			WorldSelectionStore selection = WorldSelectionStore.load(saveRoot);
			CurrentWorldContext.set(selection.effectiveEnabledSet(MusicDiscsMod.LIBRARY));
		} catch (Exception e) {
			System.err.println("[musicdiscs] Could not resolve per-world disc selection, defaulting to all enabled: " + e.getMessage());
		}
	}
}

package com.musicdiscs;

import com.musicdiscs.config.WorldSelectionStore;
import com.musicdiscs.item.CurrentWorldContext;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;

/**
 * Runs on every server start (including the integrated server behind
 * singleplayer and "Open to LAN"): loads a world's
 * musicdiscs_selection.json (if any) and pushes the resolved
 * enabled-song-id set into CurrentWorldContext, so LootHooks knows which
 * discs are eligible in this world before loot tables get built.
 *
 * jukebox_song data is handled separately, in ServerPacksSourceMixin,
 * which merges the generated jukebox_song datapack (written by
 * GeneratedPackWriter) into Minecraft's data, including the early
 * world-creation preview that runs before any world folder (and so this
 * class) exists.
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

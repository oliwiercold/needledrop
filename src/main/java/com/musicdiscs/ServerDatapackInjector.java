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
 * There used to be a second job here -- copying a generated jukebox_song
 * datapack into the world's own datapacks/ folder, since jukebox_song is a
 * datapack-loaded registry rather than something registerable in Java. That
 * approach never worked: Minecraft builds a preview of default world
 * settings (using only vanilla + built-in mod data, no per-world folder at
 * all) the moment you open "Create New World", well before any world
 * folder exists to copy into -- confirmed by testing, including trying to
 * inject earlier via a mixin on world-storage opening, which still ran too
 * late. GeneratedPackWriter now writes the jukebox_song JSON straight into
 * this mod's own resource root instead (see its class doc), which is
 * already part of every resource build including that early preview, so no
 * per-world copy is needed anymore.
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

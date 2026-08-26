package com.musicdiscs.item;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Holds "which songIds are loot-eligible in the world that's currently
 * starting up", so LootHooks can filter by it. Populated by
 * ServerDatapackInjector's SERVER_STARTING hook, which runs (and so
 * populates this) before loot tables get (re)built for that world.
 *
 * KNOWN ROUGH EDGE: if this ordering assumption is wrong on your version --
 * i.e. loot for a freshly-loaded world doesn't reflect your per-world
 * selection -- running `/reload` once after the world's up will definitely
 * pick up the correct set, since by then SERVER_STARTING has already run.
 */
public class CurrentWorldContext {

	private static Set<String> enabledSongIds = null; // null = no restriction yet (shouldn't normally happen once a world's starting)

	public static void set(Set<String> songIds) {
		enabledSongIds = new LinkedHashSet<>(songIds);
	}

	public static boolean isEnabled(String songId) {
		return enabledSongIds == null || enabledSongIds.contains(songId);
	}
}

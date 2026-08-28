package com.musicdiscs.item;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Holds "which songIds are loot-eligible in the world that's currently
 * starting up" so LootHooks can filter by it. Populated by
 * ServerDatapackInjector's SERVER_STARTING hook, which runs (and so
 * populates this) before loot tables get built for that world.
 *
 * If that ordering assumption breaks on your version (loot for a
 * freshly-loaded world doesn't reflect the per-world selection), run
 * `/reload` once: SERVER_STARTING will have already run by then and the
 * reload picks up the right set.
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

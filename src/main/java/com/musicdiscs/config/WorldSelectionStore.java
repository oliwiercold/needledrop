package com.musicdiscs.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Per-world override of which songs are eligible in loot. Lives inside the
 * world's own save folder as musicdiscs_selection.json, so it travels with
 * the world if you copy/back it up, and different saves can have completely
 * different disc sets.
 *
 * `customized = false` (the default, and the state for any world that's
 * never been opened in the "Edit Per-World Discs" screen) means "just use
 * whatever's globally enabled in LibraryStore" -- so a brand new world
 * automatically gets your whole enabled library with zero extra steps.
 */
public class WorldSelectionStore {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String FILE_NAME = "musicdiscs_selection.json";

	public boolean customized = false;
	public Set<String> enabledSongIds = new LinkedHashSet<>();

	public static WorldSelectionStore load(Path worldSaveDir) {
		Path file = worldSaveDir.resolve(FILE_NAME);
		if (Files.exists(file)) {
			try {
				WorldSelectionStore loaded = GSON.fromJson(Files.readString(file), WorldSelectionStore.class);
				if (loaded != null) return loaded;
			} catch (IOException e) {
				System.err.println("[musicdiscs] Could not read " + file + ", using global selection: " + e.getMessage());
			}
		}
		return new WorldSelectionStore();
	}

	public void save(Path worldSaveDir) {
		try {
			Files.createDirectories(worldSaveDir);
			Files.writeString(worldSaveDir.resolve(FILE_NAME), GSON.toJson(this));
		} catch (IOException e) {
			System.err.println("[musicdiscs] Could not write " + worldSaveDir.resolve(FILE_NAME) + ": " + e.getMessage());
		}
	}

	/** Resolves the actual set of song ids that should be loot-eligible in this world. */
	public Set<String> effectiveEnabledSet(LibraryStore library) {
		if (!customized) {
			Set<String> result = new LinkedHashSet<>();
			for (var entry : library.songs.entrySet()) {
				if (entry.getValue().enabledGlobally) result.add(entry.getKey());
			}
			return result;
		}
		return enabledSongIds;
	}
}

package com.musicdiscs.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.musicdiscs.scan.DiscEntry;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Persists which songs are known and whether each is globally enabled
 * (i.e. eligible to show up in loot in any world that doesn't override it).
 * See WorldSelectionStore for per-world overrides. Lives at
 * .minecraft/config/musicdiscs_library.json so the in-game "Edit Library"
 * screen has something to show even before you press Rescan again.
 */
public class LibraryStore {

	public static class Info {
		public String title;
		public String artist;
		public double lengthSeconds;
		public boolean enabledGlobally = true;
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("musicdiscs_library.json");
	private static final Type MAP_TYPE = new TypeToken<LinkedHashMap<String, Info>>() {}.getType();

	/** songId ("song_xxxx") -> Info. Kept in insertion/scan order so the UI list is stable. */
	public final Map<String, Info> songs;

	private LibraryStore(Map<String, Info> songs) {
		this.songs = songs;
	}

	public static LibraryStore load() {
		if (Files.exists(PATH)) {
			try {
				String json = Files.readString(PATH);
				Map<String, Info> map = GSON.fromJson(json, MAP_TYPE);
				if (map != null) {
					return new LibraryStore(new LinkedHashMap<>(map));
				}
			} catch (IOException e) {
				System.err.println("[musicdiscs] Could not read library file, starting fresh: " + e.getMessage());
			}
		}
		return new LibraryStore(new LinkedHashMap<>());
	}

	public void save() {
		try {
			Files.createDirectories(PATH.getParent());
			Files.writeString(PATH, GSON.toJson(songs, MAP_TYPE));
		} catch (IOException e) {
			System.err.println("[musicdiscs] Could not write library file: " + e.getMessage());
		}
	}

	/**
	 * Folds freshly-scanned entries into the store: new songs are added
	 * (enabled by default), songs we already knew about keep whatever
	 * enabled/disabled flag was already saved for them. Doesn't remove
	 * entries for songs that vanished from the folder. They just stay
	 * listed (and harmless) until you clean them up by hand in the UI.
	 */
	public void mergeScanResults(List<DiscEntry> entries) {
		for (DiscEntry e : entries) {
			Info info = songs.computeIfAbsent(e.songId(), id -> new Info());
			info.title = e.title;
			info.artist = e.artist;
			info.lengthSeconds = e.lengthSeconds;
		}
		save();
	}

	/** The cached metadata for a song, or null if it's never been scanned before. */
	public Info get(String songId) {
		return songs.get(songId);
	}

	public boolean isEnabled(String songId) {
		Info info = songs.get(songId);
		return info == null || info.enabledGlobally;
	}
}

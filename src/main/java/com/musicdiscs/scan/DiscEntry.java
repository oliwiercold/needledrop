package com.musicdiscs.scan;

import java.awt.Color;
import java.nio.file.Path;

/**
 * Everything we know about one song once it's been scanned, converted and
 * had its cover colour extracted. `id` is a short stable hash of the source
 * file's absolute path, so re-scanning the same library gives the same
 * item/song ids instead of new ones every launch.
 */
public class DiscEntry {

	public final String id;
	public final Path sourceFile;
	public String title;
	public String artist;
	public double lengthSeconds;
	public Path convertedOgg;
	public Path iconPng;
	public Color labelColor = new Color(120, 120, 120);
	public boolean hasCoverArt;

	public DiscEntry(String id, Path sourceFile) {
		this.id = id;
		this.sourceFile = sourceFile;
		this.title = sourceFile.getFileName().toString();
		this.artist = "Unknown";
	}

	/** e.g. "song_a1b2c3d4" -- used as the jukebox_song registry id (namespaced as musicdiscs:song_a1b2c3d4). */
	public String songId() {
		return "song_" + id;
	}

	/** Item registry id for this disc, e.g. "disc_a1b2c3d4". */
	public String itemId() {
		return "disc_" + id;
	}

	/** Vanilla scales comparator output by "rarity" of the vanilla discs (1-15); we just pick something in a pleasant range based on length. */
	public int comparatorOutput() {
		int v = (int) Math.round(1 + (lengthSeconds % 15));
		return Math.max(1, Math.min(15, v));
	}
}

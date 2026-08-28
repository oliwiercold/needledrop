package com.musicdiscs.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Plain JSON config, stored at .minecraft/config/musicdiscs.json.
 * Loaded once at startup. If you change values while the game is running,
 * restart for them to take effect (the scan only happens once, at boot,
 * or when you press "Rescan" in the in-game menu).
 */
public class ModConfig {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("musicdiscs.json");

	/** Folder to scan for music. Defaults to the user's Windows Music folder. Can be changed via the in-game "Browse..." button too. */
	public String musicFolderPath = System.getProperty("user.home") + java.io.File.separator + "Music";

	/** Path to the ffmpeg executable. "ffmpeg" assumes it's on your PATH. */
	public String ffmpegPath = "ffmpeg";

	/**
	 * NOT a real "how many discs you're allowed" limit: there isn't one.
	 * This is purely a runaway-scan safety valve (e.g. in case musicFolderPath
	 * accidentally points at your whole hard drive). Leave it huge.
	 */
	public int maxDiscs = 1_000_000;

	/**
	 * Chance (0.0-1.0) that a hooked loot chest rolls a bonus disc, split by
	 * dimension. Nether and End structures default higher: their chests
	 * tend to be faster to reach once you're geared for that stage (bastions/
	 * fortresses, elytra + end cities), so a flat chance would make
	 * disc-finding dry up in the late game instead of staying worthwhile.
	 */
	public double lootChanceOverworld = 0.35;
	public double lootChanceNether = 0.5;
	public double lootChanceEnd = 0.6;

	/** File extensions we'll look at when scanning the music folder. */
	public String[] extensions = {"mp3", "flac", "wav", "m4a", "ogg", "opus"};

	public static ModConfig load() {
		if (Files.exists(PATH)) {
			try {
				String json = Files.readString(PATH);
				ModConfig cfg = GSON.fromJson(json, ModConfig.class);
				if (cfg != null) {
					return cfg;
				}
			} catch (IOException e) {
				System.err.println("[musicdiscs] Could not read config, using defaults: " + e.getMessage());
			}
		}
		ModConfig fresh = new ModConfig();
		fresh.save();
		return fresh;
	}

	public void save() {
		try {
			Files.createDirectories(PATH.getParent());
			Files.writeString(PATH, GSON.toJson(this));
		} catch (IOException e) {
			System.err.println("[musicdiscs] Could not write config: " + e.getMessage());
		}
	}
}

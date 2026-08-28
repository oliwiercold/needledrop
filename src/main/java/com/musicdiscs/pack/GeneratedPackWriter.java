package com.musicdiscs.pack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.musicdiscs.scan.DiscEntry;
import net.minecraft.SharedConstants;
import net.minecraft.server.packs.PackType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Turns the list of scanned/converted DiscEntry objects into:
 *
 *  1. A real resource pack under <gameDir>/resourcepacks/Needledrop/
 *     (sounds, item icons, item models, lang), which shows up under
 *     Options > Resource Packs; MusicDiscsClientMod auto-enables it. The
 *     folder name is also literally what shows as the pack's title in that
 *     screen (Minecraft derives it from the folder name, not pack.mcmeta),
 *     so it's picked with that in mind rather than left as an internal id.
 *
 *  2. jukebox_song datapack entries, written to an external folder
 *     (<gameDir>/musicdiscs_cache/datapack_template/) that
 *     ServerPacksSourceMixin merges into Minecraft's "vanilla" data. That
 *     data is present for every resource build, including the early
 *     world-creation preview (built before any world folder exists, from
 *     vanilla + built-in mod data only); see ServerPacksSourceMixin for
 *     details.
 *
 * Both are namespaced under "musicdiscs" and safe to regenerate on every
 * launch/rescan, we just overwrite them. Includes ALL scanned songs
 * regardless of enabled/disabled state: enable/disable only affects which
 * ones LootHooks offers as loot (see CurrentWorldContext), not whether the
 * asset/data exists.
 */
public class GeneratedPackWriter {

	private static final String NAMESPACE = "musicdiscs";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public static void write(List<DiscEntry> entries, Path resourcePackDir, Path datapackTemplateDir) {
		try {
			writeResourcePack(entries, resourcePackDir);
			writeJukeboxSongData(entries, datapackTemplateDir);
		} catch (IOException e) {
			System.err.println("[musicdiscs] Failed writing generated pack: " + e.getMessage());
		}
	}

	private static void writeResourcePack(List<DiscEntry> entries, Path root) throws IOException {
		Path assets = root.resolve("assets").resolve(NAMESPACE);
		Files.createDirectories(assets);

		JsonObject pack = new JsonObject();
		JsonObject packInner = new JsonObject();
		packInner.addProperty("description", "Disc icons, sounds & names for Needledrop (auto-generated, safe to delete)");
		int resourceFormat = currentPackFormatMajor(PackType.CLIENT_RESOURCES);
		packInner.addProperty("min_format", resourceFormat);
		packInner.addProperty("max_format", resourceFormat);
		pack.add("pack", packInner);
		writeJson(root.resolve("pack.mcmeta"), pack);

		// Shown next to the pack's title in the Resource Packs screen.
		// Bundled in the mod's own jar rather than generated, since it's
		// static regardless of what's in the user's library.
		Path packIcon = root.resolve("pack.png");
		if (!Files.exists(packIcon)) {
			try (java.io.InputStream in = GeneratedPackWriter.class.getResourceAsStream("/assets/musicdiscs/pack_icon.png")) {
				if (in != null) {
					Files.copy(in, packIcon, StandardCopyOption.REPLACE_EXISTING);
				}
			}
		}

		JsonObject soundsJson = new JsonObject();
		JsonObject langJson = new JsonObject();

		Path soundsDir = assets.resolve("sounds");
		Path texturesDir = assets.resolve("textures").resolve("item");
		Path modelsDir = assets.resolve("models").resolve("item");
		Path itemsDir = assets.resolve("items");
		Files.createDirectories(soundsDir);
		Files.createDirectories(texturesDir);
		Files.createDirectories(modelsDir);
		Files.createDirectories(itemsDir);

		for (DiscEntry e : entries) {
			if (e.convertedOgg != null && Files.exists(e.convertedOgg)) {
				Files.copy(e.convertedOgg, soundsDir.resolve(e.songId() + ".ogg"), StandardCopyOption.REPLACE_EXISTING);
				JsonObject soundEntry = new JsonObject();
				com.google.gson.JsonArray soundsArray = new com.google.gson.JsonArray();
				soundsArray.add(NAMESPACE + ":" + e.songId());
				soundEntry.add("sounds", soundsArray);
				soundsJson.add("music_disc." + e.songId(), soundEntry);
			}

			if (e.iconPng != null && Files.exists(e.iconPng)) {
				Files.copy(e.iconPng, texturesDir.resolve(e.itemId() + ".png"), StandardCopyOption.REPLACE_EXISTING);
			}

			JsonObject model = new JsonObject();
			model.addProperty("parent", "minecraft:item/generated");
			JsonObject textures = new JsonObject();
			textures.addProperty("layer0", NAMESPACE + ":item/" + e.itemId());
			model.add("textures", textures);
			writeJson(modelsDir.resolve(e.itemId() + ".json"), model);

			JsonObject itemDef = new JsonObject();
			JsonObject modelRef = new JsonObject();
			modelRef.addProperty("type", "minecraft:model");
			modelRef.addProperty("model", NAMESPACE + ":item/" + e.itemId());
			itemDef.add("model", modelRef);
			writeJson(itemsDir.resolve(e.itemId() + ".json"), itemDef);

			// Generic name, matching vanilla discs. The specific song shows as its own
			// tooltip line underneath, added automatically by the jukebox_playable
			// component from the JukeboxSong's own description (see writeJukeboxSongData).
			langJson.addProperty("item." + NAMESPACE + "." + e.itemId(), "Music Disc");
		}

		writeJson(assets.resolve("sounds.json"), soundsJson);
		Path langDir = assets.resolve("lang");
		Files.createDirectories(langDir);
		writeJson(langDir.resolve("en_us.json"), langJson);
	}

	private static void writeJukeboxSongData(List<DiscEntry> entries, Path root) throws IOException {
		Path data = root.resolve("data").resolve(NAMESPACE).resolve("jukebox_song");
		for (DiscEntry e : entries) {
			JsonObject song = new JsonObject();
			song.addProperty("sound_event", NAMESPACE + ":music_disc." + e.songId());
			JsonObject description = new JsonObject();
			description.addProperty("text", e.artist + " - " + e.title);
			song.add("description", description);
			song.addProperty("length_in_seconds", Math.max(1.0, e.lengthSeconds));
			song.addProperty("comparator_output", e.comparatorOutput());
			writeJson(data.resolve(e.songId() + ".json"), song);
		}
	}

	/**
	 * 26.2 introduced major.minor pack formats. Once the major exceeds
	 * PackFormat.lastPreMinorVersion(type) (64 for resources, 81 for data,
	 * we're at 88/107), the schema wants "min_format"/"max_format" instead of
	 * a bare "pack_format" + "supported_formats" pair (see
	 * PackFormat.IntermediaryFormat.validate in Minecraft's own source).
	 * min==max==the currently-running major stays correct across Minecraft
	 * updates with no hardcoded number to maintain, since we regenerate the
	 * pack fresh every launch/rescan anyway.
	 */
	private static int currentPackFormatMajor(PackType type) {
		return SharedConstants.getCurrentVersion().packVersion(type).major();
	}

	private static void writeJson(Path path, JsonObject json) throws IOException {
		Files.createDirectories(path.getParent());
		Files.writeString(path, GSON.toJson(json));
	}
}

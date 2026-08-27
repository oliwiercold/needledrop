package com.musicdiscs.item;

import com.musicdiscs.scan.DiscEntry;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.item.Rarity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registers a plain Item per disc, with the "jukebox_playable" component set
 * as a DEFAULT on the item itself -- so it plays its song straight out of
 * the creative inventory, a /give command, or loot, the same way vanilla's
 * own discs work.
 *
 * Registers one item per EVERY scanned song, regardless of enabled/disabled
 * state -- enable/disable (global or per-world) only controls whether
 * LootHooks offers it as loot, not whether the item exists. That keeps
 * registration (which can't change without a restart) separate from
 * selection (which can change anytime).
 *
 * `.jukeboxPlayable(songKey)` needs a REAL jukebox_song registry entry, not
 * just a Holder with the right data: actual jukebox playback resolves the
 * sound to play via registry.getId(song), which is identity-based and only
 * returns a real id for objects that are genuinely in the registry. See
 * ServerPacksSourceMixin for how the generated jukebox_song data reaches
 * that registry.
 */
public class ModItems {

	public static final String MODID = "musicdiscs";

	/** itemId (e.g. "disc_a1b2c3d4") -> registered Item, kept around for the loot table hook. */
	public static final Map<String, Item> REGISTERED = new LinkedHashMap<>();

	public static void registerAll(List<DiscEntry> entries) {
		for (DiscEntry entry : entries) {
			Identifier id = Identifier.fromNamespaceAndPath(MODID, entry.itemId());
			ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id);

			Identifier soundId = Identifier.fromNamespaceAndPath(MODID, "music_disc." + entry.songId());
			Registry.register(BuiltInRegistries.SOUND_EVENT, soundId, SoundEvent.createVariableRangeEvent(soundId));

			ResourceKey<JukeboxSong> songKey = ResourceKey.create(Registries.JUKEBOX_SONG, Identifier.fromNamespaceAndPath(MODID, entry.songId()));

			Item item = new Item(new Item.Properties().setId(itemKey).stacksTo(1).rarity(Rarity.UNCOMMON).jukeboxPlayable(songKey));
			Registry.register(BuiltInRegistries.ITEM, itemKey, item);
			REGISTERED.put(entry.itemId(), item);
		}

		System.out.println("[musicdiscs] Registered " + REGISTERED.size() + " disc items.");

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> {
			for (Item item : REGISTERED.values()) {
				output.accept(item);
			}
		});
	}

	/** itemId ("disc_xxxx") -> songId ("song_xxxx"), the reverse of what REGISTERED gives you -- LootHooks needs this to check enabled state. */
	public static String songIdForItemId(String itemId) {
		return "song_" + itemId.substring("disc_".length());
	}
}

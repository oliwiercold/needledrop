package com.musicdiscs.item;

import com.musicdiscs.scan.DiscEntry;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.JukeboxSong;

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
 * NOTE ON RISK: `.jukeboxPlayable(...)` mirrors how vanilla's own
 * Items.java registers MUSIC_DISC_13 etc. in Mojang-mapped source, but this
 * whole feature is new as of 1.21, so if this specific line fails to
 * compile, that's the single most likely spot in the whole project. Fixes
 * to try, in order:
 *   1. `.component(DataComponents.JUKEBOX_PLAYABLE, new JukeboxPlayable(Optional.of(songKey), true))`
 *      (import net.minecraft.world.item.JukeboxPlayable, net.minecraft.core.component.DataComponents)
 *   2. Drop the default component from here entirely, and instead give the
 *      song via a loot table "set_components" JSON function (see
 *      https://datapack.wiki/guide/adding-new-features/jukebox-songs for the
 *      exact field names).
 */
public class ModItems {

	public static final String MODID = "musicdiscs";

	/** itemId (e.g. "disc_a1b2c3d4") -> registered Item, kept around for the loot table hook. */
	public static final Map<String, Item> REGISTERED = new LinkedHashMap<>();

	public static void registerAll(List<DiscEntry> entries) {
		for (DiscEntry entry : entries) {
			ResourceLocation id = ResourceLocation.fromNamespaceAndPath(MODID, entry.itemId());
			ResourceKey<JukeboxSong> songKey = ResourceKey.create(
					Registries.JUKEBOX_SONG,
					ResourceLocation.fromNamespaceAndPath(MODID, entry.songId())
			);
			Item item = new Item(new Item.Properties().stacksTo(1).jukeboxPlayable(songKey));
			Registry.register(BuiltInRegistries.ITEM, id, item);
			REGISTERED.put(entry.itemId(), item);
		}

		System.out.println("[musicdiscs] Registered " + REGISTERED.size() + " disc items.");

		ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(entries2 -> {
			for (Item item : REGISTERED.values()) {
				entries2.accept(item);
			}
		});
	}

	/** itemId ("disc_xxxx") -> songId ("song_xxxx"), the reverse of what REGISTERED gives you -- LootHooks needs this to check enabled state. */
	public static String songIdForItemId(String itemId) {
		return "song_" + itemId.substring("disc_".length());
	}
}

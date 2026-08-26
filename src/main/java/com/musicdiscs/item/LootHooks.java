package com.musicdiscs.item;

import com.musicdiscs.config.ModConfig;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

import java.util.Map;

/**
 * Adds a bonus loot pool (gated by config.lootChance, default 15%) to a
 * handful of vanilla chest loot tables. The pool has one roll and one entry
 * per CURRENTLY ELIGIBLE disc (see CurrentWorldContext) with equal weight,
 * so on a successful roll you get exactly one random disc from whatever
 * subset is active in this world -- adding more enabled songs makes the
 * loot more varied, not more common.
 */
public class LootHooks {

	private static final Identifier[] TARGET_TABLES = {
			BuiltInLootTables.ABANDONED_MINESHAFT.identifier(),
			BuiltInLootTables.END_CITY_TREASURE.identifier(),
			BuiltInLootTables.STRONGHOLD_CORRIDOR.identifier(),
			BuiltInLootTables.SIMPLE_DUNGEON.identifier(),
			BuiltInLootTables.BURIED_TREASURE.identifier(),
	};

	public static void register(ModConfig config) {
		LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
			if (!source.isBuiltin() || ModItems.REGISTERED.isEmpty()) return;

			for (Identifier target : TARGET_TABLES) {
				if (key.identifier().equals(target)) {
					LootPool.Builder pool = LootPool.lootPool()
							.setRolls(ConstantValue.exactly(1))
							.when(LootItemRandomChanceCondition.randomChance((float) config.lootChance));

					int added = 0;
					for (Map.Entry<String, Item> e : ModItems.REGISTERED.entrySet()) {
						String songId = ModItems.songIdForItemId(e.getKey());
						if (CurrentWorldContext.isEnabled(songId)) {
							pool.add(LootItem.lootTableItem(e.getValue()));
							added++;
						}
					}

					if (added > 0) tableBuilder.withPool(pool);
					break;
				}
			}
		});

		System.out.println("[musicdiscs] Loot table hook registered (chance=" + config.lootChance + ").");
	}
}

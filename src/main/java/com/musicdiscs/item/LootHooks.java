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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Adds a bonus loot pool to a handful of vanilla chest loot tables, gated
 * by a per-dimension chance from ModConfig (see there for why Nether/End
 * default higher than Overworld). The pool has one roll and one entry per
 * CURRENTLY ELIGIBLE disc (see CurrentWorldContext) with equal weight, so
 * on a successful roll you get exactly one random disc from whatever
 * subset is active in this world -- adding more enabled songs makes the
 * loot more varied, not more common.
 */
public class LootHooks {

	public static void register(ModConfig config) {
		Map<Identifier, Double> targetTables = new LinkedHashMap<>();
		targetTables.put(BuiltInLootTables.ABANDONED_MINESHAFT.identifier(), config.lootChanceOverworld);
		targetTables.put(BuiltInLootTables.STRONGHOLD_CORRIDOR.identifier(), config.lootChanceOverworld);
		targetTables.put(BuiltInLootTables.SIMPLE_DUNGEON.identifier(), config.lootChanceOverworld);
		targetTables.put(BuiltInLootTables.BURIED_TREASURE.identifier(), config.lootChanceOverworld);
		targetTables.put(BuiltInLootTables.NETHER_BRIDGE.identifier(), config.lootChanceNether);
		targetTables.put(BuiltInLootTables.BASTION_TREASURE.identifier(), config.lootChanceNether);
		targetTables.put(BuiltInLootTables.BASTION_OTHER.identifier(), config.lootChanceNether);
		targetTables.put(BuiltInLootTables.BASTION_BRIDGE.identifier(), config.lootChanceNether);
		targetTables.put(BuiltInLootTables.BASTION_HOGLIN_STABLE.identifier(), config.lootChanceNether);
		targetTables.put(BuiltInLootTables.END_CITY_TREASURE.identifier(), config.lootChanceEnd);

		LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
			if (!source.isBuiltin() || ModItems.REGISTERED.isEmpty()) return;

			Double chance = targetTables.get(key.identifier());
			if (chance == null) return;

			LootPool.Builder pool = LootPool.lootPool()
					.setRolls(ConstantValue.exactly(1))
					.when(LootItemRandomChanceCondition.randomChance(chance.floatValue()));

			int added = 0;
			for (Map.Entry<String, Item> e : ModItems.REGISTERED.entrySet()) {
				String songId = ModItems.songIdForItemId(e.getKey());
				if (CurrentWorldContext.isEnabled(songId)) {
					pool.add(LootItem.lootTableItem(e.getValue()));
					added++;
				}
			}

			if (added > 0) tableBuilder.withPool(pool);
		});

		System.out.println("[musicdiscs] Loot table hook registered (overworld=" + config.lootChanceOverworld
				+ ", nether=" + config.lootChanceNether + ", end=" + config.lootChanceEnd + ").");
	}
}

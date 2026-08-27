package com.musicdiscs;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.server.packs.repository.PackRepository;

/**
 * Deliberately does almost nothing itself -- the menu button lives in
 * TitleScreenMixin, and the screens under com.musicdiscs.gui do the rest.
 * All the scan/convert/register work happens in MusicDiscsMod's common
 * onInitialize(), which only runs its scan on the physical client.
 */
public class MusicDiscsClientMod implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		System.out.println("[musicdiscs] Client loaded. Look for the note button next to Singleplayer on the title screen.");

		// Auto-enable the generated resource pack (icons/sounds/item names) instead of
		// requiring a manual trip to Options > Resource Packs. CLIENT_STARTED fires once
		// Minecraft.getInstance() actually exists; mod init runs earlier than that, so
		// this can't be done directly in onInitializeClient().
		ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
			PackRepository packs = client.getResourcePackRepository();
			String packId = "file/Needledrop";
			if (packs.isAvailable(packId) && !packs.getSelectedIds().contains(packId)) {
				packs.addPack(packId);
				client.reloadResourcePacks();
				System.out.println("[musicdiscs] Auto-enabled the '" + packId + "' resource pack.");
			}
		});
	}
}

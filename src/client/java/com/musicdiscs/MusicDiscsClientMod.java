package com.musicdiscs;

import net.fabricmc.api.ClientModInitializer;

/**
 * Deliberately does almost nothing itself -- the menu button lives in
 * TitleScreenMixin, and the screens under com.musicdiscs.gui do the rest.
 * All the scan/convert/register work happens in MusicDiscsMod's common
 * onInitialize(), which only runs its scan on the physical client.
 *
 * We don't try to programmatically auto-enable the generated resource pack
 * here -- that touches client rendering-adjacent API we can't compile-test
 * up front, and a mistake would risk crashing the game at launch. It's not
 * worth that risk: the pack already shows up in Options > Resource Packs on
 * its own, it just needs one click to move it to "Selected" the first time.
 */
public class MusicDiscsClientMod implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		System.out.println("[musicdiscs] Client loaded. Title screen should show a 'Music Discs' button " +
				"in the bottom-left corner. If discs are silent/textureless, check Options > Resource Packs.");
	}
}

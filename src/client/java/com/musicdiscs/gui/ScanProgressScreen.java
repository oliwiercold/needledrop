package com.musicdiscs.gui;

import com.musicdiscs.MusicDiscsMod;
import com.musicdiscs.scan.DiscEntry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Shown while runScanAndConvert() does its work on a background thread (it
 * shells out to ffmpeg per file, which is too slow to run on the render
 * thread without freezing the game). `progress`/`done`/`result` are read on
 * the render thread and written from the background thread -- kept as plain
 * volatiles rather than routed through Minecraft#execute since they're
 * simple reads, not GUI mutations; only the actual screen swap at the end
 * needs to happen on the main thread, via tick().
 */
public class ScanProgressScreen extends Screen {

	private final Screen grandparent; // where "Done" on the menu should return to, once we're back at the menu

	private volatile int progress = 0;
	private volatile boolean done = false;
	private volatile List<DiscEntry> result;

	public ScanProgressScreen(Screen grandparent) {
		super(Component.literal("Scanning Music Folder"));
		this.grandparent = grandparent;
	}

	@Override
	protected void init() {
		Thread t = new Thread(() -> {
			List<DiscEntry> entries = MusicDiscsMod.runScanAndConvert(
					MusicDiscsMod.CONFIG, MusicDiscsMod.LIBRARY, p -> progress = p);
			result = entries;
			done = true;
		}, "musicdiscs-scan");
		t.setDaemon(true);
		t.start();
	}

	@Override
	public void tick() {
		if (done && this.minecraft != null) {
			MusicDiscsMod.ENTRIES = result;
			this.minecraft.setScreen(new MusicDiscsMenuScreen(grandparent));
		}
	}

	@Override
	public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
		super.render(gfx, mouseX, mouseY, partialTick);
		gfx.drawCenteredString(this.font, "Scanning & converting... " + progress + "%",
				this.width / 2, this.height / 2 - 10, 0xFFFFFF);
		gfx.drawCenteredString(this.font, "Big libraries take a while -- ffmpeg is doing real work per file.",
				this.width / 2, this.height / 2 + 10, 0xA0A0A0);
		if (!MusicDiscsMod.isReadyForItemRegistration()) {
			gfx.drawCenteredString(this.font, "Note: brand new songs need a relaunch before they're usable items.",
					this.width / 2, this.height / 2 + 26, 0x808080);
		}
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false; // don't let the player back out mid-scan into a half-written cache
	}
}

package com.musicdiscs.gui;

import com.musicdiscs.MusicDiscsMod;
import com.musicdiscs.scan.DiscEntry;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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

	/**
	 * Rescanning an already-fully-cached library (nothing new to convert)
	 * finishes in well under a second -- without a floor, the screen would
	 * flash and vanish before it's even readable. This just makes sure
	 * "Done!" stays up long enough to actually see.
	 */
	private static final long MIN_VISIBLE_MILLIS = 1200;

	private final Screen grandparent; // where "Done" on the menu should return to, once we're back at the menu

	private volatile int progress = 0;
	private volatile boolean done = false;
	private volatile List<DiscEntry> result;
	private long shownAt;

	public ScanProgressScreen(Screen grandparent) {
		super(Component.literal("Scanning Music Folder"));
		this.grandparent = grandparent;
	}

	@Override
	protected void init() {
		this.shownAt = System.currentTimeMillis();
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
		if (done && this.minecraft != null && System.currentTimeMillis() - shownAt >= MIN_VISIBLE_MILLIS) {
			MusicDiscsMod.ENTRIES = result;
			this.minecraft.gui.setScreen(new MusicDiscsMenuScreen(grandparent));
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(gfx, mouseX, mouseY, partialTick);
		if (done) {
			gfx.centeredText(this.font, "Done! " + result.size() + " discs ready.",
					this.width / 2, this.height / 2 - 10, 0xFF55FF55);
		} else {
			gfx.centeredText(this.font, "Scanning & converting... " + progress + "%",
					this.width / 2, this.height / 2 - 10, 0xFFFFFFFF);
		}
		gfx.centeredText(this.font, "Big libraries take a while -- ffmpeg is doing real work per file.",
				this.width / 2, this.height / 2 + 10, 0xFFA0A0A0);
		if (MusicDiscsMod.itemsAlreadyRegistered()) {
			gfx.centeredText(this.font, "Note: brand new songs need a relaunch before they're usable items.",
					this.width / 2, this.height / 2 + 26, 0xFF808080);
		}
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false; // don't let the player back out mid-scan into a half-written cache
	}
}

package com.musicdiscs.gui;

import com.musicdiscs.MusicDiscsMod;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The hub screen opened by the title-screen "Music Discs" button (see
 * TitleScreenMixin). Everything else (scanning, the library checklist,
 * per-world selection) branches off from here.
 */
public class MusicDiscsMenuScreen extends Screen {

	private final Screen parent;

	public MusicDiscsMenuScreen(Screen parent) {
		super(Component.literal("Music Discs From Folder"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int cx = this.width / 2;
		int y = this.height / 2 - 70;

		this.addRenderableWidget(Button.builder(Component.literal("Browse music folder..."), b ->
				NativeFolderPicker.pick(MusicDiscsMod.CONFIG.musicFolderPath, picked -> {
					MusicDiscsMod.CONFIG.musicFolderPath = picked;
					MusicDiscsMod.CONFIG.save();
				})
		).bounds(cx - 100, y, 200, 20).build());

		y += 24;
		this.addRenderableWidget(Button.builder(Component.literal("Rescan & Convert"), b ->
				this.minecraft.setScreen(new ScanProgressScreen(parent))
		).bounds(cx - 100, y, 200, 20).build());

		y += 24;
		this.addRenderableWidget(Button.builder(Component.literal("Edit Library"), b ->
				this.minecraft.setScreen(new SongListScreen(this, null))
		).bounds(cx - 100, y, 200, 20).build());

		y += 24;
		this.addRenderableWidget(Button.builder(Component.literal("Edit Per-World Discs"), b ->
				this.minecraft.setScreen(new WorldSelectScreen(this))
		).bounds(cx - 100, y, 200, 20).build());

		y += 24;
		this.addRenderableWidget(Button.builder(Component.literal("Done"), b ->
				this.minecraft.setScreen(parent)
		).bounds(cx - 100, y, 200, 20).build());
	}

	@Override
	public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
		super.render(gfx, mouseX, mouseY, partialTick);
		gfx.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 90, 0xFFFFFF);
		gfx.drawCenteredString(this.font, "Folder: " + MusicDiscsMod.CONFIG.musicFolderPath, this.width / 2, this.height - 30, 0xA0A0A0);
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(parent);
	}
}

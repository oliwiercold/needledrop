package com.musicdiscs.gui;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Lists your existing saves (just the folder names under .minecraft/saves --
 * doesn't use LevelStorageSource, so it also doesn't know about display
 * names/icons the way the vanilla world list does, only raw folder names).
 * Picking one opens SongListScreen scoped to that save's own selection
 * file, so you can revisit and change it anytime, not just at world
 * creation.
 *
 * Known simplification: no scrolling here -- past roughly a dozen saves the
 * list just stops adding buttons rather than overflowing off-screen. Worth
 * upgrading to the same manual-scroll approach as SongListScreen if you
 * have a lot of worlds.
 */
public class WorldSelectScreen extends Screen {

	private final Screen parent;
	private List<Path> saves = List.of();

	public WorldSelectScreen(Screen parent) {
		super(Component.literal("Choose a World"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		Path savesDir = FabricLoader.getInstance().getGameDir().resolve("saves");
		try (var stream = Files.list(savesDir)) {
			saves = stream.filter(Files::isDirectory).collect(Collectors.toList());
		} catch (IOException e) {
			saves = List.of();
		}

		int y = 30;
		for (Path save : saves) {
			String name = save.getFileName().toString();
			this.addRenderableWidget(Button.builder(Component.literal(name), b ->
					this.minecraft.gui.setScreen(new SongListScreen(this, save))
			).bounds(this.width / 2 - 100, y, 200, 20).build());
			y += 24;
			if (y > this.height - 60) break;
		}

		this.addRenderableWidget(Button.builder(Component.literal("Back"), b -> this.minecraft.gui.setScreen(parent))
				.bounds(this.width / 2 - 100, this.height - 28, 200, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(gfx, mouseX, mouseY, partialTick);
		gfx.centeredText(this.font, this.title, this.width / 2, 8, 0xFFFFFFFF);
		if (saves.isEmpty()) {
			gfx.centeredText(this.font, "No saves found yet -- create a world first.", this.width / 2, this.height / 2, 0xFFA0A0A0);
		}
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(parent);
	}
}

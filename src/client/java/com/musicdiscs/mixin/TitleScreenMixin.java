package com.musicdiscs.mixin;

import com.musicdiscs.gui.MusicDiscsMenuScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds the "Music Discs" button to the title screen, right after the
 * "Singleplayer" button (same row -- and after any other button already
 * sharing that row, e.g. the dev-only "TW" test-world button, so we never
 * overlap it). Found by matching the resolved button label text rather than
 * hardcoding vanilla's internal layout math, which shifts release to
 * release.
 *
 * Icon-only button (empty label): a white musical note drawn with a light
 * blue "shadow" note offset behind it, painted by hand in
 * extractRenderState since a plain Button only supports a single flat
 * label colour.
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {

	private @Nullable Button musicdiscs$button;

	protected TitleScreenMixin(Component title) {
		super(title);
	}

	@Inject(method = "init", at = @At("TAIL"))
	private void musicdiscs$addButton(CallbackInfo ci) {
		String singleplayerLabel = Component.translatable("menu.singleplayer").getString();

		AbstractWidget singleplayerButton = null;
		for (var child : this.children()) {
			if (child instanceof AbstractWidget widget && widget.getMessage().getString().equals(singleplayerLabel)) {
				singleplayerButton = widget;
				break;
			}
		}
		if (singleplayerButton == null) return; // demo mode etc. has no such button -- just skip

		int rowY = singleplayerButton.getY();
		int rightEdge = singleplayerButton.getX() + singleplayerButton.getWidth();
		for (var child : this.children()) {
			if (child instanceof AbstractWidget widget && widget.getY() == rowY) {
				rightEdge = Math.max(rightEdge, widget.getX() + widget.getWidth());
			}
		}

		this.musicdiscs$button = this.addRenderableWidget(
				Button.builder(Component.literal(""), b -> this.minecraft.gui.setScreen(new MusicDiscsMenuScreen(this)))
						.bounds(rightEdge + 4, rowY, 20, 20)
						.tooltip(Tooltip.create(Component.literal("Needledrop")))
						.build()
		);
	}

	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void musicdiscs$drawNoteIcon(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		if (this.musicdiscs$button == null) return;
		int cx = this.musicdiscs$button.getX() + this.musicdiscs$button.getWidth() / 2;
		int cy = this.musicdiscs$button.getY() + this.musicdiscs$button.getHeight() / 2 - 4;
		String note = "♪";
		int halfWidth = this.font.width(note) / 2;
		// dropShadow=false on both -- centeredText() always draws with a
		// shadow, which is a hardcoded dark colour baked in underneath
		// regardless of what colour we ask for, muddying the intended
		// light-blue "shadow" note into grey. Centering manually here so we
		// can call the no-shadow overload directly.
		gfx.text(this.font, note, cx + 1 - halfWidth, cy + 1, 0xFF90D5FF, false);
		gfx.text(this.font, note, cx - halfWidth, cy, 0xFFFFFFFF, false);
	}
}

package com.musicdiscs.mixin;

import com.musicdiscs.gui.MusicDiscsMenuScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds the "Music Discs" button to the title screen. Anchored to the
 * bottom-left corner deliberately, rather than slotted into vanilla's
 * button stack -- that avoids needing to replicate TitleScreen's exact
 * internal layout math (which shifts a bit release to release) just to
 * avoid overlapping it.
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {

	protected TitleScreenMixin(Component title) {
		super(title);
	}

	@Inject(method = "init", at = @At("TAIL"))
	private void musicdiscs$addButton(CallbackInfo ci) {
		this.addRenderableWidget(Button.builder(Component.literal("Music Discs"), b ->
				this.minecraft.setScreen(new MusicDiscsMenuScreen(this))
		).bounds(4, this.height - 24, 100, 20).build());
	}
}

package com.musicdiscs.gui;

import net.minecraft.client.Minecraft;

import javax.swing.JFileChooser;
import javax.swing.UIManager;
import java.util.function.Consumer;

/**
 * Wraps a native (Swing) folder-picker dialog. JFileChooser blocks the
 * calling thread until the user picks something or cancels, so we run it on
 * its own background thread and marshal the result back onto Minecraft's
 * main thread via Minecraft#execute. Never touch game/GUI state directly
 * from the picker thread.
 *
 * The dialog looks like a plain Windows/Swing window, not a Minecraft-styled
 * screen. That's fine: a fully in-game-styled file browser is a lot more
 * work and wasn't worth it for a first pass.
 */
public class NativeFolderPicker {

	public static void pick(String startingPath, Consumer<String> onPicked) {
		Thread t = new Thread(() -> {
			try {
				try {
					UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
				} catch (Exception ignored) {
					// Cosmetic only, falls back to the default Swing look if this fails.
				}

				JFileChooser chooser = new JFileChooser(startingPath);
				chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
				chooser.setDialogTitle("Choose your Music folder");

				int result = chooser.showOpenDialog(null);
				if (result == JFileChooser.APPROVE_OPTION) {
					String picked = chooser.getSelectedFile().getAbsolutePath();
					Minecraft.getInstance().execute(() -> onPicked.accept(picked));
				}
			} catch (Exception e) {
				System.err.println("[musicdiscs] Folder picker failed: " + e);
			}
		}, "musicdiscs-folder-picker");
		t.setDaemon(true);
		t.start();
	}
}

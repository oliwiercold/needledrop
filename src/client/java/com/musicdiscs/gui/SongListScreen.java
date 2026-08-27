package com.musicdiscs.gui;

import com.musicdiscs.MusicDiscsMod;
import com.musicdiscs.config.LibraryStore;
import com.musicdiscs.config.WorldSelectionStore;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Shared checklist UI for both "Edit Library" (global enable/disable, when
 * worldSaveDir is null) and "Edit Per-World Discs" (one save's override,
 * when worldSaveDir points at that save's folder).
 *
 * HIGHEST-RISK FILE IN THE WHOLE PROJECT. Two spots to watch if this
 * doesn't compile:
 *
 *  1. Checkbox.builder(...) -- I'm not 100% certain 26.2 uses a builder here
 *     the way Button does. If it doesn't exist, fall back to the older
 *     direct constructor:
 *       new Checkbox(x, y, 200, 20, Component.literal(label), selected) {
 *           @Override public void onPress() {
 *               super.onPress();
 *               if (this.selected()) workingEnabled.add(songId); else workingEnabled.remove(songId);
 *           }
 *       }
 *     (exact Checkbox constructor param order/names may still need a small
 *     tweak either way -- check whatever IntelliJ's autocomplete offers).
 *
 *  2. `this.removeWidget(...)` below (used to swap just the checkbox rows
 *     out) is a real Screen method as far as I know, but if it's missing or
 *     named differently on 26.2, the fallback is `this.clearWidgets()` +
 *     rebuilding EVERYTHING in init() -- just be aware that reintroduces
 *     the bug this version was rewritten to avoid (see the note below).
 *
 * FIXED ON REVIEW: the first version of this file called a full
 * clearWidgets()+init() rebuild from *inside* the search box's own
 * setResponder callback -- i.e. every keystroke destroyed and recreated the
 * text field that was still in the middle of handling that keystroke. That
 * risks losing focus after every character (or worse). This version only
 * ever tears down and re-adds the checkbox ROWS (refreshRows()); the search
 * box, and the Select All/None/Save/Back buttons, are created once in
 * init() and never touched again.
 */
public class SongListScreen extends Screen {

	private static final int ROWS_VISIBLE = 8;
	private static final int ROW_HEIGHT = 20;
	private static final int LIST_TOP = 76;

	private final Screen parent;
	private final Path worldSaveDir; // null = editing the global library
	private final LibraryStore library = MusicDiscsMod.LIBRARY;

	private WorldSelectionStore worldSelection;
	private Set<String> workingEnabled;
	private String searchText = "";
	private int scrollOffset = 0;
	private final List<String> filteredIds = new ArrayList<>();
	private final List<Checkbox> rowWidgets = new ArrayList<>();

	public SongListScreen(Screen parent, Path worldSaveDir) {
		super(Component.literal(worldSaveDir == null ? "Edit Library" : "Edit Discs For This World"));
		this.parent = parent;
		this.worldSaveDir = worldSaveDir;
	}

	@Override
	protected void init() {
		if (worldSaveDir != null && worldSelection == null) {
			worldSelection = WorldSelectionStore.load(worldSaveDir);
			workingEnabled = new LinkedHashSet<>(worldSelection.effectiveEnabledSet(library));
		} else if (workingEnabled == null) {
			workingEnabled = new LinkedHashSet<>();
			for (var e : library.songs.entrySet()) {
				if (e.getValue().enabledGlobally) workingEnabled.add(e.getKey());
			}
		}

		recomputeFiltered();
		rowWidgets.clear(); // init() only runs fresh (screen resize, first open) -- old Checkbox objects from before are gone with it either way

		EditBox search = new EditBox(this.font, this.width / 2 - 100, 24, 200, 20, Component.literal("Search"));
		search.setValue(searchText);
		search.setResponder(value -> {
			searchText = value;
			scrollOffset = 0;
			recomputeFiltered();
			refreshRows();
		});
		this.addRenderableWidget(search);
		this.setInitialFocus(search);

		this.addRenderableWidget(Button.builder(Component.literal("Select All"), b -> {
			workingEnabled.addAll(filteredIds);
			refreshRows();
		}).bounds(this.width / 2 - 152, 48, 100, 20).build());

		this.addRenderableWidget(Button.builder(Component.literal("Select None"), b -> {
			workingEnabled.removeAll(filteredIds);
			refreshRows();
		}).bounds(this.width / 2 - 50, 48, 100, 20).build());

		this.addRenderableWidget(Button.builder(Component.literal("Save"), b -> save())
				.bounds(this.width / 2 + 52, 48, 100, 20).build());

		refreshRows();

		this.addRenderableWidget(Button.builder(Component.literal("Back"), b -> this.minecraft.gui.setScreen(parent))
				.bounds(this.width / 2 - 100, this.height - 28, 200, 20).build());
	}

	private void recomputeFiltered() {
		filteredIds.clear();
		String needle = searchText.toLowerCase(Locale.ROOT);
		for (var e : library.songs.entrySet()) {
			String label = (e.getValue().artist + " - " + e.getValue().title).toLowerCase(Locale.ROOT);
			if (needle.isBlank() || label.contains(needle)) {
				filteredIds.add(e.getKey());
			}
		}
	}

	/**
	 * Swaps out just the checkbox rows for the current filter/scroll state.
	 * Deliberately does NOT touch the search box or the other buttons, so
	 * this is safe to call from inside their own callbacks (see the class
	 * javadoc for why that distinction matters).
	 */
	private void refreshRows() {
		for (Checkbox cb : rowWidgets) {
			this.removeWidget(cb);
		}
		rowWidgets.clear();

		int end = Math.min(filteredIds.size(), scrollOffset + ROWS_VISIBLE);
		for (int i = scrollOffset; i < end; i++) {
			String songId = filteredIds.get(i);
			LibraryStore.Info info = library.songs.get(songId);
			String label = info != null ? (info.artist + " - " + info.title) : songId;
			boolean checked = workingEnabled.contains(songId);
			int y = LIST_TOP + (i - scrollOffset) * ROW_HEIGHT;

			Checkbox cb = Checkbox.builder(Component.literal(label), this.font)
					.pos(this.width / 2 - 150, y)
					.selected(checked)
					.onValueChange((box, value) -> {
						if (value) workingEnabled.add(songId); else workingEnabled.remove(songId);
					})
					.build();
			rowWidgets.add(cb);
			this.addRenderableWidget(cb);
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		int maxOffset = Math.max(0, filteredIds.size() - ROWS_VISIBLE);
		int newOffset = Math.max(0, Math.min(maxOffset, scrollOffset - (int) Math.signum(scrollY)));
		if (newOffset != scrollOffset) {
			scrollOffset = newOffset;
			refreshRows();
		}
		return true;
	}

	private void save() {
		if (worldSaveDir == null) {
			for (var e : library.songs.entrySet()) {
				e.getValue().enabledGlobally = workingEnabled.contains(e.getKey());
			}
			library.save();
		} else {
			worldSelection.customized = true;
			worldSelection.enabledSongIds = workingEnabled;
			worldSelection.save(worldSaveDir);
		}
		this.minecraft.gui.setScreen(parent);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(gfx, mouseX, mouseY, partialTick);
		gfx.centeredText(this.font, this.title, this.width / 2, 8, 0xFFFFFFFF);
		String status = filteredIds.size() + " songs shown, " + workingEnabled.size() + " enabled"
				+ (filteredIds.size() > ROWS_VISIBLE ? " -- scroll for more" : "");
		gfx.centeredText(this.font, status, this.width / 2, this.height - 46, 0xFFA0A0A0);
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(parent);
	}
}

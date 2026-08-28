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
 * refreshRows() only tears down and re-adds the checkbox rows, never the
 * search box or the other buttons, so it's safe to call from inside the
 * search box's own setResponder callback without disrupting whatever
 * keystroke is still being handled.
 */
public class SongListScreen extends Screen {

	private static final int ROW_HEIGHT = 20;
	private static final int LIST_TOP = 76;
	// Space reserved below the list for the status line and the Back button,
	// so rowsVisible (computed per-screen-size in init()) never overlaps them.
	private static final int BOTTOM_RESERVED = 54;

	private final Screen parent;
	private final Path worldSaveDir; // null = editing the global library
	private final LibraryStore library = MusicDiscsMod.LIBRARY;

	private WorldSelectionStore worldSelection;
	private Set<String> workingEnabled;
	private String searchText = "";
	private int scrollOffset = 0;
	private int rowsVisible = 8; // recalculated in init() from the actual window height
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

		rowsVisible = Math.max(1, (this.height - BOTTOM_RESERVED - LIST_TOP) / ROW_HEIGHT);
		recomputeFiltered();
		clampScrollOffset();
		rowWidgets.clear(); // init() runs fresh on every resize, so old Checkbox objects are already gone

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

	private void clampScrollOffset() {
		int maxOffset = Math.max(0, filteredIds.size() - rowsVisible);
		scrollOffset = Math.max(0, Math.min(maxOffset, scrollOffset));
	}

	/**
	 * Swaps out just the checkbox rows for the current filter/scroll state.
	 * Doesn't touch the search box or the other buttons, so it's safe to
	 * call from inside their own callbacks (see the search box's
	 * setResponder above).
	 */
	private void refreshRows() {
		for (Checkbox cb : rowWidgets) {
			this.removeWidget(cb);
		}
		rowWidgets.clear();

		int end = Math.min(filteredIds.size(), scrollOffset + rowsVisible);
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
		int maxOffset = Math.max(0, filteredIds.size() - rowsVisible);
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
				+ (filteredIds.size() > rowsVisible ? " (scroll for more)" : "");
		gfx.centeredText(this.font, status, this.width / 2, this.height - 46, 0xFFA0A0A0);
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(parent);
	}
}

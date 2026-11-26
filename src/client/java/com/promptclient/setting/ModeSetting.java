package com.promptclient.setting;

import java.util.List;
import java.util.function.Supplier;

/** One choice out of a fixed list of modes. Left-click cycles forward in the GUI, right-click backward. */
public final class ModeSetting extends Setting {
	private final List<String> modes;
	private String currentMode;
	private int index;

	public ModeSetting(String name, String description, String defaultMode, String... modes) {
		super(name, description);

		if (modes.length == 0) {
			throw new IllegalArgumentException("Mode setting '" + name + "' needs at least one mode");
		}

		this.modes = List.of(modes);
		setMode(defaultMode);
	}

	public String getMode() {
		return currentMode;
	}

	/** Index of the current mode in {@link #getModes()}. Comparing indices is cheaper than strings on hot paths. */
	public int getIndex() {
		return index;
	}

	public List<String> getModes() {
		return modes;
	}

	public boolean is(String mode) {
		return currentMode.equals(mode);
	}

	public void setMode(String mode) {
		int newIndex = modes.indexOf(mode);

		if (newIndex < 0) {
			throw new IllegalArgumentException("Unknown mode '" + mode + "' for setting '" + getName() + "', expected one of " + modes);
		}

		index = newIndex;
		currentMode = modes.get(newIndex);
	}

	/** Moves {@code direction} steps through the list, wrapping around. */
	public void cycle(int direction) {
		index = Math.floorMod(index + direction, modes.size());
		currentMode = modes.get(index);
	}

	public ModeSetting visibleWhen(Supplier<Boolean> visibility) {
		setVisibility(visibility);
		return this;
	}
}

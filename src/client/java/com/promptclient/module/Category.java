package com.promptclient.module;

/**
 * Groups modules for filtering (e.g. in a future ClickGUI) and display.
 */
public enum Category {
	RENDER("Render"),
	MOVEMENT("Movement"),
	COMBAT("Combat"),
	PLAYER("Player"),
	MISC("Misc");

	private final String displayName;

	Category(String displayName) {
		this.displayName = displayName;
	}

	public String getDisplayName() {
		return displayName;
	}
}

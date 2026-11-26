package com.promptclient.module.impl.misc;

import com.promptclient.PromptClient;
import com.promptclient.module.Category;
import com.promptclient.module.Module;
import com.promptclient.setting.ModeSetting;
import com.promptclient.ui.Theme;
import com.promptclient.ui.clickgui.ClickGuiScreen;
import org.lwjgl.glfw.GLFW;

/**
 * Opens the ClickGUI. The module stays enabled while the screen is open; closing the screen disables it again.
 * Its "Accent" setting drives the accent color across the GUI, HUD and chat (see {@code Theme#accent()}).
 */
public final class ClickGui extends Module {
	private static final String[] ACCENT_NAMES = {"Blue", "Purple", "Pink", "Orange", "Green", "Teal"};
	private static final int[] ACCENT_COLORS = {Theme.DEFAULT_ACCENT, 0xFFA37CF5, 0xFFF0649C, 0xFFF5A443, 0xFF41C96E, 0xFF3FC5D6};

	private final ModeSetting accent = addSetting(new ModeSetting("Accent",
			"Highlight color used by the GUI, HUD and chat.", "Blue", ACCENT_NAMES));

	// Created on first open and reused, so panel positions and scroll state survive closing.
	private ClickGuiScreen screen;

	public ClickGui() {
		super("ClickGUI", "Opens this menu.", Category.MISC, GLFW.GLFW_KEY_RIGHT_SHIFT);
	}

	@Override
	protected void onEnable() {
		if (screen == null) {
			screen = new ClickGuiScreen(this, PromptClient.getModuleManager());
		}

		screen.prepareOpen();
		client().setScreen(screen);
	}

	@Override
	protected void onDisable() {
		// Disabled from somewhere other than the screen itself (e.g. clicking its own row): animate out.
		if (screen != null && client().currentScreen == screen) {
			screen.close();
		}
	}

	@Override
	public boolean isVisibleInArrayList() {
		return false;
	}

	public int getAccentColor() {
		return ACCENT_COLORS[accent.getIndex()];
	}
}

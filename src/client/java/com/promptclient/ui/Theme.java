package com.promptclient.ui;

import com.promptclient.PromptClient;
import com.promptclient.module.impl.misc.ClickGui;

/** Shared palette: macOS dark mode with an accent chosen in the ClickGUI module. All colors are 0xAARRGGBB. */
public final class Theme {
	// Screen dimming behind the ClickGUI (no blur shader: a plain alpha gradient costs nothing).
	public static final int BACKGROUND_TOP = 0x40000000;
	public static final int BACKGROUND_BOTTOM = 0x80000000;

	// Panels: #141519 at ~88% opacity with a slightly lifted top for the frosted look.
	public static final int PANEL = 0xE0141519;
	public static final int PANEL_TOP = 0xE01C1D23;
	public static final int PANEL_BORDER = 0xFF2A2D36;
	public static final int SEPARATOR = 0x992A2D36;
	public static final int SHADOW = 0x50000000;
	public static final int SETTINGS_WELL = 0x40000000;

	// HUD glass: #141519 at ~80% opacity.
	public static final int HUD_GLASS = 0xCC141519;
	public static final int HUD_GLASS_TOP = 0xCC1E1F26;
	public static final int HUD_BORDER = 0xCC2A2D36;

	// Chat card base color. Alpha comes from the Chat module's opacity setting.
	public static final int CHAT_CARD = 0xFF101114;

	public static final int ROW_HOVER = 0x12FFFFFF;
	public static final int CONTROL_TRACK = 0xFF3A3B42;
	public static final int THUMB = 0xFFFFFFFF;
	public static final int THUMB_SHADOW = 0x40000000;

	public static final int TEXT = 0xFFF2F2F7;
	public static final int TEXT_SECONDARY = 0xFF9B9BA4;
	public static final int TEXT_TERTIARY = 0xFF64646C;

	/** Electric Apple blue, used when nothing else is configured. */
	public static final int DEFAULT_ACCENT = 0xFF4F8EF7;

	private Theme() {
	}

	/** Current accent color from the ClickGUI module's "Accent" setting. Cheap: one map lookup. */
	public static int accent() {
		return PromptClient.getModuleManager().get(ClickGui.class).getAccentColor();
	}
}

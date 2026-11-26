package com.promptclient.ui.clickgui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * Per-frame state shared by every ClickGUI element. One instance per screen, refilled each frame,
 * so passing it around costs nothing. Mouse coordinates are already in content space (scale undone).
 */
final class GuiFrame {
	DrawContext context;
	TextRenderer textRenderer;
	/** Real seconds since the last frame, capped at {@code FrameClock.MAX_DELTA}. */
	float delta;
	float mouseX;
	float mouseY;
	int accent;
	long timeMs;
}

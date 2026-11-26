package com.promptclient.ui.clickgui;

import com.promptclient.ui.Theme;
import com.promptclient.ui.anim.Anim;
import com.promptclient.ui.render.Colors;
import com.promptclient.ui.render.RenderUtils;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.StringHelper;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * Spotlight-style filter at the top of the ClickGUI. Always receives typed characters while the GUI is open.
 * The lower-cased query is rebuilt only when the text changes, never per frame.
 */
final class SearchBar {
	private static final float WIDTH = 200.0f;
	private static final float HEIGHT = 20.0f;
	private static final float TOP = 12.0f;
	private static final float TEXT_X = 24.0f;
	private static final int MAX_LENGTH = 24;
	private static final String PLACEHOLDER = "Search modules";

	private final StringBuilder input = new StringBuilder();
	private String text = "";
	private String query = "";
	private int textWidth;
	private float x;
	private float y;
	private float activeAnim;

	/** Trimmed, lower-cased filter text; empty when the bar is empty. */
	String query() {
		return query;
	}

	boolean isEmpty() {
		return input.length() == 0;
	}

	void clear() {
		input.setLength(0);
		changed();
	}

	boolean charTyped(char chr) {
		if (!StringHelper.isValidChar(chr) || input.length() >= MAX_LENGTH) {
			return false;
		}

		input.append(chr);
		changed();
		return true;
	}

	/** Backspace deletes a character; Ctrl+Backspace clears everything. */
	boolean keyPressed(int keyCode) {
		if (keyCode != GLFW.GLFW_KEY_BACKSPACE || isEmpty()) {
			return false;
		}

		if (Screen.hasControlDown()) {
			input.setLength(0);
		} else {
			input.deleteCharAt(input.length() - 1);
		}

		changed();
		return true;
	}

	boolean mouseClicked(float mouseX, float mouseY) {
		if (mouseX < x || mouseX >= x + WIDTH || mouseY < y || mouseY >= y + HEIGHT) {
			return false;
		}

		// The right end holds the clear button once there is text.
		if (!isEmpty() && mouseX >= x + WIDTH - 18.0f) {
			clear();
		}

		return true;
	}

	void render(GuiFrame frame, int screenWidth) {
		x = Math.round((screenWidth - WIDTH) / 2.0f);
		y = TOP;
		activeAnim = Anim.approach(activeAnim, isEmpty() ? 0.0f : 1.0f, 14.0f, frame.delta);

		if (textWidth < 0) {
			textWidth = frame.textRenderer.getWidth(text);
		}

		RenderUtils.begin(frame.context);
		RenderUtils.shadow(x, y, WIDTH, HEIGHT, HEIGHT / 2.0f, 8.0f, Theme.SHADOW);
		RenderUtils.roundedGradientV(x, y, WIDTH, HEIGHT, HEIGHT / 2.0f, Theme.HUD_GLASS_TOP, Theme.HUD_GLASS);
		RenderUtils.roundedOutline(x, y, WIDTH, HEIGHT, HEIGHT / 2.0f, 1.0f,
				Colors.lerp(Theme.PANEL_BORDER, Colors.withAlpha(frame.accent, 0xB0), activeAnim));

		// Magnifying glass: ring plus handle.
		float iconX = x + 12.0f;
		float iconY = y + HEIGHT / 2.0f - 0.5f;
		RenderUtils.roundedOutline(iconX - 3.5f, iconY - 3.5f, 7.0f, 7.0f, 3.5f, 1.2f, Theme.TEXT_SECONDARY);
		RenderUtils.line(iconX + 2.3f, iconY + 2.3f, iconX + 5.0f, iconY + 5.0f, 1.4f, Theme.TEXT_SECONDARY);

		// Blinking caret.
		if ((frame.timeMs / 530L) % 2L == 0L) {
			float caretX = x + TEXT_X + (isEmpty() ? 0.0f : textWidth + 1.0f);
			RenderUtils.rect(caretX, y + 5.5f, 1.0f, 9.0f, frame.accent);
		}

		// Clear button.
		if (activeAnim > 0.01f) {
			float centerX = x + WIDTH - 11.0f;
			float centerY = y + HEIGHT / 2.0f;
			float size = 2.5f;
			int color = Colors.multiplyAlpha(Theme.TEXT_SECONDARY, activeAnim);
			RenderUtils.line(centerX - size, centerY - size, centerX + size, centerY + size, 1.2f, color);
			RenderUtils.line(centerX - size, centerY + size, centerX + size, centerY - size, 1.2f, color);
		}
		RenderUtils.end();

		if (isEmpty()) {
			RenderUtils.text(frame.context, frame.textRenderer, PLACEHOLDER, x + TEXT_X, y + 6.0f, Theme.TEXT_TERTIARY, false);
		} else {
			RenderUtils.text(frame.context, frame.textRenderer, text, x + TEXT_X, y + 6.0f, Theme.TEXT, false);
		}

		RenderUtils.flush(frame.context);
	}

	private void changed() {
		text = input.toString();
		query = text.trim().toLowerCase(Locale.ROOT);
		textWidth = -1;
	}
}

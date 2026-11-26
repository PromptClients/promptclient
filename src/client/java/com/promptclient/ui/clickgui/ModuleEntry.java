package com.promptclient.ui.clickgui;

import com.promptclient.module.Module;
import com.promptclient.setting.Setting;
import com.promptclient.ui.Theme;
import com.promptclient.ui.anim.Anim;
import com.promptclient.ui.render.Colors;
import com.promptclient.ui.render.RenderUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A module row plus its accordion of settings. Left-click toggles the module, right-click expands it.
 * Every animated value is a field updated once per frame; nothing here allocates while rendering.
 */
final class ModuleEntry {
	static final float ROW_HEIGHT = 18.0f;
	private static final float ROW_INSET = 4.0f;
	private static final float ROW_RADIUS = 5.0f;
	private static final float SETTINGS_INSET = 10.0f;
	private static final float SETTINGS_PADDING_TOP = 2.0f;
	private static final float SETTINGS_PADDING_BOTTOM = 5.0f;
	private static final float SETTINGS_GAP = 2.0f;
	private static final float HALF_PI = (float) (Math.PI / 2.0);

	final Module module;
	final String nameLower;
	private final List<Component> components = new ArrayList<>();

	private boolean expanded;
	private boolean rowHovered;
	private float hoverAnim;
	private float enableAnim;
	private float expandAnim;
	private float presenceAnim = 1.0f;

	// Layout of the row in content space, assigned by CategoryPanel each frame.
	float x;
	float y;
	float width;

	ModuleEntry(ClickGuiScreen screen, Module module) {
		this.module = module;
		this.nameLower = module.getName().toLowerCase(Locale.ROOT);
		this.enableAnim = module.isEnabled() ? 1.0f : 0.0f;

		for (Setting setting : module.getSettings()) {
			components.add(Component.of(screen, setting));
		}

		components.add(new KeybindComponent(screen, module));
	}

	void update(GuiFrame frame, boolean matchesSearch) {
		float dt = frame.delta;
		presenceAnim = Anim.approach(presenceAnim, matchesSearch ? 1.0f : 0.0f, 16.0f, dt);
		enableAnim = Anim.approach(enableAnim, module.isEnabled() ? 1.0f : 0.0f, 14.0f, dt);
		expandAnim = Anim.approach(expandAnim, expanded ? 1.0f : 0.0f, 13.0f, dt);
		hoverAnim = Anim.approach(hoverAnim, rowHovered ? 1.0f : 0.0f, 18.0f, dt);

		for (int i = 0; i < components.size(); i++) {
			components.get(i).update(frame);
		}
	}

	void layout(float x, float y, float width) {
		this.x = x;
		this.y = y;
		this.width = width;

		float componentY = y + ROW_HEIGHT + SETTINGS_PADDING_TOP;
		for (int i = 0; i < components.size(); i++) {
			Component component = components.get(i);
			component.x = x + SETTINGS_INSET;
			component.y = componentY;
			component.width = width - SETTINGS_INSET * 2.0f;
			componentY += component.layoutHeight();
		}
	}

	void updateHover(float mouseX, float mouseY, boolean pointerInList) {
		rowHovered = pointerInList && isOverRow(mouseX, mouseY);
		boolean inSettings = pointerInList && isOverSettings(mouseX, mouseY);

		for (int i = 0; i < components.size(); i++) {
			Component component = components.get(i);
			component.hovered = inSettings && component.visibility() > 0.5f && component.contains(mouseX, mouseY);
		}
	}

	// ---------------------------------------------------------------- geometry

	boolean isPresent() {
		return presenceAnim > 0.005f;
	}

	float presence() {
		return Anim.easeOutCubic(presenceAnim);
	}

	boolean settingsVisible() {
		return expandAnim > 0.001f;
	}

	/** True while the accordion, the search fade or any setting's visibility is mid-animation. */
	boolean settingsAnimating() {
		if (Anim.isMoving(expandAnim) || Anim.isMoving(presenceAnim)) {
			return true;
		}

		for (int i = 0; i < components.size(); i++) {
			if (components.get(i).isAnimating()) {
				return true;
			}
		}

		return false;
	}

	float settingsHeight() {
		if (expandAnim <= 0.0f) {
			return 0.0f;
		}

		float content = SETTINGS_PADDING_TOP + SETTINGS_PADDING_BOTTOM + SETTINGS_GAP;
		for (int i = 0; i < components.size(); i++) {
			content += components.get(i).layoutHeight();
		}

		return content * Anim.easeOutCubic(expandAnim);
	}

	/** Total height in the panel list, including the expanded settings. */
	float height() {
		return (ROW_HEIGHT + settingsHeight()) * presence();
	}

	private boolean isOverRow(float mouseX, float mouseY) {
		return mouseX >= x + ROW_INSET && mouseX < x + width - ROW_INSET && mouseY >= y && mouseY < y + ROW_HEIGHT;
	}

	private boolean isOverSettings(float mouseX, float mouseY) {
		return settingsVisible() && mouseX >= x && mouseX < x + width
				&& mouseY >= y + ROW_HEIGHT && mouseY < y + ROW_HEIGHT + settingsHeight();
	}

	// ---------------------------------------------------------------- rendering

	void renderRowShapes(GuiFrame frame) {
		float rowX = x + ROW_INSET;
		float rowWidth = width - ROW_INSET * 2.0f;

		if (hoverAnim > 0.01f) {
			RenderUtils.roundedRect(rowX, y, rowWidth, ROW_HEIGHT, ROW_RADIUS, Colors.multiplyAlpha(Theme.ROW_HOVER, hoverAnim));
		}

		if (enableAnim > 0.01f) {
			RenderUtils.roundedRect(rowX, y, rowWidth, ROW_HEIGHT, ROW_RADIUS, Colors.withAlpha(frame.accent, (int) (0x2E * enableAnim)));
			float barHeight = 8.0f * Anim.easeOutCubic(enableAnim);
			RenderUtils.roundedRect(rowX + 3.0f, y + (ROW_HEIGHT - barHeight) / 2.0f, 2.0f, barHeight, 1.0f,
					Colors.multiplyAlpha(frame.accent, enableAnim));
		}

		// Chevron: ">" rotating to "v" as the accordion opens.
		float angle = Anim.easeOutCubic(expandAnim) * HALF_PI;
		float cos = (float) Math.cos(angle);
		float sin = (float) Math.sin(angle);
		float centerX = x + width - 14.0f;
		float centerY = y + ROW_HEIGHT / 2.0f;
		int color = Colors.lerp(Theme.TEXT_TERTIARY, Theme.TEXT_SECONDARY, Math.max(hoverAnim, expandAnim));
		float ax = centerX + (-1.5f * cos + 3.0f * sin);
		float ay = centerY + (-1.5f * sin - 3.0f * cos);
		float bx = centerX + 1.5f * cos;
		float by = centerY + 1.5f * sin;
		float cx = centerX + (-1.5f * cos - 3.0f * sin);
		float cy = centerY + (-1.5f * sin + 3.0f * cos);
		RenderUtils.line(ax, ay, bx, by, 1.2f, color);
		RenderUtils.line(bx, by, cx, cy, 1.2f, color);
	}

	void renderRowText(GuiFrame frame) {
		int idle = Colors.lerp(Theme.TEXT_SECONDARY, Theme.TEXT, hoverAnim * 0.6f);
		RenderUtils.text(frame.context, frame.textRenderer, module.getName(), x + ROW_INSET + 9.0f, y + 5.0f,
				Colors.lerp(idle, Theme.TEXT, enableAnim), false);
	}

	void renderSettingsShapes(GuiFrame frame) {
		float height = settingsHeight();

		if (height < 0.5f) {
			return;
		}

		RenderUtils.roundedRect(x + ROW_INSET, y + ROW_HEIGHT + 1.0f, width - ROW_INSET * 2.0f, height - SETTINGS_GAP - 1.0f,
				ROW_RADIUS, Theme.SETTINGS_WELL);

		float baseAlpha = RenderUtils.getAlpha();
		float reveal = Anim.easeOutCubic(expandAnim);
		for (int i = 0; i < components.size(); i++) {
			Component component = components.get(i);

			if (component.visibility() > 0.01f) {
				RenderUtils.setAlpha(baseAlpha * reveal * component.visibility());
				component.renderShapes(frame);
			}
		}

		RenderUtils.setAlpha(baseAlpha);
	}

	void renderSettingsText(GuiFrame frame) {
		if (settingsHeight() < 0.5f) {
			return;
		}

		float baseAlpha = RenderUtils.getAlpha();
		float reveal = Anim.easeOutCubic(expandAnim);
		for (int i = 0; i < components.size(); i++) {
			Component component = components.get(i);

			if (component.visibility() > 0.01f) {
				RenderUtils.setAlpha(baseAlpha * reveal * component.visibility());
				component.renderText(frame);
			}
		}

		RenderUtils.setAlpha(baseAlpha);
	}

	// ---------------------------------------------------------------- input

	/** @return whether this entry consumed the click */
	boolean mouseClicked(float mouseX, float mouseY, int button) {
		if (presenceAnim < 0.5f) {
			return false;
		}

		if (isOverRow(mouseX, mouseY)) {
			if (button == 0) {
				module.toggle();
			} else if (button == 1) {
				expanded = !expanded;
			}

			return true;
		}

		if (expanded && expandAnim > 0.5f && isOverSettings(mouseX, mouseY)) {
			for (int i = 0; i < components.size(); i++) {
				Component component = components.get(i);

				if (component.visibility() > 0.5f && component.contains(mouseX, mouseY)) {
					component.mouseClicked(mouseX, mouseY, button);
					return true;
				}
			}

			return true;
		}

		return false;
	}

	/** Hint for whatever part of this entry is hovered, or {@code null}. */
	String hint() {
		if (rowHovered) {
			return module.getDescription();
		}

		for (int i = 0; i < components.size(); i++) {
			Component component = components.get(i);

			if (component.hovered) {
				return component.hint();
			}
		}

		return null;
	}
}

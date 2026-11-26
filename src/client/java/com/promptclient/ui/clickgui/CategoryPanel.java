package com.promptclient.ui.clickgui;

import com.promptclient.module.Category;
import com.promptclient.module.Module;
import com.promptclient.ui.Theme;
import com.promptclient.ui.anim.Anim;
import com.promptclient.ui.render.RenderUtils;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

/**
 * A draggable card for one {@link Category}. Its module list scrolls and is clipped with a scissor.
 *
 * <p>Draw calls per panel: chrome batch, header text, list batch, list text. An entry whose accordion is
 * mid-animation gets its own clipped pass, so growing settings never spill into the row below it.
 */
final class CategoryPanel {
	static final float WIDTH = 124.0f;
	static final float HEADER_HEIGHT = 24.0f;
	private static final float LIST_PADDING = 4.0f;
	private static final float RADIUS = 8.0f;
	private static final float MAX_LIST_HEIGHT = 260.0f;
	private static final float SHADOW_SPREAD = 12.0f;
	private static final float PLACEHOLDER_HEIGHT = 18.0f;
	private static final float SCROLL_STEP = 24.0f;
	private static final float SNAP_GRID = 4.0f;
	private static final float SNAP_ALIGN = 6.0f;
	private static final String EMPTY_TEXT = "No modules yet";
	private static final String NO_MATCH_TEXT = "No matches";

	final Category category;
	private final ClickGuiScreen screen;
	private final String title;
	private final String countText;
	private final List<ModuleEntry> entries = new ArrayList<>();

	// Rendered position eases toward the target, which gives dragging its trailing glide.
	float x;
	float y;
	float targetX;
	float targetY;

	private float scroll;
	private float scrollTarget;
	private float contentHeight;
	private float listHeight;
	private float placeholderAnim;
	private int countWidth = -1;
	private int emptyWidth = -1;
	private int noMatchWidth = -1;

	CategoryPanel(ClickGuiScreen screen, Category category, List<Module> modules) {
		this.screen = screen;
		this.category = category;
		this.title = category.getDisplayName();
		this.countText = Integer.toString(modules.size());

		for (Module module : modules) {
			entries.add(new ModuleEntry(screen, module));
		}

		placeholderAnim = entries.isEmpty() ? 1.0f : 0.0f;
	}

	// ---------------------------------------------------------------- geometry

	void setPosition(float x, float y) {
		this.x = this.targetX = x;
		this.y = this.targetY = y;
	}

	float bodyHeight() {
		return HEADER_HEIGHT + LIST_PADDING * 2.0f + listHeight;
	}

	boolean contains(float mouseX, float mouseY) {
		return mouseX >= x && mouseX < x + WIDTH && mouseY >= y && mouseY < y + bodyHeight();
	}

	boolean isOverHeader(float mouseX, float mouseY) {
		return contains(mouseX, mouseY) && mouseY < y + HEADER_HEIGHT;
	}

	private float listTop() {
		return y + HEADER_HEIGHT + LIST_PADDING;
	}

	private boolean isInList(float mouseX, float mouseY) {
		float top = listTop();
		return mouseX >= x && mouseX < x + WIDTH && mouseY >= top && mouseY < top + listHeight;
	}

	void dragTo(float newX, float newY, int screenWidth, int screenHeight) {
		targetX = clamp(newX, 0.0f, screenWidth - WIDTH);
		targetY = clamp(newY, 0.0f, screenHeight - HEADER_HEIGHT);
	}

	/** On release: snap to a 4 px grid, and align the top edge with a nearby panel. */
	void snap(List<CategoryPanel> panels, int screenWidth, int screenHeight) {
		float snappedX = Math.round(targetX / SNAP_GRID) * SNAP_GRID;
		float snappedY = Math.round(targetY / SNAP_GRID) * SNAP_GRID;

		for (int i = 0; i < panels.size(); i++) {
			CategoryPanel other = panels.get(i);

			if (other != this && Math.abs(other.targetY - targetY) <= SNAP_ALIGN) {
				snappedY = other.targetY;
				break;
			}
		}

		dragTo(snappedX, snappedY, screenWidth, screenHeight);
	}

	void clampInto(int screenWidth, int screenHeight) {
		dragTo(targetX, targetY, screenWidth, screenHeight);
		x = targetX;
		y = targetY;
	}

	void scrollBy(double amount) {
		scrollTarget -= (float) amount * SCROLL_STEP;
		scrollTarget = clamp(scrollTarget, 0.0f, Math.max(0.0f, contentHeight - listHeight));
	}

	// ---------------------------------------------------------------- frame

	private void update(GuiFrame frame, String query) {
		float dt = frame.delta;
		x = Anim.approach(x, targetX, 22.0f, dt);
		y = Anim.approach(y, targetY, 22.0f, dt);

		boolean anyMatch = false;
		float content = 0.0f;
		for (int i = 0; i < entries.size(); i++) {
			ModuleEntry entry = entries.get(i);
			boolean matches = query.isEmpty() || entry.nameLower.contains(query);
			anyMatch |= matches;
			entry.update(frame, matches);

			if (entry.isPresent()) {
				content += entry.height();
			}
		}

		placeholderAnim = Anim.approach(placeholderAnim, anyMatch ? 0.0f : 1.0f, 16.0f, dt);
		content += PLACEHOLDER_HEIGHT * Anim.easeOutCubic(placeholderAnim);
		contentHeight = content;

		float available = screen.height - y - HEADER_HEIGHT - LIST_PADDING * 2.0f - 8.0f;
		listHeight = Math.min(content, Math.min(MAX_LIST_HEIGHT, Math.max(PLACEHOLDER_HEIGHT, available)));

		float maxScroll = Math.max(0.0f, contentHeight - listHeight);
		scrollTarget = clamp(scrollTarget, 0.0f, maxScroll);
		scroll = clamp(Anim.approach(scroll, scrollTarget, 18.0f, dt), 0.0f, maxScroll);
	}

	void render(GuiFrame frame, String query) {
		update(frame, query);

		DrawContext context = frame.context;
		TextRenderer textRenderer = frame.textRenderer;
		float bodyHeight = bodyHeight();

		// 1. Chrome: soft shadow, frosted body, 1px inner border, header separator and accent dot.
		RenderUtils.begin(context);
		RenderUtils.shadow(x, y, WIDTH, bodyHeight, RADIUS, SHADOW_SPREAD, Theme.SHADOW);
		RenderUtils.roundedGradientV(x, y, WIDTH, bodyHeight, RADIUS, Theme.PANEL_TOP, Theme.PANEL);
		RenderUtils.roundedOutline(x, y, WIDTH, bodyHeight, RADIUS, 1.0f, Theme.PANEL_BORDER);
		RenderUtils.rect(x + 8.0f, y + HEADER_HEIGHT - 1.0f, WIDTH - 16.0f, 1.0f, Theme.SEPARATOR);
		RenderUtils.circle(x + 12.0f, y + HEADER_HEIGHT / 2.0f, 2.5f, frame.accent);
		RenderUtils.end();

		if (countWidth < 0) {
			countWidth = textRenderer.getWidth(countText);
			emptyWidth = textRenderer.getWidth(EMPTY_TEXT);
			noMatchWidth = textRenderer.getWidth(NO_MATCH_TEXT);
		}

		RenderUtils.text(context, textRenderer, title, x + 19.0f, y + 8.0f, Theme.TEXT, false);
		RenderUtils.text(context, textRenderer, countText, x + WIDTH - 10.0f - countWidth, y + 8.0f, Theme.TEXT_TERTIARY, false);

		// 2. List: lay out, then draw settled entries in one clipped batch.
		float top = listTop();
		float bottom = top + listHeight;
		boolean pointerInList = isInList(frame.mouseX, frame.mouseY) && screen.isTopmostAt(this, frame.mouseX, frame.mouseY);
		float cursor = top - scroll;
		for (int i = 0; i < entries.size(); i++) {
			ModuleEntry entry = entries.get(i);

			if (entry.isPresent()) {
				entry.layout(x, cursor, WIDTH);
				cursor += entry.height();
			}

			entry.updateHover(frame.mouseX, frame.mouseY, pointerInList && entry.isPresent());
		}

		float baseAlpha = RenderUtils.getAlpha();
		RenderUtils.enableScissor(context, x + 1.0f, top, WIDTH - 2.0f, listHeight);

		RenderUtils.begin(context);
		for (int i = 0; i < entries.size(); i++) {
			ModuleEntry entry = entries.get(i);

			if (isOnScreen(entry, top, bottom)) {
				RenderUtils.setAlpha(baseAlpha * entry.presence());
				entry.renderRowShapes(frame);

				if (entry.settingsVisible() && !entry.settingsAnimating()) {
					entry.renderSettingsShapes(frame);
				}
			}
		}

		RenderUtils.setAlpha(baseAlpha);
		if (contentHeight > listHeight + 0.5f) {
			float barHeight = Math.max(12.0f, listHeight * listHeight / contentHeight);
			float barY = top + (listHeight - barHeight) * (scroll / (contentHeight - listHeight));
			RenderUtils.roundedRect(x + WIDTH - 4.0f, barY, 2.0f, barHeight, 1.0f, 0x40FFFFFF);
		}
		RenderUtils.end();

		for (int i = 0; i < entries.size(); i++) {
			ModuleEntry entry = entries.get(i);

			if (isOnScreen(entry, top, bottom)) {
				RenderUtils.setAlpha(baseAlpha * entry.presence());
				entry.renderRowText(frame);

				if (entry.settingsVisible() && !entry.settingsAnimating()) {
					entry.renderSettingsText(frame);
				}
			}
		}

		if (placeholderAnim > 0.01f) {
			boolean searching = !query.isEmpty() && !entries.isEmpty();
			String text = searching ? NO_MATCH_TEXT : EMPTY_TEXT;
			int width = searching ? noMatchWidth : emptyWidth;
			RenderUtils.setAlpha(baseAlpha * placeholderAnim);
			RenderUtils.text(context, textRenderer, text, x + (WIDTH - width) / 2.0f, cursor + 5.0f, Theme.TEXT_TERTIARY, false);
		}

		RenderUtils.setAlpha(baseAlpha);

		// 3. Entries mid-animation: clip their settings to exactly the revealed area.
		for (int i = 0; i < entries.size(); i++) {
			ModuleEntry entry = entries.get(i);

			if (!isOnScreen(entry, top, bottom) || !entry.settingsVisible() || !entry.settingsAnimating()) {
				continue;
			}

			float clipTop = Math.max(top, entry.y + ModuleEntry.ROW_HEIGHT * entry.presence());
			float clipBottom = Math.min(bottom, entry.y + entry.height());

			if (clipBottom - clipTop < 0.5f) {
				continue;
			}

			RenderUtils.enableScissor(context, x + 1.0f, clipTop, WIDTH - 2.0f, clipBottom - clipTop);
			RenderUtils.setAlpha(baseAlpha * entry.presence());
			RenderUtils.begin(context);
			entry.renderSettingsShapes(frame);
			RenderUtils.end();
			entry.renderSettingsText(frame);
			RenderUtils.setAlpha(baseAlpha);
		}

		RenderUtils.disableScissor(context);
	}

	private static boolean isOnScreen(ModuleEntry entry, float top, float bottom) {
		return entry.isPresent() && entry.y + entry.height() >= top && entry.y <= bottom;
	}

	// ---------------------------------------------------------------- input

	boolean mouseClicked(float mouseX, float mouseY, int button) {
		if (!isInList(mouseX, mouseY)) {
			return contains(mouseX, mouseY);
		}

		for (int i = 0; i < entries.size(); i++) {
			ModuleEntry entry = entries.get(i);

			if (entry.isPresent() && entry.mouseClicked(mouseX, mouseY, button)) {
				return true;
			}
		}

		return true;
	}

	String hint() {
		for (int i = 0; i < entries.size(); i++) {
			String hint = entries.get(i).hint();

			if (hint != null) {
				return hint;
			}
		}

		return null;
	}

	private static float clamp(float value, float min, float max) {
		return value < min ? min : Math.min(value, Math.max(min, max));
	}
}

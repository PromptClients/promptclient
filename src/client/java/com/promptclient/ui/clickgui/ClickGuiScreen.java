package com.promptclient.ui.clickgui;

import com.promptclient.module.Category;
import com.promptclient.module.ModuleManager;
import com.promptclient.module.impl.misc.ClickGui;
import com.promptclient.ui.Theme;
import com.promptclient.ui.anim.Anim;
import com.promptclient.ui.anim.FrameClock;
import com.promptclient.ui.render.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The PromptClient ClickGUI: one draggable panel per {@link Category}, a live search bar, and a hint footer.
 *
 * <p>Rendering: real frame time from {@link FrameClock} drives every animation; shapes go through
 * {@link RenderUtils} batches; vanilla's background blur is skipped entirely. Gameplay keeps running
 * ({@link #shouldPause()} is false).
 *
 * <p>Input: Minecraft passes mouse coordinates in screen space, so they are mapped through the inverse of the
 * open/close scale before hit-testing. Hit-testing uses the layout from the last rendered frame.
 */
public final class ClickGuiScreen extends Screen {
	private static final float PANEL_GAP = 10.0f;
	private static final float PANELS_TOP = 44.0f;
	private static final float ROW_WRAP = 150.0f;
	private static final float OPEN_SPEED = 14.0f;
	private static final float CLOSE_SPEED = 20.0f;
	private static final float HINT_HEIGHT = 18.0f;
	private static final float OFFSCREEN = -10000.0f;

	private final ClickGui clickGui;
	private final List<CategoryPanel> panels = new ArrayList<>();
	private final SearchBar searchBar = new SearchBar();
	private final GuiFrame frame = new GuiFrame();
	private final FrameClock clock = new FrameClock();

	private float openAnim;
	private boolean closing;
	private boolean laidOut;

	private CategoryPanel draggingPanel;
	private float dragOffsetX;
	private float dragOffsetY;
	private NumberComponent draggingSlider;
	private KeybindComponent listening;
	private boolean suppressNextChar;

	private String shownHint;
	private int hintWidth;
	private float hintAnim;

	public ClickGuiScreen(ClickGui clickGui, ModuleManager moduleManager) {
		super(Text.literal("PromptClient"));
		this.clickGui = clickGui;

		for (Category category : Category.values()) {
			panels.add(new CategoryPanel(this, category, moduleManager.getModulesByCategory(category)));
		}
	}

	/** Resets transient state before the screen is shown again. Panel positions and scroll are kept. */
	public void prepareOpen() {
		closing = false;
		openAnim = 0.0f;
		clock.reset();
		draggingPanel = null;
		draggingSlider = null;
		listening = null;
		suppressNextChar = false;
	}

	@Override
	protected void init() {
		if (!laidOut) {
			layoutPanels();
			laidOut = true;
			return;
		}

		for (int i = 0; i < panels.size(); i++) {
			panels.get(i).clampInto(width, height);
		}
	}

	private void layoutPanels() {
		float total = panels.size() * CategoryPanel.WIDTH + (panels.size() - 1) * PANEL_GAP;
		float startX = Math.max(PANEL_GAP, (width - total) / 2.0f);
		float x = startX;
		float y = PANELS_TOP;

		for (CategoryPanel panel : panels) {
			if (x + CategoryPanel.WIDTH > width - PANEL_GAP && x > startX) {
				x = startX;
				y += ROW_WRAP;
			}

			panel.setPosition(Math.round(x), Math.round(y));
			x += CategoryPanel.WIDTH + PANEL_GAP;
		}
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	/** Vanilla blurs the world here with a multi-pass shader. We dim with a gradient in {@link #render} instead. */
	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		float dt = clock.tick();
		openAnim = Anim.approach(openAnim, closing ? 0.0f : 1.0f, closing ? CLOSE_SPEED : OPEN_SPEED, dt);
		float eased = Anim.easeOutCubic(openAnim);
		float scale = scale();

		RenderUtils.setAlpha(eased);
		RenderUtils.begin(context);
		RenderUtils.gradientV(0.0f, 0.0f, width, height, Theme.BACKGROUND_TOP, Theme.BACKGROUND_BOTTOM);
		RenderUtils.end();

		frame.context = context;
		frame.textRenderer = textRenderer;
		frame.delta = dt;
		frame.accent = Theme.accent();
		frame.timeMs = Util.getMeasuringTimeMs();
		frame.mouseX = closing ? OFFSCREEN : toContentX(mouseX);
		frame.mouseY = closing ? OFFSCREEN : toContentY(mouseY);

		MatrixStack matrices = context.getMatrices();
		float centerX = width / 2.0f;
		float centerY = height / 2.0f;
		matrices.push();
		matrices.translate(centerX, centerY, 0.0f);
		matrices.scale(scale, scale, 1.0f);
		matrices.translate(-centerX, -centerY, 0.0f);

		searchBar.render(frame, width);

		String query = searchBar.query();
		for (int i = 0; i < panels.size(); i++) {
			panels.get(i).render(frame, query);
		}

		renderHint();

		matrices.pop();
		RenderUtils.setAlpha(1.0f);
	}

	private void renderHint() {
		String hint = null;
		for (int i = panels.size() - 1; i >= 0; i--) {
			CategoryPanel panel = panels.get(i);

			if (panel.contains(frame.mouseX, frame.mouseY)) {
				hint = panel.hint();
				break;
			}
		}

		// Hints are constant strings, so identity tells us when to re-measure.
		if (hint != null && hint != shownHint) {
			shownHint = hint;
			hintWidth = textRenderer.getWidth(hint);
		}

		hintAnim = Anim.approach(hintAnim, hint != null ? 1.0f : 0.0f, 14.0f, frame.delta);

		if (hintAnim < 0.01f || shownHint == null) {
			return;
		}

		float baseAlpha = RenderUtils.getAlpha();
		float hintWidthPadded = hintWidth + 20.0f;
		float hintX = (width - hintWidthPadded) / 2.0f;
		float hintY = height - HINT_HEIGHT - 14.0f + (1.0f - Anim.easeOutCubic(hintAnim)) * 4.0f;

		RenderUtils.setAlpha(baseAlpha * hintAnim);
		RenderUtils.begin(frame.context);
		RenderUtils.shadow(hintX, hintY, hintWidthPadded, HINT_HEIGHT, HINT_HEIGHT / 2.0f, 8.0f, Theme.SHADOW);
		RenderUtils.roundedGradientV(hintX, hintY, hintWidthPadded, HINT_HEIGHT, HINT_HEIGHT / 2.0f, Theme.HUD_GLASS_TOP, Theme.HUD_GLASS);
		RenderUtils.roundedOutline(hintX, hintY, hintWidthPadded, HINT_HEIGHT, HINT_HEIGHT / 2.0f, 1.0f, Theme.HUD_BORDER);
		RenderUtils.end();
		RenderUtils.text(frame.context, textRenderer, shownHint, hintX + 10.0f, hintY + 5.0f, Theme.TEXT_SECONDARY, false);
		RenderUtils.flush(frame.context);
		RenderUtils.setAlpha(baseAlpha);
	}

	// ---------------------------------------------------------------- lifecycle

	/** Starts the close animation; {@link #tick()} removes the screen once it has faded out. */
	@Override
	public void close() {
		if (!closing) {
			closing = true;
			draggingPanel = null;
			draggingSlider = null;
			listening = null;
		}
	}

	@Override
	public void tick() {
		if (closing && openAnim <= 0.02f) {
			super.close();
		}
	}

	@Override
	public void removed() {
		draggingPanel = null;
		draggingSlider = null;
		listening = null;
		clickGui.setEnabled(false);
	}

	// ---------------------------------------------------------------- input

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (closing) {
			return true;
		}

		float x = toContentX(mouseX);
		float y = toContentY(mouseY);

		// Any click ends key listening. Clicking the listening pill again just cancels.
		if (listening != null) {
			KeybindComponent previous = listening;
			listening = null;

			if (previous.contains(x, y)) {
				return true;
			}
		}

		if (searchBar.mouseClicked(x, y)) {
			return true;
		}

		for (int i = panels.size() - 1; i >= 0; i--) {
			CategoryPanel panel = panels.get(i);

			if (!panel.contains(x, y)) {
				continue;
			}

			bringToFront(i);

			if (button == 0 && panel.isOverHeader(x, y)) {
				draggingPanel = panel;
				dragOffsetX = x - panel.targetX;
				dragOffsetY = y - panel.targetY;
				return true;
			}

			return panel.mouseClicked(x, y, button);
		}

		return false;
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (draggingPanel != null) {
			draggingPanel.dragTo(toContentX(mouseX) - dragOffsetX, toContentY(mouseY) - dragOffsetY, width, height);
			return true;
		}

		if (draggingSlider != null) {
			draggingSlider.drag(toContentX(mouseX));
			return true;
		}

		return false;
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (draggingPanel != null) {
			draggingPanel.snap(panels, width, height);
			draggingPanel = null;
		}

		draggingSlider = null;
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (closing) {
			return true;
		}

		float x = toContentX(mouseX);
		float y = toContentY(mouseY);

		for (int i = panels.size() - 1; i >= 0; i--) {
			CategoryPanel panel = panels.get(i);

			if (panel.contains(x, y)) {
				panel.scrollBy(verticalAmount);
				return true;
			}
		}

		return false;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		// A key that produced no character (F-keys, arrows) must not leave the flag set for the next one.
		suppressNextChar = false;

		if (closing) {
			return true;
		}

		if (listening != null) {
			boolean unbind = keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_DELETE;
			listening.bind(unbind ? GLFW.GLFW_KEY_UNKNOWN : keyCode);
			listening = null;
			// The same key press also arrives as charTyped; it must not land in the search bar.
			suppressNextChar = true;
			return true;
		}

		if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
			if (searchBar.isEmpty()) {
				close();
			} else {
				searchBar.clear();
			}

			return true;
		}

		if (keyCode == clickGui.getKeyCode() && keyCode != GLFW.GLFW_KEY_UNKNOWN) {
			close();
			return true;
		}

		return searchBar.keyPressed(keyCode);
	}

	@Override
	public boolean charTyped(char chr, int modifiers) {
		if (closing) {
			return true;
		}

		if (suppressNextChar) {
			suppressNextChar = false;
			return true;
		}

		return searchBar.charTyped(chr);
	}

	// ---------------------------------------------------------------- shared with panels and components

	boolean isTopmostAt(CategoryPanel panel, float mouseX, float mouseY) {
		for (int i = panels.size() - 1; i >= 0; i--) {
			CategoryPanel other = panels.get(i);

			if (other == panel) {
				return true;
			}

			if (other.contains(mouseX, mouseY)) {
				return false;
			}
		}

		return false;
	}

	void startDragging(NumberComponent slider) {
		draggingSlider = slider;
	}

	boolean isDragging(NumberComponent slider) {
		return draggingSlider == slider;
	}

	void startListening(KeybindComponent component) {
		listening = component;
	}

	boolean isListening(KeybindComponent component) {
		return listening == component;
	}

	// ---------------------------------------------------------------- helpers

	private void bringToFront(int index) {
		if (index != panels.size() - 1) {
			panels.add(panels.remove(index));
		}
	}

	private float scale() {
		return 0.95f + 0.05f * Anim.easeOutCubic(openAnim);
	}

	private float toContentX(double mouseX) {
		float center = width / 2.0f;
		return center + ((float) mouseX - center) / scale();
	}

	private float toContentY(double mouseY) {
		float center = height / 2.0f;
		return center + ((float) mouseY - center) / scale();
	}
}

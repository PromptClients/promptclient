package com.promptclient.ui.clickgui;

import com.promptclient.setting.BooleanSetting;
import com.promptclient.setting.ModeSetting;
import com.promptclient.setting.NumberSetting;
import com.promptclient.setting.Setting;
import com.promptclient.ui.anim.Anim;

/**
 * One row inside a module's expanded settings. Layout ({@link #x}, {@link #y}, {@link #width}) is assigned by
 * {@link ModuleEntry} each frame. Shapes and text are drawn in separate passes so each pass batches.
 */
abstract class Component {
	/** Text baseline offset that vertically centers the 9 px font in a 16 px row. */
	static final float TEXT_OFFSET = 4.0f;

	protected final ClickGuiScreen screen;
	protected final String label;

	float x;
	float y;
	float width;
	boolean hovered;

	protected float hoverAnim;
	private float visibleAnim;
	private boolean initialized;

	Component(ClickGuiScreen screen, String label) {
		this.screen = screen;
		this.label = label;
	}

	static Component of(ClickGuiScreen screen, Setting setting) {
		return switch (setting) {
			case BooleanSetting booleanSetting -> new BooleanComponent(screen, booleanSetting);
			case NumberSetting numberSetting -> new NumberComponent(screen, numberSetting);
			case ModeSetting modeSetting -> new ModeComponent(screen, modeSetting);
			default -> throw new IllegalArgumentException("No ClickGUI component for " + setting.getClass().getName());
		};
	}

	/** Full height when visible. */
	abstract float baseHeight();

	abstract void renderShapes(GuiFrame frame);

	abstract void renderText(GuiFrame frame);

	/** Whether the underlying setting should be shown. Animated, so rows grow and shrink smoothly. */
	boolean isVisible() {
		return true;
	}

	/** Footer hint shown while hovered, or {@code null}. */
	String hint() {
		return null;
	}

	boolean mouseClicked(float mouseX, float mouseY, int button) {
		return false;
	}

	void update(GuiFrame frame) {
		boolean visible = isVisible();

		if (!initialized) {
			visibleAnim = visible ? 1.0f : 0.0f;
			initialized = true;
		}

		visibleAnim = Anim.approach(visibleAnim, visible ? 1.0f : 0.0f, 16.0f, frame.delta);
		hoverAnim = Anim.approach(hoverAnim, hovered ? 1.0f : 0.0f, 18.0f, frame.delta);
	}

	/** Height this row takes in the layout right now. */
	final float layoutHeight() {
		return baseHeight() * Anim.easeOutCubic(visibleAnim);
	}

	final float visibility() {
		return visibleAnim;
	}

	final boolean isAnimating() {
		return Anim.isMoving(visibleAnim);
	}

	final boolean contains(float mouseX, float mouseY) {
		return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + baseHeight();
	}
}

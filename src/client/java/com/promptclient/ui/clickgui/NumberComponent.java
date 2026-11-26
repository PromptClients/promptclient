package com.promptclient.ui.clickgui;

import com.promptclient.setting.NumberSetting;
import com.promptclient.ui.Theme;
import com.promptclient.ui.anim.Anim;
import com.promptclient.ui.render.Colors;
import com.promptclient.ui.render.RenderUtils;

/** Label and value on top, a slider underneath. The fill animates toward the value; drag anywhere on the row. */
final class NumberComponent extends Component {
	private static final float HEIGHT = 24.0f;
	private static final float TRACK_OFFSET = 17.0f;
	private static final float TRACK_HEIGHT = 3.0f;
	private static final float THUMB_RADIUS = 3.5f;

	private final NumberSetting setting;
	private float fillAnim;
	private String shownValue;
	private int valueWidth;

	NumberComponent(ClickGuiScreen screen, NumberSetting setting) {
		super(screen, setting.getName());
		this.setting = setting;
		this.fillAnim = (float) setting.getPercent();
	}

	@Override
	float baseHeight() {
		return HEIGHT;
	}

	@Override
	boolean isVisible() {
		return setting.isVisible();
	}

	@Override
	String hint() {
		return setting.getDescription();
	}

	@Override
	void update(GuiFrame frame) {
		super.update(frame);
		fillAnim = Anim.approach(fillAnim, (float) setting.getPercent(), 22.0f, frame.delta);

		// NumberSetting returns the same String until the value changes, so this measures only on change.
		if (shownValue != setting.getDisplayValue()) {
			shownValue = setting.getDisplayValue();
			valueWidth = frame.textRenderer.getWidth(shownValue);
		}
	}

	@Override
	void renderShapes(GuiFrame frame) {
		float trackY = y + TRACK_OFFSET;
		float fillWidth = width * fillAnim;
		RenderUtils.roundedRect(x, trackY, width, TRACK_HEIGHT, TRACK_HEIGHT / 2.0f, Theme.CONTROL_TRACK);

		if (fillWidth > 0.5f) {
			RenderUtils.roundedRect(x, trackY, fillWidth, TRACK_HEIGHT, TRACK_HEIGHT / 2.0f, frame.accent);
		}

		float active = screen.isDragging(this) ? 1.0f : hoverAnim;
		float radius = THUMB_RADIUS + active * 0.8f;
		float thumbX = x + fillWidth;
		float thumbY = trackY + TRACK_HEIGHT / 2.0f;
		RenderUtils.circle(thumbX, thumbY + 0.5f, radius + 0.5f, Theme.THUMB_SHADOW);
		RenderUtils.circle(thumbX, thumbY, radius, Theme.THUMB);
	}

	@Override
	void renderText(GuiFrame frame) {
		RenderUtils.text(frame.context, frame.textRenderer, label, x, y + TEXT_OFFSET,
				Colors.lerp(Theme.TEXT_SECONDARY, Theme.TEXT, hoverAnim), false);
		RenderUtils.text(frame.context, frame.textRenderer, shownValue, x + width - valueWidth, y + TEXT_OFFSET,
				Colors.lerp(Theme.TEXT_TERTIARY, Theme.TEXT_SECONDARY, hoverAnim), false);
	}

	@Override
	boolean mouseClicked(float mouseX, float mouseY, int button) {
		if (button != 0) {
			return false;
		}

		screen.startDragging(this);
		drag(mouseX);
		return true;
	}

	void drag(float mouseX) {
		setting.setPercent((mouseX - x) / width);
	}
}

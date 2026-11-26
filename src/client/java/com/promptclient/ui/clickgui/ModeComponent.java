package com.promptclient.ui.clickgui;

import com.promptclient.setting.ModeSetting;
import com.promptclient.ui.Theme;
import com.promptclient.ui.anim.Anim;
import com.promptclient.ui.render.Colors;
import com.promptclient.ui.render.RenderUtils;

/** Current mode in an accent pill. Left-click cycles forward, right-click backward; the old label cross-fades out. */
final class ModeComponent extends Component {
	private static final float HEIGHT = 16.0f;
	private static final float PILL_HEIGHT = 12.0f;
	private static final float PILL_PADDING = 6.0f;
	private static final float SLIDE = 4.0f;

	private final ModeSetting setting;
	private String shownMode;
	private int shownWidth;
	private String previousMode;
	private int previousWidth;
	private float changeAnim = 1.0f;

	ModeComponent(ClickGuiScreen screen, ModeSetting setting) {
		super(screen, setting.getName());
		this.setting = setting;
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

		// Mode strings come from an immutable list, so identity tells us when the mode changed.
		String mode = setting.getMode();
		if (shownMode != mode) {
			if (shownMode != null) {
				previousMode = shownMode;
				previousWidth = shownWidth;
				changeAnim = 0.0f;
			}

			shownMode = mode;
			shownWidth = frame.textRenderer.getWidth(mode);
		}

		changeAnim = Anim.approach(changeAnim, 1.0f, 14.0f, frame.delta);
	}

	@Override
	void renderShapes(GuiFrame frame) {
		float pillWidth = pillWidth();
		RenderUtils.roundedRect(x + width - pillWidth, y + (HEIGHT - PILL_HEIGHT) / 2.0f, pillWidth, PILL_HEIGHT,
				PILL_HEIGHT / 2.0f, Colors.withAlpha(frame.accent, (int) (0x30 + 0x18 * hoverAnim)));
	}

	@Override
	void renderText(GuiFrame frame) {
		RenderUtils.text(frame.context, frame.textRenderer, label, x, y + TEXT_OFFSET,
				Colors.lerp(Theme.TEXT_SECONDARY, Theme.TEXT, hoverAnim), false);

		float eased = Anim.easeOutCubic(changeAnim);
		float right = x + width - PILL_PADDING;
		int color = Colors.lerp(frame.accent, Theme.TEXT, 0.35f);
		float baseAlpha = RenderUtils.getAlpha();

		if (previousMode != null && changeAnim < 1.0f) {
			RenderUtils.setAlpha(baseAlpha * (1.0f - eased));
			RenderUtils.text(frame.context, frame.textRenderer, previousMode, right - previousWidth - eased * SLIDE, y + TEXT_OFFSET, color, false);
		}

		RenderUtils.setAlpha(baseAlpha * eased);
		RenderUtils.text(frame.context, frame.textRenderer, shownMode, right - shownWidth + (1.0f - eased) * SLIDE, y + TEXT_OFFSET, color, false);
		RenderUtils.setAlpha(baseAlpha);
	}

	@Override
	boolean mouseClicked(float mouseX, float mouseY, int button) {
		if (button == 0) {
			setting.cycle(1);
			return true;
		}

		if (button == 1) {
			setting.cycle(-1);
			return true;
		}

		return false;
	}

	private float pillWidth() {
		float textWidth = previousMode == null
				? shownWidth
				: Anim.lerp(previousWidth, shownWidth, Anim.easeOutCubic(changeAnim));
		return textWidth + PILL_PADDING * 2.0f;
	}
}

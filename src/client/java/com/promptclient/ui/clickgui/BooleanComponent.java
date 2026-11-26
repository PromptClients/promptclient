package com.promptclient.ui.clickgui;

import com.promptclient.setting.BooleanSetting;
import com.promptclient.ui.Theme;
import com.promptclient.ui.anim.Anim;
import com.promptclient.ui.render.Colors;
import com.promptclient.ui.render.RenderUtils;

/** Apple-style switch: pill track that fills with the accent, with a sliding white thumb. */
final class BooleanComponent extends Component {
	private static final float HEIGHT = 16.0f;
	private static final float TRACK_WIDTH = 18.0f;
	private static final float TRACK_HEIGHT = 10.0f;
	private static final float THUMB_INSET = 1.5f;

	private final BooleanSetting setting;
	private float toggleAnim;

	BooleanComponent(ClickGuiScreen screen, BooleanSetting setting) {
		super(screen, setting.getName());
		this.setting = setting;
		this.toggleAnim = setting.getValue() ? 1.0f : 0.0f;
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
		toggleAnim = Anim.approach(toggleAnim, setting.getValue() ? 1.0f : 0.0f, 18.0f, frame.delta);
	}

	@Override
	void renderShapes(GuiFrame frame) {
		float trackX = x + width - TRACK_WIDTH;
		float trackY = y + (HEIGHT - TRACK_HEIGHT) / 2.0f;
		RenderUtils.roundedRect(trackX, trackY, TRACK_WIDTH, TRACK_HEIGHT, TRACK_HEIGHT / 2.0f,
				Colors.lerp(Theme.CONTROL_TRACK, frame.accent, toggleAnim));

		float radius = TRACK_HEIGHT / 2.0f - THUMB_INSET;
		float travel = TRACK_WIDTH - 2.0f * (radius + THUMB_INSET);
		float thumbX = trackX + THUMB_INSET + radius + travel * toggleAnim;
		float thumbY = trackY + TRACK_HEIGHT / 2.0f;
		RenderUtils.circle(thumbX, thumbY + 0.5f, radius + 0.5f, Theme.THUMB_SHADOW);
		RenderUtils.circle(thumbX, thumbY, radius + hoverAnim * 0.3f, Theme.THUMB);
	}

	@Override
	void renderText(GuiFrame frame) {
		RenderUtils.text(frame.context, frame.textRenderer, label, x, y + TEXT_OFFSET,
				Colors.lerp(Theme.TEXT_SECONDARY, Theme.TEXT, hoverAnim), false);
	}

	@Override
	boolean mouseClicked(float mouseX, float mouseY, int button) {
		if (button != 0) {
			return false;
		}

		setting.toggle();
		return true;
	}
}

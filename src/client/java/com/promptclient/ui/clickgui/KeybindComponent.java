package com.promptclient.ui.clickgui;

import com.promptclient.module.Module;
import com.promptclient.ui.Theme;
import com.promptclient.ui.anim.Anim;
import com.promptclient.ui.render.Colors;
import com.promptclient.ui.render.RenderUtils;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * The last row of every module's settings. Click to listen for the next key; Esc or Delete unbinds.
 * Right-click also unbinds. The key name is resolved and measured only when the binding changes.
 */
final class KeybindComponent extends Component {
	private static final float HEIGHT = 16.0f;
	private static final float PILL_HEIGHT = 12.0f;
	private static final float PILL_PADDING = 6.0f;
	private static final String LISTENING_TEXT = "Press a key";
	private static final String HINT = "Click, then press a key. Esc or Del unbinds.";

	private final Module module;
	private int shownKey = Integer.MIN_VALUE;
	private String keyName;
	private int keyWidth;
	private int listeningWidth = -1;
	private float pillWidth = -1.0f;
	private float listenAnim;

	KeybindComponent(ClickGuiScreen screen, Module module) {
		super(screen, "Bind");
		this.module = module;
	}

	@Override
	float baseHeight() {
		return HEIGHT;
	}

	@Override
	String hint() {
		return HINT;
	}

	@Override
	void update(GuiFrame frame) {
		super.update(frame);

		if (module.getKeyCode() != shownKey) {
			shownKey = module.getKeyCode();
			keyName = describe(shownKey);
			keyWidth = frame.textRenderer.getWidth(keyName);
		}

		if (listeningWidth < 0) {
			listeningWidth = frame.textRenderer.getWidth(LISTENING_TEXT);
		}

		boolean listening = screen.isListening(this);
		listenAnim = Anim.approach(listenAnim, listening ? 1.0f : 0.0f, 16.0f, frame.delta);

		float target = (listening ? listeningWidth : keyWidth) + PILL_PADDING * 2.0f;
		pillWidth = pillWidth < 0.0f ? target : Anim.approach(pillWidth, target, 18.0f, frame.delta);
	}

	@Override
	void renderShapes(GuiFrame frame) {
		float pillX = x + width - pillWidth;
		float pillY = y + (HEIGHT - PILL_HEIGHT) / 2.0f;
		int idle = Colors.lerp(0x1AFFFFFF, 0x26FFFFFF, hoverAnim);
		RenderUtils.roundedRect(pillX, pillY, pillWidth, PILL_HEIGHT, PILL_HEIGHT / 2.0f,
				Colors.lerp(idle, Colors.withAlpha(frame.accent, 0x30), listenAnim));

		if (listenAnim > 0.01f) {
			float pulse = 0.55f + 0.45f * (float) Math.sin(frame.timeMs / 160.0);
			RenderUtils.roundedOutline(pillX, pillY, pillWidth, PILL_HEIGHT, PILL_HEIGHT / 2.0f, 1.0f,
					Colors.multiplyAlpha(frame.accent, listenAnim * pulse));
		}
	}

	@Override
	void renderText(GuiFrame frame) {
		RenderUtils.text(frame.context, frame.textRenderer, label, x, y + TEXT_OFFSET,
				Colors.lerp(Theme.TEXT_TERTIARY, Theme.TEXT_SECONDARY, hoverAnim), false);

		boolean listening = screen.isListening(this);
		String text = listening ? LISTENING_TEXT : keyName;
		int textWidth = listening ? listeningWidth : keyWidth;
		float pillX = x + width - pillWidth;
		int color = listening
				? Colors.lerp(frame.accent, Theme.TEXT, 0.35f)
				: Colors.lerp(Theme.TEXT_SECONDARY, Theme.TEXT, hoverAnim);
		RenderUtils.text(frame.context, frame.textRenderer, text, pillX + (pillWidth - textWidth) / 2.0f, y + TEXT_OFFSET, color, false);
	}

	@Override
	boolean mouseClicked(float mouseX, float mouseY, int button) {
		if (button == 0) {
			screen.startListening(this);
			return true;
		}

		if (button == 1) {
			bind(GLFW.GLFW_KEY_UNKNOWN);
			return true;
		}

		return false;
	}

	void bind(int keyCode) {
		module.setKeyCode(keyCode);
	}

	private static String describe(int keyCode) {
		if (keyCode == GLFW.GLFW_KEY_UNKNOWN) {
			return "None";
		}

		return InputUtil.Type.KEYSYM.createFromCode(keyCode).getLocalizedText().getString();
	}
}

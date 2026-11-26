package com.promptclient.ui;

import com.promptclient.PromptClient;
import com.promptclient.module.impl.render.Chat;
import com.promptclient.ui.anim.Anim;
import com.promptclient.ui.anim.FrameClock;
import com.promptclient.ui.render.Colors;
import com.promptclient.ui.render.RenderUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHudLine;

import java.util.List;

/**
 * Chat styling for the {@link Chat} module, driven by {@code ChatHudMixin}.
 *
 * <p>Vanilla draws one flat rectangle per chat line. Instead we skip those fills and draw a single
 * rounded card in one batch <i>before</i> vanilla's line loop, so vanilla's text, message indicators,
 * hover and click handling are untouched and stay on top.
 *
 * <p>The row logic below mirrors {@code ChatHud#render} in 1.21.1 exactly (which lines are drawn and with
 * what opacity). Re-check it against the decompiled source before porting to another version.
 */
public final class ChatRenderer {
	private static final float RADIUS = 3.0f;
	private static final int FADE_TICKS = 200;
	private static final FrameClock CLOCK = new FrameClock();

	private static float slideOffset;
	private static float fadeIn = 1.0f;
	private static int newestAddedTime = Integer.MIN_VALUE;

	private ChatRenderer() {
	}

	/** Whether chat styling is on. Every hook checks this first and falls back to vanilla behavior. */
	public static boolean isActive() {
		return PromptClient.getModuleManager().isEnabled(Chat.class);
	}

	/** A new message arrived: slide the stack up from one line lower and fade the message in. */
	public static void onMessageAdded(int addedTime, int lineHeight, boolean scrolled) {
		if (!isActive() || scrolled || !chat().animateMessages()) {
			return;
		}

		newestAddedTime = addedTime;
		fadeIn = 0.0f;
		// Capped at two lines so a burst of messages never slides under the open chat input box.
		slideOffset = Math.min(slideOffset + lineHeight, lineHeight * 2.0f);
	}

	/**
	 * Draws the card behind the visible chat lines. Runs inside vanilla's chat matrix (scaled and shifted
	 * 4 px right), right before vanilla's line loop.
	 *
	 * @param chatWidth  chat width in scaled units (vanilla's {@code k})
	 * @param chatBottom bottom of the newest line in scaled units (vanilla's {@code m})
	 */
	public static void renderCard(DrawContext context, List<ChatHudLine.Visible> lines, int scrolledLines,
			int visibleLineCount, int lineHeight, int chatWidth, int chatBottom, int currentTick, boolean focused) {
		Chat chat = chat();
		float dt = CLOCK.tick();

		if (chat.animateMessages()) {
			slideOffset = Anim.approach(slideOffset, 0.0f, 14.0f, dt);
			fadeIn = Anim.approach(fadeIn, 1.0f, 9.0f, dt);
		} else {
			slideOffset = 0.0f;
			fadeIn = 1.0f;
		}

		// Shifts the card and vanilla's lines together; vanilla pops this matrix entry after the chat.
		if (slideOffset > 0.0f) {
			context.getMatrices().translate(0.0f, slideOffset, 0.0f);
		}

		double textOpacity = MinecraftClient.getInstance().options.getChatOpacity().getValue() * 0.9 + 0.1;

		// Pass 1: find the oldest line that vanilla will draw. Drawn lines are always a contiguous run from
		// the newest one, because opacity only falls with age.
		int lastDrawn = -1;
		for (int row = 0; row + scrolledLines < lines.size() && row < visibleLineCount; row++) {
			if (textAlpha(lines.get(row + scrolledLines), currentTick, focused, textOpacity) > 3) {
				lastDrawn = row;
			} else {
				break;
			}
		}

		if (lastDrawn < 0) {
			return;
		}

		// Pass 2: one rounded segment per line (corners only at the ends of the card), plus the accent bar.
		float cardOpacity = chat.getBackgroundOpacity();
		boolean accentBar = chat.showAccentBar();
		int accent = Theme.accent();
		float left = -4.0f;
		float width = chatWidth + 12.0f;

		RenderUtils.begin(context);
		for (int row = 0; row <= lastDrawn; row++) {
			ChatHudLine.Visible line = lines.get(row + scrolledLines);
			float age = lineOpacity(line, currentTick, focused) * (line.addedTime() == newestAddedTime ? fadeIn : 1.0f);
			float bottom = chatBottom - row * lineHeight;
			float top = bottom - lineHeight;
			boolean isBottom = row == 0;
			boolean isTop = row == lastDrawn;
			float topRadius = isTop ? RADIUS : 0.0f;
			float bottomRadius = isBottom ? RADIUS : 0.0f;

			RenderUtils.roundedRect(left, top, width, lineHeight, topRadius, topRadius, bottomRadius, bottomRadius,
					Colors.withAlpha(Theme.CHAT_CARD, (int) (255.0f * cardOpacity * age)));

			if (accentBar) {
				float barTop = top + (isTop ? 2.0f : 0.0f);
				float barBottom = bottom - (isBottom ? 2.0f : 0.0f);
				RenderUtils.rect(left + 1.0f, barTop, 1.0f, barBottom - barTop, Colors.multiplyAlpha(accent, 0.85f * age));
			}
		}
		RenderUtils.end();
	}

	/** Applies the fade-in to vanilla's text color for lines of the newest message. */
	public static int fadeTextColor(int color, int addedTime) {
		if (fadeIn >= 1.0f || addedTime != newestAddedTime || !isActive()) {
			return color;
		}

		// TextRenderer treats alpha < 4 as opaque, so never go below 4.
		int alpha = Math.max(4, (int) ((color >>> 24) * fadeIn));
		return (alpha << 24) | (color & 0x00FFFFFF);
	}

	/** Vanilla's per-line opacity multiplier: lines fade out over their last 20 ticks unless chat is open. */
	private static float lineOpacity(ChatHudLine.Visible line, int currentTick, boolean focused) {
		if (focused) {
			return 1.0f;
		}

		int age = currentTick - line.addedTime();
		double remaining = Math.max(0.0, Math.min(1.0, (1.0 - age / (double) FADE_TICKS) * 10.0));
		return (float) (remaining * remaining);
	}

	private static int textAlpha(ChatHudLine.Visible line, int currentTick, boolean focused, double textOpacity) {
		if (line == null) {
			return 0;
		}

		int age = currentTick - line.addedTime();

		if (age >= FADE_TICKS && !focused) {
			return 0;
		}

		return (int) (255.0 * lineOpacity(line, currentTick, focused) * textOpacity);
	}

	private static Chat chat() {
		return PromptClient.getModuleManager().get(Chat.class);
	}
}

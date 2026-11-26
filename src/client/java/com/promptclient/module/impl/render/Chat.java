package com.promptclient.module.impl.render;

import com.promptclient.module.Category;
import com.promptclient.module.Module;
import com.promptclient.setting.BooleanSetting;
import com.promptclient.setting.NumberSetting;

/**
 * Replaces vanilla's per-line chat background with one rounded translucent card. Enabled by default.
 * Drawing lives in {@link com.promptclient.ui.ChatRenderer}; the hooks are in {@code ChatHudMixin}.
 * Disabling it restores vanilla chat exactly.
 */
public final class Chat extends Module {
	private final NumberSetting opacity = addSetting(new NumberSetting("Opacity",
			"Background opacity of the chat card.", 65, 0, 100, 5).suffix("%"));
	private final BooleanSetting accentBar = addSetting(new BooleanSetting("Accent Bar",
			"Thin accent line along the card's left edge.", true));
	private final BooleanSetting animations = addSetting(new BooleanSetting("Animations",
			"Slide and fade in new messages.", true));

	public Chat() {
		super("Chat", "Rounded, translucent chat background.", Category.RENDER);
	}

	@Override
	public boolean isVisibleInArrayList() {
		return false;
	}

	/** Card opacity from 0 to 1. */
	public float getBackgroundOpacity() {
		return (float) (opacity.getValue() / 100.0);
	}

	public boolean showAccentBar() {
		return accentBar.getValue();
	}

	public boolean animateMessages() {
		return animations.getValue();
	}
}

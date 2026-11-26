package com.promptclient.module.impl.render;

import com.promptclient.module.Category;
import com.promptclient.module.Module;
import com.promptclient.setting.BooleanSetting;
import com.promptclient.setting.ModeSetting;

/** Settings for the overlay drawn by {@link com.promptclient.ui.HudRenderer}. Enabled by default. */
public final class Hud extends Module {
	public static final int LIST_TOP_RIGHT = 0;
	public static final int LIST_BELOW_WATERMARK = 1;

	private final BooleanSetting watermark = addSetting(new BooleanSetting("Watermark",
			"Show the PromptClient badge in the top-left corner.", true));
	private final BooleanSetting fps = addSetting(new BooleanSetting("FPS",
			"Show the frame rate inside the badge.", true).visibleWhen(watermark::getValue));
	private final BooleanSetting moduleList = addSetting(new BooleanSetting("Module List",
			"Show enabled modules on screen.", true));
	// Short mode names: a settings row is ~104 px wide, shared by the label and the mode pill.
	private final ModeSetting listPosition = addSetting(new ModeSetting("List Position",
			"Right: top-right corner. Left: under the watermark.", "Right", "Right", "Left")
			.visibleWhen(moduleList::getValue));

	public Hud() {
		super("HUD", "Watermark badge and module list overlay.", Category.RENDER);
	}

	@Override
	public boolean isVisibleInArrayList() {
		return false;
	}

	public boolean showWatermark() {
		return watermark.getValue();
	}

	public boolean showFps() {
		return fps.getValue();
	}

	public boolean showModuleList() {
		return moduleList.getValue();
	}

	/** {@link #LIST_TOP_RIGHT} or {@link #LIST_BELOW_WATERMARK}. */
	public int getListPosition() {
		return listPosition.getIndex();
	}
}

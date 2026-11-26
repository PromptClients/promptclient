package com.promptclient.module.impl.render;

import com.promptclient.module.Category;
import com.promptclient.module.Module;
import com.promptclient.setting.ModeSetting;
import com.promptclient.setting.NumberSetting;
import org.lwjgl.glfw.GLFW;

/**
 * Renders the world brighter, in one of three ways. All of them are hooks in
 * {@link com.promptclient.mixin.LightmapTextureManagerMixin}, which rebuilds the lightmap every tick, so
 * switching modes or disabling the module takes effect on the next frame with nothing to restore:
 * <ul>
 *   <li><b>Lightmap</b>: every lightmap texel is written as white. Flat, maximum brightness.</li>
 *   <li><b>Gamma</b>: the lightmap is computed with a boosted gamma instead of the option value. Keeps some
 *       shading. The gamma option itself is never written, so nothing reaches {@code options.txt} and
 *       {@code SimpleOption} bounds validation never runs.</li>
 *   <li><b>NightVision</b>: the lightmap takes vanilla's night-vision path without the status effect.</li>
 * </ul>
 */
public final class Fullbright extends Module {
	private static final int MODE_LIGHTMAP = 0;
	private static final int MODE_GAMMA = 1;
	private static final int MODE_NIGHT_VISION = 2;

	private final ModeSetting mode = addSetting(new ModeSetting("Mode", "How brightness is applied.",
			"Lightmap", "Lightmap", "Gamma", "NightVision"));
	private final NumberSetting gamma = addSetting(new NumberSetting("Gamma", "Brightness level for Gamma mode.",
			8.0, 1.0, 15.0, 0.5).visibleWhen(() -> mode.getIndex() == MODE_GAMMA));

	public Fullbright() {
		super("Fullbright", "Lights up caves and dark areas.", Category.RENDER, GLFW.GLFW_KEY_G);
	}

	public boolean overridesLightmap() {
		return isEnabled() && mode.getIndex() == MODE_LIGHTMAP;
	}

	public boolean overridesGamma() {
		return isEnabled() && mode.getIndex() == MODE_GAMMA;
	}

	public float getGamma() {
		return (float) gamma.getValue();
	}

	public boolean forcesNightVision() {
		return isEnabled() && mode.getIndex() == MODE_NIGHT_VISION;
	}
}

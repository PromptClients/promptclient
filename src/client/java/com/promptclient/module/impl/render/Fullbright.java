package com.promptclient.module.impl.render;

import com.promptclient.module.Category;
import com.promptclient.module.Module;
import org.lwjgl.glfw.GLFW;

/**
 * Renders the world at full brightness.
 *
 * <p>The work happens in {@link com.promptclient.mixin.LightmapTextureManagerMixin}: while this
 * module is enabled, every lightmap texel is written as pure white. The gamma option is never touched,
 * so there's nothing to persist to {@code options.txt} and no {@code SimpleOption} bounds validation to
 * get around. Vanilla rebuilds the lightmap every tick, so normal lighting comes back on the next
 * frame after the module is disabled.
 */
public final class Fullbright extends Module {
	public Fullbright() {
		super("Fullbright", "Lights up caves and dark areas.", Category.RENDER, GLFW.GLFW_KEY_G);
	}
}

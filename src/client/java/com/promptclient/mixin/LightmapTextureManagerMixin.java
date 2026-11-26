package com.promptclient.mixin;

import com.promptclient.PromptClient;
import com.promptclient.module.impl.render.Fullbright;
import net.minecraft.client.render.LightmapTextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Fullbright: in 1.21.1 {@code LightmapTextureManager#update(float)} computes the 16x16 lightmap
 * on the CPU and writes each texel with {@code NativeImage#setColor(int x, int y, int abgr)}.
 * We swap the color argument for opaque white while {@link Fullbright} is enabled.
 *
 * <p>{@code @ModifyArg} chains with other mods that target the same call (unlike {@code @Redirect}).
 */
@Mixin(LightmapTextureManager.class)
public abstract class LightmapTextureManagerMixin {
	private static final int FULL_BRIGHT = 0xFFFFFFFF;

	@ModifyArg(
			method = "update",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/texture/NativeImage;setColor(III)V"
			),
			index = 2
	)
	private int promptclient$fullbright(int color) {
		return PromptClient.getModuleManager().isEnabled(Fullbright.class) ? FULL_BRIGHT : color;
	}
}

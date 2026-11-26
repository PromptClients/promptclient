package com.promptclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.promptclient.PromptClient;
import com.promptclient.module.impl.render.Fullbright;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Fullbright hooks. In 1.21.1 {@code LightmapTextureManager#update(float)} computes the 16x16 lightmap on the
 * CPU once per tick. Targets (checked against 1.21.1 bytecode):
 * <ul>
 *   <li>{@code NativeImage#setColor(III)V}: the single texel write. <b>Lightmap</b> mode writes white.</li>
 *   <li>{@code Double#floatValue()} ordinal 1: the gamma read (ordinal 0 is the darkness-effect scale).
 *       <b>Gamma</b> mode substitutes its own value; the option is never touched.</li>
 *   <li>{@code ClientPlayerEntity#hasStatusEffect} ordinal 0 (night vision; ordinal 1 is conduit power) and
 *       {@code GameRenderer#getNightVisionStrength}: <b>NightVision</b> mode takes vanilla's night-vision path.</li>
 * </ul>
 */
@Mixin(LightmapTextureManager.class)
public abstract class LightmapTextureManagerMixin {
	@Unique
	private static final int FULL_BRIGHT = 0xFFFFFFFF;

	@ModifyArg(
			method = "update",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/texture/NativeImage;setColor(III)V"
			),
			index = 2
	)
	private int promptclient$fullbrightTexel(int color) {
		return fullbright().overridesLightmap() ? FULL_BRIGHT : color;
	}

	@ModifyExpressionValue(
			method = "update",
			at = @At(
					value = "INVOKE",
					target = "Ljava/lang/Double;floatValue()F",
					ordinal = 1
			)
	)
	private float promptclient$gamma(float gamma) {
		Fullbright fullbright = fullbright();
		return fullbright.overridesGamma() ? fullbright.getGamma() : gamma;
	}

	@ModifyExpressionValue(
			method = "update",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/network/ClientPlayerEntity;hasStatusEffect(Lnet/minecraft/registry/entry/RegistryEntry;)Z",
					ordinal = 0
			)
	)
	private boolean promptclient$forceNightVision(boolean hasNightVision) {
		return hasNightVision || fullbright().forcesNightVision();
	}

	/** Vanilla reads the effect's remaining duration here, which would throw without a real effect. */
	@WrapOperation(
			method = "update",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/render/GameRenderer;getNightVisionStrength(Lnet/minecraft/entity/LivingEntity;F)F"
			)
	)
	private float promptclient$nightVisionStrength(LivingEntity entity, float tickDelta, Operation<Float> original) {
		return fullbright().forcesNightVision() ? 1.0f : original.call(entity, tickDelta);
	}

	@Unique
	private static Fullbright fullbright() {
		return PromptClient.getModuleManager().get(Fullbright.class);
	}
}

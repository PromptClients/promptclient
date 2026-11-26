package com.promptclient.mixin;

import com.promptclient.util.RotationManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * While silent rotations are actively spoofing the server look, the visible crosshair no longer matches the
 * server rotation. Vanilla attacks would use the camera rotation and reset the cooldown mid-aura, so entity
 * attacks are suppressed here. Block attacks are left alone.
 */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
	@Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
	private void promptclient$suppressVanillaEntityAttack(CallbackInfoReturnable<Boolean> cir) {
		RotationManager rotation = RotationManager.get();
		MinecraftClient client = (MinecraftClient) (Object) this;

		if (rotation.isSilent() && rotation.isActive() && client.crosshairTarget instanceof EntityHitResult) {
			cir.setReturnValue(true);
		}
	}
}

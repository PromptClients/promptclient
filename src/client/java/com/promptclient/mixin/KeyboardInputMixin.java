package com.promptclient.mixin;

import com.promptclient.util.RotationManager;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Rotates the freshly sampled movement input to match the silent server yaw (MoveFix). */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin {
	@Inject(method = "tick", at = @At("RETURN"))
	private void promptclient$applyMoveFix(boolean slowDown, float slowDownFactor, CallbackInfo ci) {
		RotationManager.get().applyMovementFix((Input) (Object) this);
	}
}

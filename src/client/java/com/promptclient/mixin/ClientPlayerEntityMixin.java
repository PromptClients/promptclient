package com.promptclient.mixin;

import com.promptclient.util.RotationManager;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies silent rotations around the movement packet: the pre-movement task (attack ordering) and the
 * spoofed look values are installed at HEAD, then the visible camera values are restored at RETURN.
 */
@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin {
	@Inject(method = "sendMovementPackets", at = @At("HEAD"))
	private void promptclient$beforeMovementPackets(CallbackInfo ci) {
		RotationManager rotation = RotationManager.get();
		rotation.runPreMovementTask();
		rotation.pushServerRotation((ClientPlayerEntity) (Object) this);
	}

	@Inject(method = "sendMovementPackets", at = @At("RETURN"))
	private void promptclient$afterMovementPackets(CallbackInfo ci) {
		RotationManager.get().popServerRotation((ClientPlayerEntity) (Object) this);
	}
}

package com.promptclient.mixin;

import com.promptclient.util.RotationManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla applies the sprint-jump velocity boost along {@code getYaw()}, which is the visible camera.
 * With silent rotations the movement packet carries a different yaw, so the server simulates the jump
 * along another direction and flags Simulation. Re-aim the 0.2 impulse along the server yaw.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	@Inject(method = "jump", at = @At("RETURN"))
	private void promptclient$fixSprintJumpImpulse(CallbackInfo ci) {
		if (!((Object) this instanceof ClientPlayerEntity player) || MinecraftClient.getInstance().player != player) {
			return;
		}

		RotationManager rotation = RotationManager.get();

		if (!player.isSprinting() || !rotation.isSilent() || !rotation.isActive()) {
			return;
		}

		float cameraYaw = player.getYaw();
		float serverYaw = rotation.getServerYaw();
		float delta = MathHelper.wrapDegrees(serverYaw - cameraYaw);

		if (Math.abs(delta) < 1.0E-3f) {
			return;
		}

		double cameraRad = cameraYaw * MathHelper.RADIANS_PER_DEGREE;
		double serverRad = serverYaw * MathHelper.RADIANS_PER_DEGREE;
		Vec3d velocity = player.getVelocity();

		player.setVelocity(velocity.x + 0.2 * (-Math.sin(serverRad) + Math.sin(cameraRad)), velocity.y,
				velocity.z + 0.2 * (Math.cos(serverRad) - Math.cos(cameraRad)));
	}
}

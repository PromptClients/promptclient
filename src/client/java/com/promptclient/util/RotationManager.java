package com.promptclient.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.Optional;
import java.util.Random;

/**
 * Central rotation controller shared by combat and world modules.
 *
 * <p>Modules call {@link #configure} and {@link #aimAt} every tick, then {@link #tick}. Silent rotations are
 * installed into {@code ClientPlayerEntity#sendMovementPackets} by {@code ClientPlayerEntityMixin}, so only the
 * look values that go into movement packets are spoofed and the visible camera stays untouched. The third-person
 * player model follows the spoofed rotation through the entity's render yaw/pitch, eased per tick.
 *
 * <p>Every applied delta is snapped to the sensitivity-based vanilla mouse step (GCD), and while silent rotation
 * is active {@link #applyMovementFix} rotates keyboard input so the world-space movement direction still matches
 * what the user is holding. {@link #canSee} raycasts with the current server-side rotation only.
 */
public final class RotationManager {
	private static final RotationManager INSTANCE = new RotationManager();

	private static final float MAX_PITCH = 89.9f;
	private static final float RENDER_EASE = 0.5f;
	private static final float AIM_EASE_ANGLE = 30.0f;
	private static final float JITTER_DEGREES = 0.3f;
	private static final float BASE_JITTER_DEGREES = 0.04f;

	private final Random random = new Random();

	private boolean active;
	private boolean silent;
	private boolean lockView;
	private boolean hasGoal;

	private float serverYaw;
	private float serverPitch;
	private float sentYaw;
	private float sentPitch;
	private float goalYaw;
	private float goalPitch;

	private float minTurnSpeed = 25.0f;
	private float maxTurnSpeed = 70.0f;
	private boolean gcdFix = true;
	private boolean moveFix = true;

	private float yawRemainder;
	private float pitchRemainder;
	private float speedRamp;

	private boolean pushed;
	private float savedYaw;
	private float savedPitch;

	private Runnable preMovementTask;

	private RotationManager() {
	}

	public static RotationManager get() {
		return INSTANCE;
	}

	/** Reads module settings into the controller. Safe to call every tick. */
	public void configure(boolean active, boolean silent, boolean lockView,
			float minTurnSpeed, float maxTurnSpeed, boolean gcdFix, boolean moveFix) {
		if (active && !this.active) {
			MinecraftClient client = MinecraftClient.getInstance();

			if (client.player != null) {
				this.serverYaw = client.player.getYaw();
				this.serverPitch = client.player.getPitch();
				this.sentYaw = this.serverYaw;
				this.sentPitch = this.serverPitch;
			}
		}

		this.active = active;
		this.silent = silent;
		this.lockView = lockView;
		this.minTurnSpeed = Math.min(minTurnSpeed, maxTurnSpeed);
		this.maxTurnSpeed = Math.max(minTurnSpeed, maxTurnSpeed);
		this.gcdFix = gcdFix;
		this.moveFix = moveFix;

		if (!active) {
			dropGoal();
		}
	}

	public boolean isActive() {
		return active && (hasGoal || isOffCamera());
	}

	public boolean isSilent() {
		return silent;
	}

	public float getServerYaw() {
		return serverYaw;
	}

	public float getServerPitch() {
		return serverPitch;
	}

	/** Rotation the server actually holds; use this for attack raycasts, never the next tick's goal. */
	public float getSentYaw() {
		return sentYaw;
	}

	public float getSentPitch() {
		return sentPitch;
	}

	public void setPreMovementTask(Runnable task) {
		this.preMovementTask = task;
	}

	/** Runs the registered pre-movement task (attack before the movement packet). */
	public void runPreMovementTask() {
		if (preMovementTask != null) {
			preMovementTask.run();
		}
	}

	/** Points the controller at an aim point on {@code target}. */
	public void aimAt(ClientPlayerEntity player, Entity target, int aimPoint, boolean microJitter) {
		if (!hasGoal) {
			speedRamp = 0.0f;
		}

		Box box = target.getBoundingBox();
		Vec3d eye = player.getEyePos();
		double centerX = (box.minX + box.maxX) * 0.5;
		double centerZ = (box.minZ + box.maxZ) * 0.5;
		double x;
		double y;
		double z;

		switch (aimPoint) {
			case 1 -> { // chest / center
				x = centerX;
				y = (box.minY + box.maxY) * 0.5;
				z = centerZ;
			}
			case 2 -> { // head
				x = centerX;
				y = box.maxY - 0.1;
				z = centerZ;
			}
			case 3 -> { // adaptive: closest point up close, head further out
				double closestX = MathHelper.clamp(eye.x, box.minX, box.maxX);
				double closestY = MathHelper.clamp(eye.y, box.minY, box.maxY);
				double closestZ = MathHelper.clamp(eye.z, box.minZ, box.maxZ);
				double distance = Math.sqrt(eye.squaredDistanceTo(closestX, closestY, closestZ));
				float blend = MathHelper.clamp((float) ((distance - 1.25) / 2.0), 0.0f, 1.0f);
				x = MathHelper.lerp(blend, closestX, centerX);
				y = MathHelper.lerp(blend, closestY, box.maxY - 0.1);
				z = MathHelper.lerp(blend, closestZ, centerZ);
			}
			default -> { // closest point on the hitbox
				x = MathHelper.clamp(eye.x, box.minX, box.maxX);
				y = MathHelper.clamp(eye.y, box.minY, box.maxY);
				z = MathHelper.clamp(eye.z, box.minZ, box.maxZ);
			}
		}

		double dx = x - eye.x;
		double dy = y - eye.y;
		double dz = z - eye.z;
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));

		float jitter = microJitter ? JITTER_DEGREES : BASE_JITTER_DEGREES;
		yaw += (float) random.nextGaussian() * jitter;
		pitch += (float) random.nextGaussian() * jitter * 0.6f;

		goalYaw = yaw;
		goalPitch = MathHelper.clamp(pitch, -MAX_PITCH, MAX_PITCH);
		hasGoal = true;
	}

	public void clearGoal() {
		hasGoal = false;
	}

	/**
	 * Advances the server-side rotation one tick: speed-limited, GCD-snapped, and eased out near the goal.
	 * Also eases the third-person model render rotation and, in Lock View mode, the actual camera.
	 */
	public void tick() {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientPlayerEntity player = client.player;

		if (player == null) {
			return;
		}

		if (!active) {
			serverYaw = player.getYaw();
			serverPitch = player.getPitch();
			sentYaw = serverYaw;
			sentPitch = serverPitch;
			hasGoal = false;
			pushed = false;
			restoreRenderRotation(player);
			return;
		}

		if (!hasGoal) {
			// Teleports, portals and spawn snaps can move the camera far outside our turn budget. Snap the
			// server rotation to it instead of crawling there and desyncing movement for seconds.
			float cameraGap = MathHelper.wrapDegrees(player.getYaw() - serverYaw);

			if (Math.abs(cameraGap) > 90.0f) {
				serverYaw = player.getYaw();
				yawRemainder = 0.0f;
			}

			goalYaw = player.getYaw();
			goalPitch = player.getPitch();
		}

		float deltaYaw = MathHelper.wrapDegrees(goalYaw - serverYaw);
		float deltaPitch = goalPitch - serverPitch;
		float remaining = (float) Math.sqrt(deltaYaw * deltaYaw + deltaPitch * deltaPitch);
		float stepYaw;
		float stepPitch;

		if (remaining <= 0.01f) {
			stepYaw = deltaYaw;
			stepPitch = deltaPitch;
			yawRemainder = 0.0f;
			pitchRemainder = 0.0f;
		} else {
			float speed = maxTurnSpeed;

			// Sub-snap speeds ramp in, then ease out as the remaining angle shrinks.
			if (maxTurnSpeed < 180.0f) {
				speedRamp = Math.min(1.0f, speedRamp + 0.3f);
				float eased = MathHelper.clamp(remaining / AIM_EASE_ANGLE * maxTurnSpeed, minTurnSpeed, maxTurnSpeed);
				speed = Math.max(minTurnSpeed * speedRamp, eased * speedRamp);
			}

			speed = Math.min(speed, remaining);
			stepYaw = deltaYaw / remaining * speed;
			stepPitch = deltaPitch / remaining * speed;

			if (gcdFix) {
				float gcd = mouseGcd(client);
				yawRemainder += stepYaw;
				pitchRemainder += stepPitch;
				stepYaw = Math.round(yawRemainder / gcd) * gcd;
				stepPitch = Math.round(pitchRemainder / gcd) * gcd;
				yawRemainder -= stepYaw;
				pitchRemainder -= stepPitch;
			} else {
				yawRemainder = 0.0f;
				pitchRemainder = 0.0f;
			}
		}

		// Vanilla yaw is unbounded; wrapping it makes clockwise turns snap ~360 at the ±180 edge
		// (Grim AimModulo360) and desyncs the client-side movement rotation space.
		serverYaw += stepYaw;

		float newPitch = serverPitch + stepPitch;

		if (newPitch > MAX_PITCH || newPitch < -MAX_PITCH) {
			pitchRemainder = 0.0f;
		}

		serverPitch = MathHelper.clamp(newPitch, -MAX_PITCH, MAX_PITCH);

		updateRenderRotation(player);

		if (lockView) {
			player.setYaw(serverYaw);
			player.setPitch(serverPitch);
		}
	}

	/** Stops controlling rotations. The caller is responsible for the module's own cleanup. */
	public void release() {
		active = false;
		hasGoal = false;
		silent = false;
		lockView = false;
		pushed = false;
		MinecraftClient client = MinecraftClient.getInstance();

		if (client.player != null) {
			sentYaw = client.player.getYaw();
			sentPitch = client.player.getPitch();
		}
		yawRemainder = 0.0f;
		pitchRemainder = 0.0f;
		speedRamp = 0.0f;
		preMovementTask = null;
	}

	/** Installs the server rotation into the player for the duration of one movement packet. */
	public void pushServerRotation(ClientPlayerEntity player) {
		if (!silent || !isActive()) {
			return;
		}

		savedYaw = player.getYaw();
		savedPitch = player.getPitch();
		pushed = true;
		player.setYaw(serverYaw);
		player.setPitch(serverPitch);
	}

	/** Restores the visible rotation after the movement packet was sent. */
	public void popServerRotation(ClientPlayerEntity player) {
		if (!pushed) {
			return;
		}

		// The movement packet was just built; this is now the rotation the server holds.
		sentYaw = serverYaw;
		sentPitch = serverPitch;
		player.setYaw(savedYaw);
		player.setPitch(savedPitch);
		pushed = false;
	}

	/** Rotates keyboard input so strafing still follows the spoofed server yaw. */
	public void applyMovementFix(Input input) {
		if (!moveFix || !silent || !active) {
			return;
		}

		ClientPlayerEntity player = MinecraftClient.getInstance().player;

		if (player == null || player.input != input || player.hasVehicle()) {
			return;
		}

		float delta = MathHelper.wrapDegrees(serverYaw - player.getYaw());

		if (Math.abs(delta) < 1.0E-3f) {
			return;
		}

		float radians = delta * MathHelper.RADIANS_PER_DEGREE;
		float cos = (float) Math.cos(radians);
		float sin = (float) Math.sin(radians);
		float forward = input.movementForward;
		float sideways = input.movementSideways;

		input.movementForward = forward * cos + sideways * sin;
		input.movementSideways = sideways * cos - forward * sin;

	}

	/**
	 * Tests the current server-side crosshair against {@code target}'s expanded box within {@code reach}.
	 * Blocks in the way only stop the hit when the target is farther than {@code wallRange} or when
	 * {@code ignoreWalls} is set.
	 */
	public boolean canSee(ClientPlayerEntity player, Entity target, double reach, double wallRange, boolean ignoreWalls) {
		// Attack validation uses the server-side player position: the last position this client sent.
		double eyeOffset = player.getEyeY() - player.getY();
		ClientPlayerEntityAccess access = (ClientPlayerEntityAccess) player;
		Vec3d eye = new Vec3d(access.promptclient$getLastX(), access.promptclient$getLastBaseY() + eyeOffset,
				access.promptclient$getLastZ());
		Vec3d direction = Vec3d.fromPolar(sentPitch, sentYaw);
		Vec3d end = eye.add(direction.multiply(reach));
		Optional<Vec3d> hit = target.getBoundingBox().raycast(eye, end);

		if (hit.isEmpty()) {
			return false;
		}

		double distance = eye.distanceTo(hit.get());

		if (distance > reach) {
			return false;
		}

		if (ignoreWalls || player.getWorld() == null) {
			return true;
		}

		BlockHitResult blockHit = player.getWorld().raycast(
				new RaycastContext(eye, hit.get(), RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));

		if (blockHit.getType() == HitResult.Type.MISS) {
			return true;
		}

		double blockDistance = eye.distanceTo(blockHit.getPos());
		return blockDistance >= distance - 1.0E-3 || distance <= wallRange;
	}

	/** Vanilla mouse step for the current sensitivity: {@code (s * 0.6 + 0.2)^3 * 1.2}. */
	public static float mouseGcd(MinecraftClient client) {
		double sensitivity = client.options.getMouseSensitivity().getValue();
		float f = (float) (sensitivity * 0.6 + 0.2);
		float gcd = f * f * f * 1.2f;
		return gcd <= 0.0f ? 1.0E-4f : gcd;
	}

	private boolean isOffCamera() {
		ClientPlayerEntity player = MinecraftClient.getInstance().player;

		if (player == null) {
			return false;
		}

		return Math.abs(MathHelper.wrapDegrees(serverYaw - player.getYaw())) > 0.01f
				|| Math.abs(serverPitch - player.getPitch()) > 0.01f;
	}

	private void updateRenderRotation(ClientPlayerEntity player) {
		player.lastRenderYaw = player.renderYaw;
		player.lastRenderPitch = player.renderPitch;
		player.renderYaw += MathHelper.wrapDegrees(serverYaw - player.renderYaw) * RENDER_EASE;
		player.renderPitch += (serverPitch - player.renderPitch) * RENDER_EASE;
	}

	private void restoreRenderRotation(ClientPlayerEntity player) {
		player.lastRenderYaw = player.renderYaw;
		player.lastRenderPitch = player.renderPitch;
		player.renderYaw += MathHelper.wrapDegrees(player.getYaw() - player.renderYaw) * RENDER_EASE;
		player.renderPitch += (player.getPitch() - player.renderPitch) * RENDER_EASE;
	}

	private void dropGoal() {
		hasGoal = false;
		yawRemainder = 0.0f;
		pitchRemainder = 0.0f;
		speedRamp = 0.0f;
	}
}

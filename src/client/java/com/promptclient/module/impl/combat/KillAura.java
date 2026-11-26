package com.promptclient.module.impl.combat;

import com.promptclient.module.Category;
import com.promptclient.util.ClientPlayerEntityAccess;
import com.promptclient.module.Module;
import com.promptclient.setting.BooleanSetting;
import com.promptclient.setting.ModeSetting;
import com.promptclient.setting.NumberSetting;
import com.promptclient.ui.Theme;
import com.promptclient.util.RotationManager;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Modular combat aura with anti-cheat profile presets.
 *
 * <p>All aim work goes through {@link RotationManager}: it installs silent look values only inside movement
 * packets, quantizes every delta to the vanilla mouse GCD, and corrects keyboard input (MoveFix). Presets are
 * ordinary setting combinations, so they can be tweaked after selecting one. Strict raycasts use the spoofed
 * server rotation, never the camera. The target ring is drawn in {@code WorldRenderEvents.AFTER_ENTITIES}.
 */
public final class KillAura extends Module {
	private static final int ROT_SILENT = 0;
	private static final int ROT_LOCK_VIEW = 1;
	private static final int ROT_NONE = 2;
	private static final int TARGET_SINGLE = 0;
	private static final int TARGET_SWITCH = 1;
	private static final int TARGET_MULTI = 2;
	private static final int SWING_MAIN = 0;
	private static final int SWING_PACKET = 1;
	private static final int SPRINT_NONE = 0;
	private static final int SPRINT_W_TAP = 1;
	private static final int SPRINT_PACKET = 2;
	private static final int RING_SEGMENTS = 48;
	private static final float[] RING_COS = new float[RING_SEGMENTS];
	private static final float[] RING_SIN = new float[RING_SEGMENTS];

	static {
		for (int i = 0; i < RING_SEGMENTS; i++) {
			double angle = Math.PI * 2.0 * i / RING_SEGMENTS;
			RING_COS[i] = (float) Math.cos(angle);
			RING_SIN[i] = (float) Math.sin(angle);
		}
	}

	private final Random random = new Random();
	private final List<LivingEntity> candidates = new ArrayList<>();
	private final List<LivingEntity> attackTargets = new ArrayList<>();
	private final Runnable preMovementTask = this::attackNow;

	private final Comparator<LivingEntity> byDistance = Comparator.comparingDouble(this::distanceSq);
	private final Comparator<LivingEntity> byAngle = Comparator.comparingDouble(this::angleTo);

	private final ModeSetting preset = addSetting(new ModeSetting("Preset",
			"Anti-cheat profile: Vanilla/Legit, Grim, Polar, Vulcan or Blatant.", "Custom",
			"Custom", "Legit", "Grim", "Polar", "Vulcan", "Blatant"));

	// Targeting
	private final ModeSetting targetMode = addSetting(new ModeSetting("Targets",
			"Single, delayed switching or every valid target.", "Single", "Single", "Switch", "Multi"));
	private final NumberSetting switchDelay = addSetting(new NumberSetting("Switch",
			"Milliseconds before switching targets.", 250, 50, 1000, 10).suffix("ms")
			.visibleWhen(() -> targetMode.getIndex() == TARGET_SWITCH));
	private final ModeSetting sortPriority = addSetting(new ModeSetting("Sort",
			"Order used to choose a target.", "Distance", "Distance", "Health", "Angle", "HurtTime"));
	private final NumberSetting range = addSetting(new NumberSetting("Range",
			"Attack reach in blocks.", 3.0, 1.0, 6.0, 0.05));
	private final NumberSetting wallRange = addSetting(new NumberSetting("Wall Range",
			"Reach through blocks; 0 disables.", 0.0, 0.0, 4.5, 0.05));
	private final NumberSetting fov = addSetting(new NumberSetting("FOV",
			"Horizontal target cone.", 180.0, 10.0, 360.0, 5.0).suffix("°"));
	private final BooleanSetting players = addSetting(new BooleanSetting("Players", "Target players.", true));
	private final BooleanSetting hostiles = addSetting(new BooleanSetting("Hostile Mobs", "Target monsters.", true));
	private final BooleanSetting passives = addSetting(new BooleanSetting("Passives", "Target animals.", false));
	private final BooleanSetting invisibles = addSetting(new BooleanSetting("Invisibles", "Target invisible entities.", false));
	private final BooleanSetting ignoreTeams = addSetting(new BooleanSetting("Teams",
			"Skip teammates and same armor color.", true));
	private final BooleanSetting antiBot = addSetting(new BooleanSetting("Anti-Bot",
			"Skip fresh, tab-less or hovering entities.", false));

	// Timing
	private final ModeSetting attackMode = addSetting(new ModeSetting("Attack",
			"1.9+ cooldown gate or a fixed attack rate.", "1.9+", "1.9+", "Custom APS"));
	private final NumberSetting cooldownThreshold = addSetting(new NumberSetting("Threshold",
			"Cooldown fraction required before attacking.", 0.92, 0.75, 1.0, 0.01)
			.visibleWhen(() -> attackMode.getIndex() == 0));
	private final BooleanSetting humanVariance = addSetting(new BooleanSetting("Variance",
			"Gaussian jitter on attack timing.", false));
	private final NumberSetting minAps = addSetting(new NumberSetting("Min APS",
			"Slowest attacks per second.", 8.0, 1.0, 20.0, 1.0)
			.visibleWhen(() -> attackMode.getIndex() == 1));
	private final NumberSetting maxAps = addSetting(new NumberSetting("Max APS",
			"Fastest attacks per second.", 12.0, 1.0, 20.0, 1.0)
			.visibleWhen(() -> attackMode.getIndex() == 1));
	private final ModeSetting sprintReset = addSetting(new ModeSetting("Sprint",
			"Release sprint for crisper knockback.", "W-Tap", "None", "W-Tap", "Packet"));
	private final BooleanSetting pauseEating = addSetting(new BooleanSetting("Pause Eating",
			"Stop attacking while using an item.", true));

	// Rotation
	private final ModeSetting rotationMode = addSetting(new ModeSetting("Rotate",
			"Silent spoofs the server look only.", "Silent", "Silent", "Lock View", "None"));
	private final ModeSetting aimPoint = addSetting(new ModeSetting("Aim",
			"Where on the hitbox to aim.", "Closest", "Closest", "Center", "Head", "Adaptive")
			.visibleWhen(() -> rotationMode.getIndex() != ROT_NONE));
	private final NumberSetting minTurn = addSetting(new NumberSetting("Min Turn",
			"Slowest turn speed per tick.", 25.0, 5.0, 180.0, 1.0).suffix("°")
			.visibleWhen(() -> rotationMode.getIndex() != ROT_NONE));
	private final NumberSetting maxTurn = addSetting(new NumberSetting("Max Turn",
			"Fastest turn speed per tick.", 70.0, 5.0, 180.0, 1.0).suffix("°")
			.visibleWhen(() -> rotationMode.getIndex() != ROT_NONE));
	private final BooleanSetting gcdFix = addSetting(new BooleanSetting("GCD Fix",
			"Snap rotation deltas to vanilla mouse steps.", true)
			.visibleWhen(() -> rotationMode.getIndex() != ROT_NONE));
	private final BooleanSetting moveFix = addSetting(new BooleanSetting("Move Fix",
			"Correct movement against the silent yaw.", true)
			.visibleWhen(() -> rotationMode.getIndex() == ROT_SILENT));
	private final BooleanSetting strictRaycast = addSetting(new BooleanSetting("Raycast",
			"Only attack when the server crosshair ray hits.", true));
	private final BooleanSetting throughWalls = addSetting(new BooleanSetting("Walls",
			"Ignore block occlusion.", false));
	private final BooleanSetting packetOrder = addSetting(new BooleanSetting("Order",
			"Attack before the movement packet is sent.", true));
	private final BooleanSetting rotationJitter = addSetting(new BooleanSetting("Jitter",
			"Gaussian micro-jitter on the aim point.", false)
			.visibleWhen(() -> rotationMode.getIndex() == ROT_SILENT));
	private final ModeSetting swingHand = addSetting(new ModeSetting("Swing",
			"How the arm swings.", "Main Hand", "Main Hand", "Silent", "None"));

	private final NumberSetting multiLimit = addSetting(new NumberSetting("Multi Limit",
			"Max attacks per swing beat in Multi mode.", 1.0, 1.0, 5.0, 1.0)
			.visibleWhen(() -> targetMode.getIndex() == TARGET_MULTI));

	// Visuals
	private final BooleanSetting targetEsp = addSetting(new BooleanSetting("Target ESP",
			"Glowing ring around the locked target.", true));
	private final BooleanSetting debug = addSetting(new BooleanSetting("Debug",
			"Show targeting and rotation diagnostics.", false));

	private LivingEntity currentTarget;
	private long switchTimer;
	private long nextAttackMs;
	private int multiCursor;
	private int debugAttacks;
	private boolean resumeSprint;
	private boolean resumeSprintPacket;

	public KillAura() {
		super("KillAura", "Automatically attacks entities in range.", Category.COMBAT, GLFW.GLFW_KEY_R);
		preset.onChange(this::applyPreset);
		WorldRenderEvents.AFTER_ENTITIES.register(this::renderTargetEsp);
		HudRenderCallback.EVENT.register(this::renderDebugHud);
	}

	@Override
	protected void onEnable() {
		debugAttacks = 0;
		nextAttackMs = 0L;

		if (preset.getIndex() != 0) {
			applyPreset(preset.getIndex());
		}
	}

	@Override
	protected void onDisable() {
		RotationManager.get().release();
		currentTarget = null;
		candidates.clear();
		attackTargets.clear();
		resumeSprint = false;
		resumeSprintPacket = false;
		nextAttackMs = 0L;
	}

	@Override
	protected void onTick() {
		ClientPlayerEntity player = client().player;

		if (player == null || client().world == null || client().interactionManager == null) {
			RotationManager.get().release();
			return;
		}

		RotationManager rotation = RotationManager.get();
		int rotationIndex = rotationMode.getIndex();
		boolean silent = rotationIndex == ROT_SILENT;

		rotation.configure(rotationIndex != ROT_NONE, silent, rotationIndex == ROT_LOCK_VIEW,
				(float) minTurn.getValue(), (float) maxTurn.getValue(), gcdFix.getValue(), moveFix.getValue());
		rotation.setPreMovementTask(packetOrder.getValue() ? preMovementTask : null);

		collectCandidates();
		selectTarget();

		if (currentTarget != null) {
			rotation.aimAt(player, currentTarget, aimPoint.getIndex(), silent && rotationJitter.getValue());
		} else {
			rotation.clearGoal();
		}

		resumeSprintIfNeeded(player);

		if (!packetOrder.getValue()) {
			attackNow(); // uses the rotation already sent this tick, never the next one
		}

		rotation.tick();
	}

	// ---------------------------------------------------------------- targeting

	private void collectCandidates() {
		candidates.clear();
		ClientPlayerEntity player = client().player;

		if (player == null || client().world == null) {
			return;
		}

		double reach = range.getValue();
		double maxSquared = reach * reach;
		float halfFov = (float) fov.getValue() * 0.5f;
		boolean limitFov = fov.getValue() < 360.0;

		for (Entity entity : client().world.getEntities()) {
			if (!(entity instanceof LivingEntity living) || !isValidTarget(living)) {
				continue;
			}

			if (distanceSqToHitbox(player, living) > maxSquared) {
				continue;
			}

			if (limitFov && Math.abs(MathHelper.wrapDegrees(angleTo(living))) > halfFov) {
				continue;
			}

			candidates.add(living);
		}

		if (candidates.size() < 2) {
			return;
		}

		switch (sortPriority.getIndex()) {
			case 1 -> candidates.sort(Comparator.comparingDouble(LivingEntity::getHealth));
			case 2 -> candidates.sort(byAngle);
			case 3 -> candidates.sort(Comparator.comparingInt(entity -> entity.hurtTime));
			default -> candidates.sort(byDistance);
		}
	}

	private void selectTarget() {
		if (candidates.isEmpty()) {
			currentTarget = null;
			return;
		}

		if (targetMode.getIndex() == TARGET_MULTI) {
			multiCursor %= candidates.size();
			currentTarget = candidates.get(multiCursor);
			return;
		}

		if (targetMode.getIndex() == TARGET_SWITCH) {
			long now = Util.getMeasuringTimeMs();

			if (currentTarget == null || !candidates.contains(currentTarget)) {
				currentTarget = candidates.get(0);
				switchTimer = now + (long) switchDelay.getValue();
			} else if (now >= switchTimer) {
				int index = candidates.indexOf(currentTarget);
				currentTarget = candidates.get((index + 1) % candidates.size());
				switchTimer = now + (long) switchDelay.getValue();
			}

			return;
		}

		currentTarget = candidates.get(0);
	}

	private boolean isValidTarget(LivingEntity entity) {
		ClientPlayerEntity player = client().player;

		if (player == null || entity == player || !entity.isAlive() || entity.isRemoved()) {
			return false;
		}

		boolean isPlayer = entity instanceof PlayerEntity;
		boolean hostile = entity instanceof Monster || entity.getType().getSpawnGroup() == SpawnGroup.MONSTER;

		if (!isPlayer && !(entity instanceof MobEntity)) {
			return false;
		}

		if (isPlayer && !players.getValue()) {
			return false;
		}

		if (!isPlayer && hostile && !hostiles.getValue()) {
			return false;
		}

		if (!isPlayer && !hostile && !passives.getValue()) {
			return false;
		}

		if (entity.isInvisible() && !invisibles.getValue()) {
			return false;
		}

		if (ignoreTeams.getValue() && (player.isTeammate(entity)
				|| player.getScoreboardTeam() != null && entity.getScoreboardTeam() != null
				&& entity.getTeamColorValue() == player.getTeamColorValue())) {
			return false;
		}

		if (antiBot.getValue() && isBot(entity)) {
			return false;
		}

		return true;
	}

	private boolean isBot(LivingEntity entity) {
		// Freshly spawned entities have not been simulated yet.
		if (entity.age <= 1) {
			return true;
		}

		// Players without a tab list entry are usually NPCs.
		if (entity instanceof PlayerEntity player
				&& client().getNetworkHandler() != null
				&& client().getNetworkHandler().getPlayerListEntry(player.getUuid()) == null) {
			return true;
		}

		// Hovering vertically with no gravity. NoAI mobs never update onGround, so test the block below too.
		if (entity.isOnGround() || isSupported(entity)) {
			return false;
		}

		Vec3d velocity = entity.getVelocity();
		return Math.abs(velocity.y) < 1.0E-4 && entity.age > 40
				&& !entity.isTouchingWater() && !entity.isSwimming() && !entity.hasVehicle();
	}

	private boolean isSupported(LivingEntity entity) {
		BlockPos below = BlockPos.ofFloored(entity.getX(), entity.getY() - 0.1, entity.getZ());
		return !entity.getWorld().getBlockState(below).getCollisionShape(entity.getWorld(), below).isEmpty();
	}

	private double distanceSq(LivingEntity entity) {
		ClientPlayerEntity player = client().player;
		return player == null ? Double.MAX_VALUE : player.squaredDistanceTo(entity.getBoundingBox().getCenter());
	}

	private double angleTo(LivingEntity entity) {
		ClientPlayerEntity player = client().player;

		if (player == null) {
			return 0.0;
		}

		double degrees = Math.toDegrees(Math.atan2(-(entity.getX() - player.getX()), entity.getZ() - player.getZ()));
		return MathHelper.wrapDegrees(degrees - player.getYaw());
	}

	// ---------------------------------------------------------------- attacking

	private void attackNow() {
		ClientPlayerEntity player = client().player;

		if (player == null || client().world == null || client().interactionManager == null
				|| currentTarget == null || !isValidTarget(currentTarget)) {
			return;
		}

		if (pauseEating.getValue() && player.isUsingItem()) {
			return;
		}

		long now = Util.getMeasuringTimeMs();

		if (now < nextAttackMs) {
			return;
		}

		if (attackMode.getIndex() == 0) {
			float threshold = (float) cooldownThreshold.getValue();

			if (humanVariance.getValue()) {
				threshold = MathHelper.clamp(threshold + (float) random.nextGaussian() * 0.02f, 0.75f, 1.0f);
			}

			if (player.getAttackCooldownProgress(0.5f) < threshold) {
				return;
			}
		}

		attackTargets.clear();
		boolean multi = targetMode.getIndex() == TARGET_MULTI;

		if (multi) {
			int limit = (int) multiLimit.getValue();
			int size = candidates.size();
			int start = Math.max(0, candidates.indexOf(currentTarget));

			for (int step = 0; step < size && attackTargets.size() < limit; step++) {
				LivingEntity candidate = candidates.get((start + step) % size);

				if (canAttack(player, candidate)) {
					attackTargets.add(candidate);
				}
			}

			multiCursor = (start + Math.max(1, attackTargets.size())) % size;
		} else if (canAttack(player, currentTarget)) {
			attackTargets.add(currentTarget);
		}

		if (attackTargets.isEmpty()) {
			return;
		}

		applySprintReset(player);

		for (int i = 0; i < attackTargets.size(); i++) {
					debugAttacks++;
					client().interactionManager.attackEntity(player, attackTargets.get(i));
			swing(player);
		}

		nextAttackMs = now + 50L;
		scheduleNextAttack(now);
	}

	private boolean canAttack(ClientPlayerEntity player, LivingEntity target) {
		double reach = range.getValue();

		if (distanceSqToHitbox(player, target) > reach * reach) {
			return false;
		}

		return !strictRaycast.getValue()
				|| RotationManager.get().canSee(player, target, reach, wallRange.getValue(), throughWalls.getValue());
	}

	/** Squared eye distance to the closest point of the hitbox, so reach is not penalized by entity height. */
	private static double distanceSqToHitbox(ClientPlayerEntity player, LivingEntity target) {
		Box box = target.getBoundingBox();
		double dx = MathHelper.clamp(player.getX(), box.minX, box.maxX) - player.getX();
		double dy = MathHelper.clamp(player.getEyeY(), box.minY, box.maxY) - player.getEyeY();
		double dz = MathHelper.clamp(player.getZ(), box.minZ, box.maxZ) - player.getZ();
		return dx * dx + dy * dy + dz * dz;
	}

	private void swing(ClientPlayerEntity player) {
		switch (swingHand.getIndex()) {
			case SWING_MAIN -> player.swingHand(Hand.MAIN_HAND);
			case SWING_PACKET -> {
				if (client().getNetworkHandler() != null) {
					client().getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
				}
			}
			default -> {
			}
		}
	}

	private void applySprintReset(ClientPlayerEntity player) {
		if (!player.isSprinting() || sprintReset.getIndex() == SPRINT_NONE) {
			return;
		}

		boolean packet = sprintReset.getIndex() == SPRINT_PACKET;

		if (packet && client().getNetworkHandler() != null) {
			client().getNetworkHandler().sendPacket(
					new ClientCommandC2SPacket(player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
		}

		player.setSprinting(false);
		((ClientPlayerEntityAccess) player).promptclient$setLastSprinting(false);
		resumeSprint = true;
		resumeSprintPacket = packet;
	}

	private void resumeSprintIfNeeded(ClientPlayerEntity player) {
		if (!resumeSprint) {
			return;
		}

		resumeSprint = false;
		boolean packet = resumeSprintPacket;
		resumeSprintPacket = false;
		player.setSprinting(true);

		if (packet) {
			if (client().getNetworkHandler() != null) {
				client().getNetworkHandler().sendPacket(
						new ClientCommandC2SPacket(player, ClientCommandC2SPacket.Mode.START_SPRINTING));
			}

			((ClientPlayerEntityAccess) player).promptclient$setLastSprinting(true);
		}
	}

	private void scheduleNextAttack(long now) {
		if (attackMode.getIndex() != 1) {
			return;
		}

		double min = minAps.getValue();
		double max = Math.max(min, maxAps.getValue());
		double aps = min + random.nextDouble() * (max - min);
		long delay = (long) (1000.0 / Math.max(1.0, aps));

		if (humanVariance.getValue()) {
			delay = Math.round(delay * (1.0 + random.nextGaussian() * 0.08));
		}

		nextAttackMs = now + Math.max(50L, delay);
	}

	// ---------------------------------------------------------------- presets

	/** Fills the settings for a profile. {@code Custom} keeps the current values untouched. */
	private void applyPreset(int index) {
		if (index <= 0) {
			return;
		}

		targetMode.setMode("Single");
		switchDelay.setValue(250.0);
		sortPriority.setMode("Distance");
		range.setValue(3.0);
		wallRange.setValue(0.0);
		fov.setValue(180.0);
		players.setValue(true);
		hostiles.setValue(true);
		passives.setValue(false);
		invisibles.setValue(false);
		ignoreTeams.setValue(true);
		antiBot.setValue(false);
		attackMode.setMode("1.9+");
		cooldownThreshold.setValue(0.95);
		humanVariance.setValue(true);
		minAps.setValue(8.0);
		maxAps.setValue(12.0);
		sprintReset.setMode("W-Tap");
		pauseEating.setValue(true);
		rotationMode.setMode("Silent");
		aimPoint.setMode("Closest");
		minTurn.setValue(25.0);
		maxTurn.setValue(70.0);
		gcdFix.setValue(true);
		moveFix.setValue(true);
		strictRaycast.setValue(true);
		throughWalls.setValue(false);
		packetOrder.setValue(true);
		rotationJitter.setValue(false);
		swingHand.setMode("Main Hand");
		multiLimit.setValue(1.0);
		targetEsp.setValue(true);

		switch (index) {
			case 1 -> { // Vanilla / Legit
				fov.setValue(90.0);
				cooldownThreshold.setValue(1.0);
				aimPoint.setMode("Center");
				minTurn.setValue(35.0);
				maxTurn.setValue(80.0);
			}
			case 2 -> { // Grim / Simulation
				fov.setValue(120.0);
				cooldownThreshold.setValue(0.95);
				aimPoint.setMode("Closest");
				minTurn.setValue(30.0);
				maxTurn.setValue(70.0);
				packetOrder.setValue(true);
				sprintReset.setMode("Packet");
			}
			case 3 -> { // Polar / Heuristic
				fov.setValue(110.0);
				cooldownThreshold.setValue(0.93);
				humanVariance.setValue(true);
				rotationJitter.setValue(true);
				minTurn.setValue(30.0);
				maxTurn.setValue(60.0);
			}
			case 4 -> { // Vulcan / Matrix
				range.setValue(3.05);
				wallRange.setValue(0.5);
				fov.setValue(130.0);
				cooldownThreshold.setValue(0.95);
				humanVariance.setValue(true);
				aimPoint.setMode("Adaptive");
				minTurn.setValue(20.0);
				maxTurn.setValue(55.0);
				packetOrder.setValue(true);
			}
			case 5 -> { // Blatant / HvH
				humanVariance.setValue(false);
				targetMode.setMode("Multi");
				multiLimit.setValue(5.0);
				range.setValue(4.5);
				wallRange.setValue(4.5);
				fov.setValue(360.0);
				cooldownThreshold.setValue(0.75);
				passives.setValue(true);
				invisibles.setValue(true);
				aimPoint.setMode("Adaptive");
				minTurn.setValue(180.0);
				maxTurn.setValue(180.0);
				strictRaycast.setValue(false);
				throughWalls.setValue(true);
				packetOrder.setValue(false);
			}
			default -> {
			}
		}
	}

	// ---------------------------------------------------------------- debug HUD

	private void renderDebugHud(DrawContext context, RenderTickCounter tickCounter) {
		ClientPlayerEntity player = client().player;

		if (!isEnabled() || !debug.getValue() || player == null || client().world == null) {
			return;
		}

		RotationManager rotation = RotationManager.get();
		LivingEntity target = currentTarget;
		double distance = target == null ? -1.0 : Math.sqrt(distanceSqToHitbox(player, target));
		boolean ray = target != null && rotation.canSee(player, target, range.getValue(), wallRange.getValue(),
				throughWalls.getValue());
		int x = 8;
		int y = 32;

		context.drawTextWithShadow(client().textRenderer, "KillAura debug", x, y, Theme.accent());
		context.drawTextWithShadow(client().textRenderer,
				"target: " + (target == null ? "-" : target.getType().getName().getString()), x, y + 11, 0xFFFFFFFF);
		context.drawTextWithShadow(client().textRenderer,
				"dist: " + (distance < 0 ? "-" : String.format("%.2f", distance))
						+ "  ray: " + (ray ? "yes" : "no"), x, y + 22, 0xFFFFFFFF);
		context.drawTextWithShadow(client().textRenderer,
				"yaw: " + String.format("%.1f", player.getYaw()) + " -> " + String.format("%.1f", rotation.getServerYaw())
						+ "  pitch: " + String.format("%.1f", rotation.getServerPitch()), x, y + 33, 0xFFFFFFFF);
		context.drawTextWithShadow(client().textRenderer,
				"cd: " + String.format("%.2f", player.getAttackCooldownProgress(0.5f))
						+ "  hits: " + debugAttacks, x, y + 44, 0xFFFFFFFF);
		context.drawTextWithShadow(client().textRenderer,
				"mode: " + rotationMode.getMode() + "  order: " + (packetOrder.getValue() ? "pre" : "post")
						+ "  bot: " + antiBot.getValue(), x, y + 55, 0xFFFFFFFF);
	}

	// ---------------------------------------------------------------- target ring

	private void renderTargetEsp(WorldRenderContext context) {
		LivingEntity target = currentTarget;

		if (!isEnabled() || !targetEsp.getValue() || target == null || !target.isAlive()
				|| target.getWorld() != context.world()) {
			return;
		}

		MatrixStack matrices = context.matrixStack();
		VertexConsumerProvider consumers = context.consumers();

		if (matrices == null || consumers == null) {
			return;
		}

		float tickDelta = context.tickCounter().getTickDelta(true);
		Camera camera = context.camera();
		Vec3d cameraPos = camera.getPos();
		double x = MathHelper.lerp(tickDelta, target.lastRenderX, target.getX()) - cameraPos.x;
		double y = MathHelper.lerp(tickDelta, target.lastRenderY, target.getY()) - cameraPos.y;
		double z = MathHelper.lerp(tickDelta, target.lastRenderZ, target.getZ()) - cameraPos.z;
		double width = target.getWidth();
		double height = target.getHeight();
		double halfWidth = width * 0.5 + 0.06;
		double minY = y - 0.02;
		double maxY = y + height + 0.02;

		int accent = Theme.accent();
		float red = ((accent >> 16) & 0xFF) / 255.0f;
		float green = ((accent >> 8) & 0xFF) / 255.0f;
		float blue = (accent & 0xFF) / 255.0f;
		float pulse = (float) (Math.sin(Util.getMeasuringTimeMs() * 0.006) * 0.5 + 0.5);
		float boxAlpha = 0.22f + pulse * 0.12f;
		float ringAlpha = 0.45f + pulse * 0.3f;

		matrices.push();
		Matrix4f matrix = matrices.peek().getPositionMatrix();
		VertexConsumer buffer = consumers.getBuffer(RenderLayer.getLines());

		// Minimal bounding highlight.
		line(buffer, matrix, x - halfWidth, minY, z - halfWidth, x + halfWidth, minY, z - halfWidth, red, green, blue, boxAlpha);
		line(buffer, matrix, x + halfWidth, minY, z - halfWidth, x + halfWidth, minY, z + halfWidth, red, green, blue, boxAlpha);
		line(buffer, matrix, x + halfWidth, minY, z + halfWidth, x - halfWidth, minY, z + halfWidth, red, green, blue, boxAlpha);
		line(buffer, matrix, x - halfWidth, minY, z + halfWidth, x - halfWidth, minY, z - halfWidth, red, green, blue, boxAlpha);
		line(buffer, matrix, x - halfWidth, maxY, z - halfWidth, x + halfWidth, maxY, z - halfWidth, red, green, blue, boxAlpha);
		line(buffer, matrix, x + halfWidth, maxY, z - halfWidth, x + halfWidth, maxY, z + halfWidth, red, green, blue, boxAlpha);
		line(buffer, matrix, x + halfWidth, maxY, z + halfWidth, x - halfWidth, maxY, z + halfWidth, red, green, blue, boxAlpha);
		line(buffer, matrix, x - halfWidth, maxY, z + halfWidth, x - halfWidth, maxY, z - halfWidth, red, green, blue, boxAlpha);
		line(buffer, matrix, x - halfWidth, minY, z - halfWidth, x - halfWidth, maxY, z - halfWidth, red, green, blue, boxAlpha);
		line(buffer, matrix, x + halfWidth, minY, z - halfWidth, x + halfWidth, maxY, z - halfWidth, red, green, blue, boxAlpha);
		line(buffer, matrix, x + halfWidth, minY, z + halfWidth, x + halfWidth, maxY, z + halfWidth, red, green, blue, boxAlpha);
		line(buffer, matrix, x - halfWidth, minY, z + halfWidth, x - halfWidth, maxY, z + halfWidth, red, green, blue, boxAlpha);

		// Soft double ring at the feet.
		double ringRadius = Math.max(halfWidth + 0.22, 0.4);
		double ringY = minY + 0.03;

		for (int i = 0; i < RING_SEGMENTS; i++) {
			int next = (i + 1) % RING_SEGMENTS;
			line(buffer, matrix,
					x + RING_COS[i] * ringRadius, ringY, z + RING_SIN[i] * ringRadius,
					x + RING_COS[next] * ringRadius, ringY, z + RING_SIN[next] * ringRadius,
					red, green, blue, ringAlpha);
			line(buffer, matrix,
					x + RING_COS[i] * (ringRadius + 0.09), ringY + 0.03, z + RING_SIN[i] * (ringRadius + 0.09),
					x + RING_COS[next] * (ringRadius + 0.09), ringY + 0.03, z + RING_SIN[next] * (ringRadius + 0.09),
					red, green, blue, ringAlpha * 0.35f);
		}

		matrices.pop();
	}

	private static void line(VertexConsumer buffer, Matrix4f matrix,
			double x1, double y1, double z1, double x2, double y2, double z2,
			float red, float green, float blue, float alpha) {
		buffer.vertex(matrix, (float) x1, (float) y1, (float) z1).color(red, green, blue, alpha).normal(0.0f, 1.0f, 0.0f);
		buffer.vertex(matrix, (float) x2, (float) y2, (float) z2).color(red, green, blue, alpha).normal(0.0f, 1.0f, 0.0f);
	}
}

package com.promptclient;

import com.promptclient.module.impl.combat.KillAura;
import com.promptclient.setting.BooleanSetting;
import com.promptclient.setting.Setting;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.util.ScreenshotRecorder;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;

/** TEMP production harness, enabled by a promptclient-autotest.txt file in the game directory. */
public final class DevHarness {
	private static int step;
	private static int ticks;
	private static double lastX, lastZ;

	private DevHarness() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(DevHarness::tick);
	}

	public static boolean shouldRun() {
		return Files.exists(FabricLoader.getInstance().getGameDir().resolve("promptclient-autotest.txt"));
	}

	private static void tick(MinecraftClient client) {
		switch (step) {
			case 0 -> {
				if (client.currentScreen instanceof TitleScreen) {
					client.options.pauseOnLostFocus = false;
					client.options.getGuiScale().setValue(2);
					GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 1600, 900);
					client.onResolutionChanged();
					PromptClient.LOGGER.info("AUTOTEST connecting");
					String host = System.getenv().getOrDefault("PROMPTCLIENT_TEST_HOST", "anticheat-test.com");
					ConnectScreen.connect(new TitleScreen(), client, ServerAddress.parse(host),
							new ServerInfo("autotest", host, ServerInfo.ServerType.OTHER), false, null);
					step = 1;
				}
			}
			case 1 -> {
				if (client.player != null && client.world != null && client.getNetworkHandler() != null) {
					PromptClient.LOGGER.info("AUTOTEST joined as {}", client.player.getGameProfile().getName());
					client.options.getGuiScale().setValue(2);

					for (Setting setting : PromptClient.getModuleManager().get(KillAura.class).getSettings()) {
						if (setting instanceof BooleanSetting bool && bool.getName().equals("Debug")) {
							bool.setValue(true);
						}
						if (setting instanceof com.promptclient.setting.ModeSetting mode && mode.getName().equals("Preset")) {
							mode.setMode("Grim");
						}
						if (setting instanceof com.promptclient.setting.ModeSetting mode && mode.getName().equals("Rotate")) {
							mode.setMode("Silent");
						}

					}

					PromptClient.getModuleManager().get(KillAura.class).setEnabled(true);
					int total = 0;
					for (var entity : client.world.getEntities()) {
						total++;
						PromptClient.LOGGER.info("AUTOTEST entity {} at {} {} {}", entity.getType().getName().getString(),
								entity.getX(), entity.getY(), entity.getZ());
					}
					PromptClient.LOGGER.info("AUTOTEST entities={}", total);
					nearby(client);
					ticks = 0;
					step = 2;
				} else if (client.currentScreen instanceof net.minecraft.client.gui.screen.DisconnectedScreen) {
					PromptClient.LOGGER.info("AUTOTEST disconnected");
					step = 4;
				}
			}
			case 2 -> {
				if (ticks < 1) {
					client.options.forwardKey.setPressed(true);
					client.options.leftKey.setPressed(true);
					client.options.sprintKey.setPressed(true);
					client.options.jumpKey.setPressed(ticks % 200 == 0);

					if (ticks % 2 == 0) {
						client.player.setYaw(client.player.getYaw() + 3.0f);
					}
				} else {
					if (ticks == 1) {
						for (Setting setting : PromptClient.getModuleManager().get(KillAura.class).getSettings()) {
							if (setting instanceof com.promptclient.setting.NumberSetting number && number.getName().equals("FOV")) {
								number.setValue(360.0);
							}
						}
					}

					var nearest = client.world.getEntitiesByClass(net.minecraft.entity.LivingEntity.class,
							client.player.getBoundingBox().expand(48.0), other -> other != client.player && (other instanceof net.minecraft.entity.mob.MobEntity || other instanceof net.minecraft.entity.player.PlayerEntity)).stream()
							.min(java.util.Comparator.comparingDouble(client.player::distanceTo)).orElse(null);

					if (nearest != null) {
						client.options.sprintKey.setPressed(true);
						PromptClient.LOGGER.info("AUTOTEST seek {} d={} reject={}", nearest.getType().getName().getString(),
								client.player.distanceTo(nearest),
								PromptClient.getModuleManager().get(KillAura.class).debugReject(nearest));
						double dx = nearest.getX() - client.player.getX();
						double dz = nearest.getZ() - client.player.getZ();
						float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
						targetYaw += (float) (Math.sin(ticks * 0.25) * 6.0);
						client.player.setYaw(targetYaw);
						client.options.forwardKey.setPressed(client.player.distanceTo(nearest) > 2.2);
						client.options.leftKey.setPressed(false);
						client.options.jumpKey.setPressed(ticks % 200 == 0);
					} else {
						client.options.forwardKey.setPressed(false);
						client.options.sprintKey.setPressed(false);
						client.options.jumpKey.setPressed(false);
					}
				}

				double dx = client.player.getX() - lastX;
				double dz = client.player.getZ() - lastZ;

				if (dx * dx + dz * dz > 1.0E-6) {
					PromptClient.LOGGER.info("MOVE dir={} speed={}", Math.toDegrees(Math.atan2(-dx, dz)),
							Math.sqrt(dx * dx + dz * dz));
				}

				lastX = client.player.getX();
				lastZ = client.player.getZ();

				if (++ticks % 40 == 0) {
					int total = 0;
					int living = 0;
					for (var entity : client.world.getEntities()) {
						total++;
						if (entity instanceof net.minecraft.entity.LivingEntity) {
							living++;
						}
					}
					var aura = PromptClient.getModuleManager().get(KillAura.class);
					PromptClient.LOGGER.info("AUTOTEST tick={} pos={} {} {} yaw={} server={} state={} hits={} total={} living={} cd={} thr={} mode={} atkSpeed={}",
							ticks, String.format("%.2f", client.player.getX()), String.format("%.2f", client.player.getY()),
							String.format("%.2f", client.player.getZ()), client.player.getYaw(),
							com.promptclient.util.RotationManager.get().getServerYaw(),
							aura.debugState(), aura.debugAttacks(), total, living,
							String.format("%.3f", aura.debugCd()), String.format("%.3f", aura.debugThreshold()),
							aura.debugAttackMode(), client.player.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.GENERIC_ATTACK_SPEED));
					var sidebar = client.world.getScoreboard().getObjectiveForSlot(net.minecraft.scoreboard.ScoreboardDisplaySlot.SIDEBAR);
					if (sidebar != null) {
						PromptClient.LOGGER.info("AUTOTEST sidebar {}", sidebar.getDisplayName().getString());
					}
					if (ticks % 80 == 0) {
						nearby(client);
					}
					ScreenshotRecorder.saveScreenshot(client.runDirectory, "act-" + ticks + ".png", client.getFramebuffer(), message -> { });
				}

				if (ticks == 2400) {
					ScreenshotRecorder.saveScreenshot(client.runDirectory, "act-final.png", client.getFramebuffer(), message -> { });
					client.scheduleStop();
					step = 3;
				}
			}
			default -> {
			}
		}
	}

	private static void nearby(MinecraftClient client) {
		int shown = 0;
		for (var entity : client.world.getEntities()) {
			if (shown >= 8) {
				break;
			}
			if (entity instanceof net.minecraft.entity.player.PlayerEntity other && other != client.player) {
				shown++;
				PromptClient.LOGGER.info("AUTOTEST near {} d={} age={} alive={} removed={} invis={} team={} sameTeam={}",
						other.getGameProfile().getName(), client.player.distanceTo(other), other.age, other.isAlive(),
						other.isRemoved(), other.isInvisible(),
						other.getScoreboardTeam() == null ? "-" : other.getScoreboardTeam().getName(),
						client.player.isTeammate(other));
			}
		}
	}
}

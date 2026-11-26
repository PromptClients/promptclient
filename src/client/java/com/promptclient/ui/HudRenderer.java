package com.promptclient.ui;

import com.promptclient.PromptClient;
import com.promptclient.module.Module;
import com.promptclient.module.ModuleManager;
import com.promptclient.module.impl.render.Hud;
import com.promptclient.ui.anim.Anim;
import com.promptclient.ui.anim.FrameClock;
import com.promptclient.ui.render.Colors;
import com.promptclient.ui.render.RenderUtils;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Glass watermark badge at (8, 8) and the enabled-module list.
 *
 * <p>Per frame this does one shape batch and one text flush, and allocates nothing: the module list is
 * built and sorted by width once, widths are cached, and the FPS string is rebuilt at most every 250 ms.
 */
public final class HudRenderer implements HudRenderCallback {
	private static final float MARGIN = 8.0f;
	private static final float BADGE_HEIGHT = 16.0f;
	private static final float BADGE_PADDING = 7.0f;
	private static final float VERSION_PILL_HEIGHT = 11.0f;
	private static final float ROW_HEIGHT = 12.0f;
	private static final float ROW_GAP = 2.0f;
	private static final float ROW_PADDING = 5.0f;
	private static final float ACCENT_BAR = 2.0f;
	private static final float STATUS_EFFECT_ROW = 26.0f;
	private static final long FPS_INTERVAL_MS = 250L;

	private final ModuleManager moduleManager;
	private final FrameClock clock = new FrameClock();

	private Hud hud;
	private ListEntry[] entries;
	private String versionText;
	private int titleWidth;
	private int versionWidth;
	private String fpsText = "";
	private int fpsWidth;
	private int lastFps = -1;
	private long lastFpsUpdate;
	private float badgeWidth = -1.0f;
	private float listOffsetY;

	public HudRenderer(ModuleManager moduleManager) {
		this.moduleManager = moduleManager;
	}

	@Override
	public void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();

		// Respect F1, and stay out of the way of the F3 debug screen, which uses both top corners.
		if (client.options.hudHidden || client.getDebugHud().shouldShowDebugHud()) {
			clock.reset();
			return;
		}

		TextRenderer textRenderer = client.textRenderer;

		if (hud == null) {
			initialize(textRenderer);
		}

		float dt = clock.tick();

		if (!hud.isEnabled()) {
			return;
		}

		int accent = Theme.accent();
		updateFps(client, textRenderer);

		boolean watermark = hud.showWatermark();
		boolean showFps = watermark && hud.showFps();
		boolean list = hud.showModuleList();
		boolean listOnRight = hud.getListPosition() == Hud.LIST_TOP_RIGHT;
		float screenWidth = context.getScaledWindowWidth();

		// Keep the right-hand list clear of vanilla's status effect icons in the top-right corner.
		boolean effectsShown = listOnRight && client.player != null && !client.player.getStatusEffects().isEmpty();
		listOffsetY = Anim.approach(listOffsetY, effectsShown ? STATUS_EFFECT_ROW : 0.0f, 12.0f, dt);

		float listY = MARGIN + (listOnRight ? listOffsetY : (watermark ? BADGE_HEIGHT + 4.0f : 0.0f));
		layoutList(list, listOnRight, screenWidth, listY, dt);

		float targetBadge = badgeTargetWidth(showFps);
		badgeWidth = badgeWidth < 0.0f ? targetBadge : Anim.approach(badgeWidth, targetBadge, 18.0f, dt);

		// One batch for every shape on the HUD...
		RenderUtils.begin(context);
		if (watermark) {
			renderBadgeShapes(accent, showFps);
		}
		renderListShapes(accent, listOnRight);
		RenderUtils.end();

		// ...and one flush for all of its text.
		if (watermark) {
			renderBadgeText(context, textRenderer, accent, showFps);
		}
		renderListText(context, textRenderer);
		RenderUtils.flush(context);
		RenderUtils.setAlpha(1.0f);
	}

	private void initialize(TextRenderer textRenderer) {
		hud = moduleManager.get(Hud.class);
		titleWidth = textRenderer.getWidth(PromptClient.NAME);
		versionText = "v" + shortVersion();
		versionWidth = textRenderer.getWidth(versionText);

		List<ListEntry> built = new ArrayList<>();
		for (Module module : moduleManager.getModules()) {
			if (module.isVisibleInArrayList()) {
				built.add(new ListEntry(module, textRenderer.getWidth(module.getName())));
			}
		}

		// Names never change, so sorting once by width (longest first) is enough.
		built.sort(Comparator.comparingInt((ListEntry entry) -> entry.textWidth).reversed()
				.thenComparing(entry -> entry.module.getName()));
		entries = built.toArray(new ListEntry[0]);
	}

	/** "0.1.0" becomes "0.1". */
	private static String shortVersion() {
		String version = FabricLoader.getInstance().getModContainer(PromptClient.MOD_ID)
				.map(container -> container.getMetadata().getVersion().getFriendlyString())
				.orElse("dev");
		String[] parts = version.split("[.+-]");
		return parts.length >= 2 ? parts[0] + "." + parts[1] : version;
	}

	private void updateFps(MinecraftClient client, TextRenderer textRenderer) {
		long now = Util.getMeasuringTimeMs();

		if (now - lastFpsUpdate < FPS_INTERVAL_MS) {
			return;
		}

		lastFpsUpdate = now;
		int fps = client.getCurrentFps();

		if (fps != lastFps) {
			lastFps = fps;
			fpsText = fps + " FPS";
			fpsWidth = textRenderer.getWidth(fpsText);
		}
	}

	// ---------------------------------------------------------------- watermark badge

	private float badgeTargetWidth(boolean showFps) {
		float width = BADGE_PADDING + 8.0f + titleWidth + 5.0f + versionWidth + 8.0f + BADGE_PADDING;

		if (showFps) {
			width += 6.0f + 1.0f + 6.0f + fpsWidth;
		}

		return width;
	}

	private float versionPillX() {
		return MARGIN + BADGE_PADDING + 8.0f + titleWidth + 5.0f;
	}

	private void renderBadgeShapes(int accent, boolean showFps) {
		float radius = BADGE_HEIGHT / 2.0f;
		RenderUtils.shadow(MARGIN, MARGIN, badgeWidth, BADGE_HEIGHT, radius, 6.0f, 0x40000000);
		RenderUtils.roundedGradientV(MARGIN, MARGIN, badgeWidth, BADGE_HEIGHT, radius, Theme.HUD_GLASS_TOP, Theme.HUD_GLASS);
		RenderUtils.roundedOutline(MARGIN, MARGIN, badgeWidth, BADGE_HEIGHT, radius, 1.0f, Theme.HUD_BORDER);

		float dotX = MARGIN + BADGE_PADDING + 2.5f;
		float centerY = MARGIN + BADGE_HEIGHT / 2.0f;
		RenderUtils.circle(dotX, centerY, 4.0f, Colors.withAlpha(accent, 0x40));
		RenderUtils.circle(dotX, centerY, 2.5f, accent);

		float pillX = versionPillX();
		float pillY = MARGIN + (BADGE_HEIGHT - VERSION_PILL_HEIGHT) / 2.0f;
		RenderUtils.roundedRect(pillX, pillY, versionWidth + 8.0f, VERSION_PILL_HEIGHT, VERSION_PILL_HEIGHT / 2.0f,
				Colors.withAlpha(accent, 0x38));

		if (showFps) {
			float dividerX = pillX + versionWidth + 8.0f + 6.0f;
			RenderUtils.rect(dividerX, MARGIN + 4.0f, 1.0f, BADGE_HEIGHT - 8.0f, 0x33FFFFFF);
		}
	}

	private void renderBadgeText(DrawContext context, TextRenderer textRenderer, int accent, boolean showFps) {
		float textY = MARGIN + 4.0f;
		RenderUtils.text(context, textRenderer, PromptClient.NAME, MARGIN + BADGE_PADDING + 8.0f, textY, Theme.TEXT, false);

		float pillX = versionPillX();
		RenderUtils.text(context, textRenderer, versionText, pillX + 4.0f, textY, Colors.lerp(accent, Theme.TEXT, 0.35f), false);

		if (showFps) {
			float fpsX = pillX + versionWidth + 8.0f + 6.0f + 1.0f + 6.0f;
			RenderUtils.text(context, textRenderer, fpsText, fpsX, textY, Theme.TEXT_SECONDARY, false);
		}
	}

	// ---------------------------------------------------------------- module list

	private void layoutList(boolean listShown, boolean onRight, float screenWidth, float startY, float dt) {
		float cursor = startY;

		for (ListEntry entry : entries) {
			boolean active = listShown && entry.module.isEnabled();
			entry.anim = Anim.approach(entry.anim, active ? 1.0f : 0.0f, 14.0f, dt);
			entry.drawn = entry.anim > 0.005f;

			if (!entry.drawn) {
				entry.placed = false;
				continue;
			}

			// New rows start in place; afterwards they glide when rows above them come or go.
			entry.y = entry.placed ? Anim.approach(entry.y, cursor, 16.0f, dt) : cursor;
			entry.placed = true;

			float eased = Anim.easeOutCubic(entry.anim);
			float rowWidth = entry.textWidth + ROW_PADDING * 2.0f + ACCENT_BAR;
			float hiddenOffset = (1.0f - eased) * (rowWidth + MARGIN);
			entry.width = rowWidth;
			entry.x = onRight ? screenWidth - MARGIN - rowWidth + hiddenOffset : MARGIN - hiddenOffset;
			cursor += (ROW_HEIGHT + ROW_GAP) * eased;
		}
	}

	private void renderListShapes(int accent, boolean onRight) {
		float baseAlpha = RenderUtils.getAlpha();

		for (ListEntry entry : entries) {
			if (!entry.drawn) {
				continue;
			}

			RenderUtils.setAlpha(baseAlpha * entry.anim);

			// Rounded on the inner side, square on the screen edge where the accent bar sits.
			if (onRight) {
				RenderUtils.roundedRect(entry.x, entry.y, entry.width, ROW_HEIGHT, 3.0f, 0.0f, 0.0f, 3.0f, Theme.HUD_GLASS);
				RenderUtils.rect(entry.x + entry.width - ACCENT_BAR, entry.y, ACCENT_BAR, ROW_HEIGHT, accent);
			} else {
				RenderUtils.roundedRect(entry.x, entry.y, entry.width, ROW_HEIGHT, 0.0f, 3.0f, 3.0f, 0.0f, Theme.HUD_GLASS);
				RenderUtils.rect(entry.x, entry.y, ACCENT_BAR, ROW_HEIGHT, accent);
			}
		}

		RenderUtils.setAlpha(baseAlpha);
	}

	private void renderListText(DrawContext context, TextRenderer textRenderer) {
		float baseAlpha = RenderUtils.getAlpha();
		boolean onRight = hud.getListPosition() == Hud.LIST_TOP_RIGHT;

		for (ListEntry entry : entries) {
			if (!entry.drawn) {
				continue;
			}

			float textX = onRight ? entry.x + ROW_PADDING : entry.x + ACCENT_BAR + ROW_PADDING;
			RenderUtils.setAlpha(baseAlpha * entry.anim);
			RenderUtils.text(context, textRenderer, entry.module.getName(), textX, entry.y + 2.0f, Theme.TEXT, false);
		}

		RenderUtils.setAlpha(baseAlpha);
	}

	/** Animation state for one module in the list. Built once; mutated in place every frame. */
	private static final class ListEntry {
		final Module module;
		final int textWidth;
		float anim;
		float x;
		float y;
		float width;
		boolean drawn;
		boolean placed;

		ListEntry(Module module, int textWidth) {
			this.module = module;
			this.textWidth = textWidth;
		}
	}
}

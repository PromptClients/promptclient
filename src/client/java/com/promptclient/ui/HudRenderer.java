package com.promptclient.ui;

import com.promptclient.PromptClient;
import com.promptclient.module.Module;
import com.promptclient.module.ModuleManager;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

/**
 * Draws the top-left overlay: the "PromptClient" watermark with the enabled modules listed under it.
 */
public final class HudRenderer implements HudRenderCallback {
	private static final int X = 6;
	private static final int Y = 6;
	private static final int WATERMARK_GAP = 4;
	private static final int LINE_SPACING = 2;

	private static final int WATERMARK_COLOR = 0xFF8AB4FF;
	private static final int MODULE_COLOR = 0xFFE0E0E0;

	private final ModuleManager moduleManager;

	public HudRenderer(ModuleManager moduleManager) {
		this.moduleManager = moduleManager;
	}

	@Override
	public void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();

		// Respect F1 (hidden HUD), and stay out of the way of the F3 debug screen in the same corner.
		if (client.options.hudHidden || client.getDebugHud().shouldShowDebugHud()) {
			return;
		}

		TextRenderer textRenderer = client.textRenderer;

		context.drawTextWithShadow(textRenderer, PromptClient.NAME, X, Y, WATERMARK_COLOR);

		int y = Y + textRenderer.fontHeight + WATERMARK_GAP;
		for (Module module : moduleManager.getEnabledModules()) {
			context.drawTextWithShadow(textRenderer, module.getName(), X, y, MODULE_COLOR);
			y += textRenderer.fontHeight + LINE_SPACING;
		}
	}
}

package com.promptclient;

import com.promptclient.module.ModuleManager;
import com.promptclient.module.impl.render.Fullbright;
import com.promptclient.ui.HudRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PromptClient implements ClientModInitializer {
	public static final String MOD_ID = "promptclient";
	public static final String NAME = "PromptClient";
	public static final Logger LOGGER = LoggerFactory.getLogger(NAME);

	// Created eagerly so mixins can query it at any time without null checks.
	private static final ModuleManager MODULE_MANAGER = new ModuleManager();

	@Override
	public void onInitializeClient() {
		registerModules();

		ClientTickEvents.END_CLIENT_TICK.register(MODULE_MANAGER::onTick);
		HudRenderCallback.EVENT.register(new HudRenderer(MODULE_MANAGER));

		LOGGER.info("{} initialized with {} module(s)", NAME, MODULE_MANAGER.getModules().size());
	}

	/** Every module is registered here, one line each. */
	private static void registerModules() {
		// Render
		MODULE_MANAGER.register(new Fullbright());
	}

	public static ModuleManager getModuleManager() {
		return MODULE_MANAGER;
	}
}

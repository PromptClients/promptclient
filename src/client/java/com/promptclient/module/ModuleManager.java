package com.promptclient.module;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Central registry for all modules. Also polls module keybinds once per client tick.
 */
public final class ModuleManager {
	private final Map<Class<? extends Module>, Module> modules = new LinkedHashMap<>();
	private final List<Module> orderedModules = new ArrayList<>();
	private final List<Module> modulesView = Collections.unmodifiableList(orderedModules);

	// Key state indexed by GLFW key code. heldKeys is the previous tick, so a key toggles once per press
	// rather than every tick. Plain arrays avoid boxing key codes (most are > 127) on every tick.
	private boolean[] heldKeys = new boolean[GLFW.GLFW_KEY_LAST + 1];
	private boolean[] pressedKeys = new boolean[GLFW.GLFW_KEY_LAST + 1];

	/** Registers a module. Each module class may only be registered once. */
	public <T extends Module> T register(T module) {
		if (modules.putIfAbsent(module.getClass(), module) != null) {
			throw new IllegalStateException("Module already registered: " + module.getClass().getName());
		}

		orderedModules.add(module);
		return module;
	}

	/** Returns the registered instance of {@code type}, or throws if it isn't registered. */
	public <T extends Module> T get(Class<T> type) {
		Module module = modules.get(type);

		if (module == null) {
			throw new IllegalArgumentException("Module not registered: " + type.getName());
		}

		return type.cast(module);
	}

	/** Case-insensitive lookup by display name. */
	public Optional<Module> getByName(String name) {
		return modules.values().stream()
				.filter(module -> module.getName().equalsIgnoreCase(name))
				.findFirst();
	}

	/**
	 * Fast, null-safe enabled check. Safe to call from mixins on hot paths and before
	 * modules are registered (returns {@code false}).
	 */
	public boolean isEnabled(Class<? extends Module> type) {
		Module module = modules.get(type);
		return module != null && module.isEnabled();
	}

	/** All modules in registration order, as a read-only view. Allocation-free, so it is safe per frame. */
	public List<Module> getModules() {
		return modulesView;
	}

	/** Allocates a new list; call it once at setup, not every frame. */
	public List<Module> getModulesByCategory(Category category) {
		return modules.values().stream()
				.filter(module -> module.getCategory() == category)
				.toList();
	}

	/** Enabled modules, sorted alphabetically by name. Allocates a new list; don't call it every frame. */
	public List<Module> getEnabledModules() {
		return modules.values().stream()
				.filter(Module::isEnabled)
				.sorted(Comparator.comparing(Module::getName, String.CASE_INSENSITIVE_ORDER))
				.toList();
	}

	/** Wired to {@code ClientTickEvents.END_CLIENT_TICK} in {@code PromptClient}. */
	public void onTick(MinecraftClient client) {
		if (client.player == null || client.world == null) {
			Arrays.fill(heldKeys, false);
			return;
		}

		handleKeybinds(client);

		for (int i = 0; i < orderedModules.size(); i++) {
			Module module = orderedModules.get(i);

			if (module.isEnabled()) {
				module.onTick();
			}
		}
	}

	private void handleKeybinds(MinecraftClient client) {
		long window = client.getWindow().getHandle();

		Arrays.fill(pressedKeys, false);
		for (int i = 0; i < orderedModules.size(); i++) {
			int key = orderedModules.get(i).getKeyCode();

			if (isValidKey(key) && InputUtil.isKeyPressed(window, key)) {
				pressedKeys[key] = true;
			}
		}

		// Key state is always tracked, but toggles only fire with no screen open, so typing in
		// chat or a sign doesn't toggle modules, and a key still held when a screen closes doesn't either.
		for (int i = 0; i < orderedModules.size() && client.currentScreen == null; i++) {
			Module module = orderedModules.get(i);
			int key = module.getKeyCode();

			// Re-checking currentScreen each iteration stops once a toggle opens a screen (e.g. the ClickGUI).
			if (isValidKey(key) && pressedKeys[key] && !heldKeys[key]) {
				module.toggle();
			}
		}

		boolean[] previous = heldKeys;
		heldKeys = pressedKeys;
		pressedKeys = previous;
	}

	private static boolean isValidKey(int key) {
		// GLFW reports an "invalid enum" error for anything outside the keyboard key range.
		return key >= GLFW.GLFW_KEY_SPACE && key <= GLFW.GLFW_KEY_LAST;
	}
}

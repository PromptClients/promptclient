package com.promptclient.module;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Central registry for all modules. Also polls module keybinds once per client tick.
 */
public final class ModuleManager {
	private final Map<Class<? extends Module>, Module> modules = new LinkedHashMap<>();

	// Keys held during the previous tick, so a key toggles once per press rather than every tick.
	private Set<Integer> heldKeys = new HashSet<>();
	private Set<Integer> pressedKeys = new HashSet<>();

	/** Registers a module. Each module class may only be registered once. */
	public <T extends Module> T register(T module) {
		if (modules.putIfAbsent(module.getClass(), module) != null) {
			throw new IllegalStateException("Module already registered: " + module.getClass().getName());
		}

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

	/** All modules, in registration order. */
	public List<Module> getModules() {
		return List.copyOf(modules.values());
	}

	public List<Module> getModulesByCategory(Category category) {
		return modules.values().stream()
				.filter(module -> module.getCategory() == category)
				.toList();
	}

	/** Enabled modules, sorted alphabetically by name. */
	public List<Module> getEnabledModules() {
		return modules.values().stream()
				.filter(Module::isEnabled)
				.sorted(Comparator.comparing(Module::getName, String.CASE_INSENSITIVE_ORDER))
				.toList();
	}

	/** Wired to {@code ClientTickEvents.END_CLIENT_TICK} in {@code PromptClient}. */
	public void onTick(MinecraftClient client) {
		if (client.player == null || client.world == null) {
			heldKeys.clear();
			return;
		}

		handleKeybinds(client);

		for (Module module : modules.values()) {
			if (module.isEnabled()) {
				module.onTick();
			}
		}
	}

	private void handleKeybinds(MinecraftClient client) {
		long window = client.getWindow().getHandle();

		pressedKeys.clear();
		for (Module module : modules.values()) {
			int key = module.getKeyCode();

			if (isValidKey(key) && InputUtil.isKeyPressed(window, key)) {
				pressedKeys.add(key);
			}
		}

		// Key state is always tracked, but toggles only fire with no screen open, so typing in
		// chat or a sign doesn't toggle modules, and a key still held when a screen closes doesn't either.
		if (client.currentScreen == null) {
			for (Module module : modules.values()) {
				int key = module.getKeyCode();

				if (pressedKeys.contains(key) && !heldKeys.contains(key)) {
					module.toggle();
				}
			}
		}

		Set<Integer> previous = heldKeys;
		heldKeys = pressedKeys;
		pressedKeys = previous;
	}

	private static boolean isValidKey(int key) {
		// GLFW reports an "invalid enum" error for anything outside the keyboard key range.
		return key >= GLFW.GLFW_KEY_SPACE && key <= GLFW.GLFW_KEY_LAST;
	}
}

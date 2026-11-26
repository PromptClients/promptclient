package com.promptclient.module;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

/**
 * Base class for every PromptClient feature.
 *
 * <p>Subclasses pass their metadata to the constructor and override only the hooks they need:
 * {@link #onEnable()}, {@link #onDisable()} and {@link #onTick()}. Register new modules in
 * {@code PromptClient#registerModules()}.
 */
public abstract class Module {
	private final String name;
	private final String description;
	private final Category category;
	private int keyCode;
	private boolean enabled;

	protected Module(String name, String description, Category category) {
		this(name, description, category, GLFW.GLFW_KEY_UNKNOWN);
	}

	/**
	 * @param keyCode a GLFW keyboard key code ({@code GLFW.GLFW_KEY_*}), or
	 *                {@link GLFW#GLFW_KEY_UNKNOWN} for no keybind
	 */
	protected Module(String name, String description, Category category, int keyCode) {
		this.name = name;
		this.description = description;
		this.category = category;
		this.keyCode = keyCode;
	}

	/** Flips the enabled state, firing {@link #onEnable()} or {@link #onDisable()}. */
	public final void toggle() {
		setEnabled(!enabled);
	}

	/** Sets the enabled state. Lifecycle hooks only fire when the state actually changes. */
	public final void setEnabled(boolean enabled) {
		if (this.enabled == enabled) {
			return;
		}

		this.enabled = enabled;

		if (enabled) {
			onEnable();
		} else {
			onDisable();
		}
	}

	/** Called once when the module is switched on. */
	protected void onEnable() {
	}

	/** Called once when the module is switched off. Undo any side effects here. */
	protected void onDisable() {
	}

	/** Called at the end of every client tick while the module is enabled. */
	protected void onTick() {
	}

	/** Shorthand for {@link MinecraftClient#getInstance()}. */
	protected final MinecraftClient client() {
		return MinecraftClient.getInstance();
	}

	public final String getName() {
		return name;
	}

	public final String getDescription() {
		return description;
	}

	public final Category getCategory() {
		return category;
	}

	public final int getKeyCode() {
		return keyCode;
	}

	public final void setKeyCode(int keyCode) {
		this.keyCode = keyCode;
	}

	public final boolean isEnabled() {
		return enabled;
	}
}

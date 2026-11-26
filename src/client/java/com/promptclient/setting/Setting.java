package com.promptclient.setting;

import com.promptclient.module.Module;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * A configurable option that a {@link Module} exposes in the ClickGUI.
 *
 * <p>Declare settings as fields of the module so they are registered in its constructor:
 * <pre>{@code
 * private final BooleanSetting smooth = addSetting(new BooleanSetting("Smooth", "Interpolate movement.", true));
 * private final NumberSetting speed = addSetting(new NumberSetting("Speed", "Blocks per tick.", 1.0, 0.1, 5.0, 0.1)
 *         .visibleWhen(smooth::getValue));
 * }</pre>
 */
public abstract class Setting {
	private static final Supplier<Boolean> ALWAYS_VISIBLE = () -> Boolean.TRUE;

	private final String name;
	private final String description;
	private Module parentModule;
	private Supplier<Boolean> visibility = ALWAYS_VISIBLE;

	protected Setting(String name, String description) {
		this.name = Objects.requireNonNull(name, "name");
		this.description = Objects.requireNonNull(description, "description");
	}

	/** Links this setting to its module. Called by {@code Module#addSetting}; a setting belongs to one module only. */
	public final void attach(Module module) {
		if (parentModule != null) {
			throw new IllegalStateException("Setting '" + name + "' already belongs to " + parentModule.getName());
		}

		parentModule = Objects.requireNonNull(module, "module");
	}

	/** Subclasses expose this through a covariant {@code visibleWhen(...)} so calls can be chained. */
	protected final void setVisibility(Supplier<Boolean> visibility) {
		this.visibility = Objects.requireNonNull(visibility, "visibility");
	}

	/** Whether the ClickGUI should currently show this setting. Evaluated every frame, so keep it cheap. */
	public final boolean isVisible() {
		return visibility.get();
	}

	public final String getName() {
		return name;
	}

	public final String getDescription() {
		return description;
	}

	public final Module getParentModule() {
		return parentModule;
	}
}

package com.promptclient.setting;

import java.util.function.Supplier;

/** An on/off option, drawn as an animated toggle switch. */
public final class BooleanSetting extends Setting {
	private boolean value;

	public BooleanSetting(String name, String description, boolean defaultValue) {
		super(name, description);
		this.value = defaultValue;
	}

	public boolean getValue() {
		return value;
	}

	public void setValue(boolean value) {
		this.value = value;
	}

	public void toggle() {
		value = !value;
	}

	public BooleanSetting visibleWhen(Supplier<Boolean> visibility) {
		setVisibility(visibility);
		return this;
	}
}

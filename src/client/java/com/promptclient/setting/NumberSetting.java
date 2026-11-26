package com.promptclient.setting;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.function.Supplier;

/**
 * A number in {@code [min, max]}, snapped to {@code increment}, drawn as a slider.
 *
 * <p>The formatted value is cached in {@link #getDisplayValue()} and only rebuilt when the value
 * actually changes, so the GUI can draw it every frame without allocating.
 */
public final class NumberSetting extends Setting {
	private final double min;
	private final double max;
	private final double increment;
	private final int decimals;
	private final double roundingScale;

	private double value;
	private String suffix = "";
	private String displayValue;

	public NumberSetting(String name, String description, double defaultValue, double min, double max, double increment) {
		super(name, description);

		if (!(max > min)) {
			throw new IllegalArgumentException("max must be greater than min for setting '" + name + "'");
		}

		if (!(increment > 0)) {
			throw new IllegalArgumentException("increment must be positive for setting '" + name + "'");
		}

		this.min = min;
		this.max = max;
		this.increment = increment;
		this.decimals = Math.max(0, BigDecimal.valueOf(increment).stripTrailingZeros().scale());
		this.roundingScale = Math.pow(10, decimals);
		this.value = Double.NaN;
		setValue(defaultValue);
	}

	/** Clamps to {@code [min, max]} and snaps to the nearest increment. */
	public void setValue(double value) {
		double snapped = min + Math.round((value - min) / increment) * increment;
		snapped = Math.max(min, Math.min(max, snapped));
		// Remove floating-point noise such as 0.30000000000000004.
		snapped = Math.round(snapped * roundingScale) / roundingScale;

		if (snapped == this.value) {
			return;
		}

		this.value = snapped;
		this.displayValue = format(snapped);
	}

	/** Position of the value between min (0) and max (1). */
	public double getPercent() {
		return (value - min) / (max - min);
	}

	public void setPercent(double percent) {
		setValue(min + Math.max(0, Math.min(1, percent)) * (max - min));
	}

	/** Appends a unit to the displayed value, e.g. {@code "%"}. */
	public NumberSetting suffix(String suffix) {
		this.suffix = suffix;
		this.displayValue = format(value);
		return this;
	}

	public NumberSetting visibleWhen(Supplier<Boolean> visibility) {
		setVisibility(visibility);
		return this;
	}

	public double getValue() {
		return value;
	}

	public double getMin() {
		return min;
	}

	public double getMax() {
		return max;
	}

	public double getIncrement() {
		return increment;
	}

	/** The formatted value, e.g. {@code "8.5"} or {@code "65%"}. Same instance until the value changes. */
	public String getDisplayValue() {
		return displayValue;
	}

	private String format(double number) {
		String text = decimals == 0
				? Long.toString(Math.round(number))
				: BigDecimal.valueOf(number).setScale(decimals, RoundingMode.HALF_UP).toPlainString();
		return suffix.isEmpty() ? text : text + suffix;
	}
}

package com.promptclient.ui.render;

/** Allocation-free helpers for packed {@code 0xAARRGGBB} colors. Use these instead of {@code new Color(...)}. */
public final class Colors {
	private Colors() {
	}

	public static int alpha(int argb) {
		return argb >>> 24;
	}

	/** Replaces the alpha channel. {@code alpha} is clamped to 0-255. */
	public static int withAlpha(int argb, int alpha) {
		return (clamp255(alpha) << 24) | (argb & 0x00FFFFFF);
	}

	/** Scales the existing alpha channel by {@code factor} (0-1). */
	public static int multiplyAlpha(int argb, float factor) {
		if (factor >= 1.0f) {
			return argb;
		}

		int alpha = (int) ((argb >>> 24) * Math.max(0.0f, factor) + 0.5f);
		return (clamp255(alpha) << 24) | (argb & 0x00FFFFFF);
	}

	/** Per-channel linear interpolation, alpha included. */
	public static int lerp(int from, int to, float t) {
		if (t <= 0.0f) {
			return from;
		}

		if (t >= 1.0f) {
			return to;
		}

		int a = lerpChannel(from >>> 24, to >>> 24, t);
		int r = lerpChannel((from >> 16) & 0xFF, (to >> 16) & 0xFF, t);
		int g = lerpChannel((from >> 8) & 0xFF, (to >> 8) & 0xFF, t);
		int b = lerpChannel(from & 0xFF, to & 0xFF, t);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	private static int lerpChannel(int from, int to, float t) {
		return (int) (from + (to - from) * t + 0.5f);
	}

	private static int clamp255(int value) {
		return value < 0 ? 0 : Math.min(value, 255);
	}
}

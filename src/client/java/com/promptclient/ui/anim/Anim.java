package com.promptclient.ui.anim;

/** Frame-rate-independent animation helpers. All inputs are plain floats: no allocation. */
public final class Anim {
	private static final float SNAP_EPSILON = 0.001f;

	private Anim() {
	}

	/**
	 * Exponential approach: moves {@code current} toward {@code target} so the remaining distance shrinks
	 * by {@code 1 - e^(-speed * dt)} per frame. Identical feel at any frame rate. Snaps once close enough.
	 *
	 * @param speed higher is snappier; ~10 is relaxed, ~20 is quick
	 * @param dt    seconds since the last frame, already capped by {@link FrameClock}
	 */
	public static float approach(float current, float target, float speed, float dt) {
		float diff = target - current;

		if (Math.abs(diff) < SNAP_EPSILON) {
			return target;
		}

		return current + diff * (1.0f - (float) Math.exp(-speed * dt));
	}

	public static float easeOutCubic(float t) {
		float inverse = 1.0f - clamp01(t);
		return 1.0f - inverse * inverse * inverse;
	}

	public static float clamp01(float t) {
		return t < 0.0f ? 0.0f : Math.min(t, 1.0f);
	}

	public static float lerp(float from, float to, float t) {
		return from + (to - from) * t;
	}

	/** True while a 0-1 animation value is between its resting states. */
	public static boolean isMoving(float value) {
		return value > 0.0f && value < 1.0f;
	}
}

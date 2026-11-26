package com.promptclient.ui.anim;

import net.minecraft.util.Util;

/**
 * Measures real time between frames for animations.
 *
 * <p>Don't animate with the {@code delta}/{@code tickDelta} that Minecraft passes to render methods: that is
 * the partial tick (0-1 within a 50 ms tick), not frame time. Keep one clock per independently drawn element.
 */
public final class FrameClock {
	/** Caps a single step so a hitch (GC, world load, alt-tab) doesn't make animations jump. */
	public static final float MAX_DELTA = 0.05f;

	private long lastNanos = -1L;

	/** Seconds since the previous call, capped at {@link #MAX_DELTA}. Returns 0 on the first call. */
	public float tick() {
		long now = Util.getMeasuringTimeNano();
		float delta = lastNanos < 0L ? 0.0f : (now - lastNanos) / 1.0E9f;
		lastNanos = now;
		return Math.min(delta, MAX_DELTA);
	}

	/** Forget the previous frame, e.g. when a screen reopens after being hidden. */
	public void reset() {
		lastNanos = -1L;
	}
}

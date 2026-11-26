package com.promptclient.ui.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.Window;
import org.joml.Matrix4f;

import java.util.function.Supplier;

/**
 * Batched 2D primitives for the ClickGUI, HUD and chat.
 *
 * <p>Every shape between {@link #begin} and {@link #end} goes into one {@code TRIANGLES}/{@code POSITION_COLOR}
 * buffer and is drawn with a single draw call, instead of one draw per {@code DrawContext#fill} (in 1.21.1 every
 * {@code fill} and {@code drawText} flushes immediately). Text is queued with {@link #text} into DrawContext's
 * buffers and drawn by one {@link #flush}.
 *
 * <p>Draw order is call order: {@link #end()} first flushes anything DrawContext already queued, then draws the
 * shapes, so the usual pattern for one layer is: {@code begin -> shapes -> end -> text -> flush}.
 *
 * <p>Hot-path rules: no allocation per shape. Corner geometry comes from precomputed tables, colors are packed
 * ints, and vertices are streamed straight into the buffer. Render thread only.
 */
public final class RenderUtils {
	/** Segments per quarter circle. 8 looks smooth up to ~16 px radius at GUI scale 4. */
	private static final int SEGMENTS = 8;
	private static final int POINTS = 4 * (SEGMENTS + 1);
	private static final float[] COS = new float[SEGMENTS + 1];
	private static final float[] SIN = new float[SEGMENTS + 1];

	private static final Supplier<ShaderProgram> POSITION_COLOR_PROGRAM = GameRenderer::getPositionColorProgram;

	static {
		for (int i = 0; i <= SEGMENTS; i++) {
			double angle = Math.PI / 2.0 * i / SEGMENTS;
			COS[i] = (float) Math.cos(angle);
			SIN[i] = (float) Math.sin(angle);
		}
	}

	// Our own allocator, so a batch can never interleave with vanilla code that uses Tessellator.getInstance().
	private static BufferAllocator allocator;
	private static BufferBuilder buffer;
	private static DrawContext context;
	private static float alpha = 1.0f;

	// Output of outlinePoint(); avoids allocating a vector per point.
	private static float pointX;
	private static float pointY;

	private RenderUtils() {
	}

	// ---------------------------------------------------------------- batching

	/** Opens a shape batch. Every shape until {@link #end()} is drawn in one call. */
	public static void begin(DrawContext drawContext) {
		if (buffer != null) {
			throw new IllegalStateException("RenderUtils.begin() called twice without end()");
		}

		if (allocator == null) {
			allocator = new BufferAllocator(1 << 16);
		}

		context = drawContext;
		buffer = new BufferBuilder(allocator, VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
	}

	/** Draws the batch. Anything DrawContext had queued is flushed first, so it stays underneath. */
	public static void end() {
		BufferBuilder builder = buffer;

		if (builder == null) {
			throw new IllegalStateException("RenderUtils.end() called without begin()");
		}

		DrawContext drawContext = context;
		buffer = null;
		context = null;

		drawContext.draw();
		BuiltBuffer built = builder.endNullable();

		if (built == null) {
			return;
		}

		// Same state DrawContext#draw uses for GUI layers, plus no culling: our winding isn't guaranteed.
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableCull();
		RenderSystem.disableDepthTest();
		RenderSystem.setShader(POSITION_COLOR_PROGRAM);
		BufferRenderer.drawWithGlobalProgram(built);
		RenderSystem.enableDepthTest();
		RenderSystem.enableCull();
		RenderSystem.disableBlend();
	}

	/** Multiplies the alpha of everything drawn afterwards (shapes and text). Used for fades. */
	public static void setAlpha(float value) {
		alpha = Math.max(0.0f, Math.min(1.0f, value));
	}

	public static float getAlpha() {
		return alpha;
	}

	// ---------------------------------------------------------------- shapes

	public static void rect(float x, float y, float width, float height, int color) {
		if (width <= 0 || height <= 0) {
			return;
		}

		int c = applyAlpha(color);
		quad(matrix(), x, y, x + width, y + height, c, c, c, c);
	}

	/** Vertical gradient, {@code top} to {@code bottom}. */
	public static void gradientV(float x, float y, float width, float height, int top, int bottom) {
		if (width <= 0 || height <= 0) {
			return;
		}

		int t = applyAlpha(top);
		int b = applyAlpha(bottom);
		quad(matrix(), x, y, x + width, y + height, t, t, b, b);
	}

	public static void roundedRect(float x, float y, float width, float height, float radius, int color) {
		roundedRect(x, y, width, height, radius, radius, radius, radius, color);
	}

	/** Rounded rectangle with one radius per corner (top-left, top-right, bottom-right, bottom-left). */
	public static void roundedRect(float x, float y, float width, float height,
			float radiusTopLeft, float radiusTopRight, float radiusBottomRight, float radiusBottomLeft, int color) {
		roundedFan(x, y, width, height, radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft, color, color);
	}

	/** Rounded rectangle with a vertical gradient. Interpolation stays exactly linear in y across the fan. */
	public static void roundedGradientV(float x, float y, float width, float height, float radius, int top, int bottom) {
		roundedFan(x, y, width, height, radius, radius, radius, radius, top, bottom);
	}

	/** A border of {@code thickness} drawn just inside the bounds, without overdrawing the interior. */
	public static void roundedOutline(float x, float y, float width, float height, float radius, float thickness, int color) {
		if (width <= 0 || height <= 0 || thickness <= 0) {
			return;
		}

		float outerRadius = clampRadius(radius, width, height);
		float innerRadius = Math.max(0.0f, outerRadius - thickness);
		int c = applyAlpha(color);
		ring(x, y, width, height, outerRadius, 0.0f,
				x + thickness, y + thickness, width - 2 * thickness, height - 2 * thickness, innerRadius, c, c);
	}

	/**
	 * Soft drop shadow: a ring from the shape's edge ({@code color}) out to {@code spread} pixels (transparent).
	 * One gradient ring instead of stacked translucent copies, and nothing is drawn under the shape itself.
	 */
	public static void shadow(float x, float y, float width, float height, float radius, float spread, int color) {
		if (width <= 0 || height <= 0 || spread <= 0) {
			return;
		}

		float r = clampRadius(radius, width, height);
		int inner = applyAlpha(color);
		int outer = inner & 0x00FFFFFF;
		ring(x, y, width, height, r, spread, x, y, width, height, r, outer, inner);
	}

	public static void circle(float centerX, float centerY, float radius, int color) {
		roundedRect(centerX - radius, centerY - radius, radius * 2, radius * 2, radius, color);
	}

	/** A straight line of {@code thickness}, at any angle. */
	public static void line(float x1, float y1, float x2, float y2, float thickness, int color) {
		float dx = x2 - x1;
		float dy = y2 - y1;
		float length = (float) Math.sqrt(dx * dx + dy * dy);

		if (length < 1.0E-4f) {
			return;
		}

		float nx = -dy / length * thickness * 0.5f;
		float ny = dx / length * thickness * 0.5f;
		int c = applyAlpha(color);
		Matrix4f m = matrix();
		vertex(m, x1 + nx, y1 + ny, c);
		vertex(m, x1 - nx, y1 - ny, c);
		vertex(m, x2 - nx, y2 - ny, c);
		vertex(m, x1 + nx, y1 + ny, c);
		vertex(m, x2 - nx, y2 - ny, c);
		vertex(m, x2 + nx, y2 + ny, c);
	}

	// ---------------------------------------------------------------- text

	/**
	 * Queues text into DrawContext's buffers; nothing is drawn until {@link #flush}. Must be called outside
	 * {@code begin/end}, otherwise the text would be flushed underneath the shapes.
	 */
	public static void text(DrawContext drawContext, TextRenderer textRenderer, String text, float x, float y, int color, boolean shadow) {
		if (buffer != null) {
			throw new IllegalStateException("Queue text after RenderUtils.end(), not inside a shape batch");
		}

		int c = applyAlpha(color);

		// TextRenderer turns alpha < 4 into fully opaque, so skip nearly invisible text instead.
		if ((c >>> 24) < 4) {
			return;
		}

		textRenderer.draw(text, x, y, c, shadow, drawContext.getMatrices().peek().getPositionMatrix(),
				drawContext.getVertexConsumers(), TextRenderer.TextLayerType.NORMAL, 0, LightmapTextureManager.MAX_LIGHT_COORDINATE);
	}

	/** Draws all queued text in as few draw calls as possible (one per font texture). */
	public static void flush(DrawContext drawContext) {
		drawContext.draw();
	}

	// ---------------------------------------------------------------- clipping

	/**
	 * Clips subsequent drawing to a rectangle in the current matrix space, so scaled or translated content
	 * clips correctly. Queued text is flushed first so it is drawn under the previous clip state.
	 */
	public static void enableScissor(DrawContext drawContext, float x, float y, float width, float height) {
		if (buffer != null) {
			throw new IllegalStateException("Change the scissor outside a shape batch");
		}

		drawContext.draw();

		Matrix4f m = drawContext.getMatrices().peek().getPositionMatrix();
		float x1 = m.m00() * x + m.m10() * y + m.m30();
		float y1 = m.m01() * x + m.m11() * y + m.m31();
		float x2 = m.m00() * (x + width) + m.m10() * (y + height) + m.m30();
		float y2 = m.m01() * (x + width) + m.m11() * (y + height) + m.m31();

		Window window = MinecraftClient.getInstance().getWindow();
		double scale = window.getScaleFactor();
		int left = (int) Math.floor(Math.min(x1, x2) * scale);
		int right = (int) Math.ceil(Math.max(x1, x2) * scale);
		int top = (int) Math.floor(Math.min(y1, y2) * scale);
		int bottom = (int) Math.ceil(Math.max(y1, y2) * scale);

		// OpenGL scissor origin is the bottom-left corner of the framebuffer.
		RenderSystem.enableScissor(left, window.getFramebufferHeight() - bottom, Math.max(0, right - left), Math.max(0, bottom - top));
	}

	public static void disableScissor(DrawContext drawContext) {
		if (buffer != null) {
			throw new IllegalStateException("Change the scissor outside a shape batch");
		}

		drawContext.draw();
		RenderSystem.disableScissor();
	}

	// ---------------------------------------------------------------- internals

	/** Triangle fan around the center. Vertex colors follow y, so a two-color gradient stays exact. */
	private static void roundedFan(float x, float y, float width, float height,
			float rTL, float rTR, float rBR, float rBL, int topColor, int bottomColor) {
		if (width <= 0 || height <= 0) {
			return;
		}

		rTL = clampRadius(rTL, width, height);
		rTR = clampRadius(rTR, width, height);
		rBR = clampRadius(rBR, width, height);
		rBL = clampRadius(rBL, width, height);

		int top = applyAlpha(topColor);
		int bottom = applyAlpha(bottomColor);
		boolean gradient = top != bottom;
		Matrix4f m = matrix();
		float centerX = x + width * 0.5f;
		float centerY = y + height * 0.5f;
		int centerColor = gradient ? Colors.lerp(top, bottom, 0.5f) : top;

		outlinePoint(POINTS - 1, x, y, width, height, rTL, rTR, rBR, rBL, 0.0f);
		float previousX = pointX;
		float previousY = pointY;
		int previousColor = gradient ? Colors.lerp(top, bottom, (previousY - y) / height) : top;

		for (int i = 0; i < POINTS; i++) {
			outlinePoint(i, x, y, width, height, rTL, rTR, rBR, rBL, 0.0f);

			// Square corners repeat the same point; skip the zero-area triangles.
			if (pointX == previousX && pointY == previousY) {
				continue;
			}

			int pointColor = gradient ? Colors.lerp(top, bottom, (pointY - y) / height) : top;
			vertex(m, centerX, centerY, centerColor);
			vertex(m, previousX, previousY, previousColor);
			vertex(m, pointX, pointY, pointColor);
			previousX = pointX;
			previousY = pointY;
			previousColor = pointColor;
		}
	}

	/**
	 * Quad strip between an outer outline (rect A grown by {@code outerExpand}) and an inner outline (rect B).
	 * Both outlines use the same angles per point, so the pairs line up exactly.
	 */
	private static void ring(float ox, float oy, float ow, float oh, float outerRadius, float outerExpand,
			float ix, float iy, float iw, float ih, float innerRadius, int outerColor, int innerColor) {
		Matrix4f m = matrix();

		outlinePoint(POINTS - 1, ox, oy, ow, oh, outerRadius, outerRadius, outerRadius, outerRadius, outerExpand);
		float previousOuterX = pointX;
		float previousOuterY = pointY;
		outlinePoint(POINTS - 1, ix, iy, iw, ih, innerRadius, innerRadius, innerRadius, innerRadius, 0.0f);
		float previousInnerX = pointX;
		float previousInnerY = pointY;

		for (int i = 0; i < POINTS; i++) {
			outlinePoint(i, ox, oy, ow, oh, outerRadius, outerRadius, outerRadius, outerRadius, outerExpand);
			float outerX = pointX;
			float outerY = pointY;
			outlinePoint(i, ix, iy, iw, ih, innerRadius, innerRadius, innerRadius, innerRadius, 0.0f);
			float innerX = pointX;
			float innerY = pointY;

			vertex(m, previousOuterX, previousOuterY, outerColor);
			vertex(m, outerX, outerY, outerColor);
			vertex(m, innerX, innerY, innerColor);
			vertex(m, previousOuterX, previousOuterY, outerColor);
			vertex(m, innerX, innerY, innerColor);
			vertex(m, previousInnerX, previousInnerY, innerColor);

			previousOuterX = outerX;
			previousOuterY = outerY;
			previousInnerX = innerX;
			previousInnerY = innerY;
		}
	}

	/**
	 * Writes the {@code index}-th outline point (clockwise from the left end of the top-left arc) into
	 * {@link #pointX}/{@link #pointY}. {@code expand} pushes the point outward along the corner normal.
	 */
	private static void outlinePoint(int index, float x, float y, float width, float height,
			float rTL, float rTR, float rBR, float rBL, float expand) {
		int corner = index / (SEGMENTS + 1);
		int step = index - corner * (SEGMENTS + 1);
		float radius;
		float centerX;
		float centerY;
		float unitX;
		float unitY;

		switch (corner) {
			case 0 -> { // top-left: 180 -> 270 degrees
				radius = rTL;
				centerX = x + radius;
				centerY = y + radius;
				unitX = -COS[step];
				unitY = -SIN[step];
			}
			case 1 -> { // top-right: 270 -> 360
				radius = rTR;
				centerX = x + width - radius;
				centerY = y + radius;
				unitX = SIN[step];
				unitY = -COS[step];
			}
			case 2 -> { // bottom-right: 0 -> 90
				radius = rBR;
				centerX = x + width - radius;
				centerY = y + height - radius;
				unitX = COS[step];
				unitY = SIN[step];
			}
			default -> { // bottom-left: 90 -> 180
				radius = rBL;
				centerX = x + radius;
				centerY = y + height - radius;
				unitX = -SIN[step];
				unitY = COS[step];
			}
		}

		float distance = radius + expand;
		pointX = centerX + unitX * distance;
		pointY = centerY + unitY * distance;
	}

	private static void quad(Matrix4f m, float x1, float y1, float x2, float y2,
			int topLeft, int topRight, int bottomRight, int bottomLeft) {
		vertex(m, x1, y1, topLeft);
		vertex(m, x1, y2, bottomLeft);
		vertex(m, x2, y2, bottomRight);
		vertex(m, x1, y1, topLeft);
		vertex(m, x2, y2, bottomRight);
		vertex(m, x2, y1, topRight);
	}

	private static void vertex(Matrix4f m, float x, float y, int color) {
		buffer.vertex(m, x, y, 0.0f).color(color);
	}

	private static Matrix4f matrix() {
		if (buffer == null) {
			throw new IllegalStateException("Draw shapes between RenderUtils.begin() and end()");
		}

		return context.getMatrices().peek().getPositionMatrix();
	}

	private static int applyAlpha(int color) {
		return alpha >= 1.0f ? color : Colors.multiplyAlpha(color, alpha);
	}

	private static float clampRadius(float radius, float width, float height) {
		return Math.max(0.0f, Math.min(radius, Math.min(width, height) * 0.5f));
	}
}

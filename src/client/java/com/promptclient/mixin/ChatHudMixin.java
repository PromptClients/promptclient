package com.promptclient.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import com.promptclient.ui.ChatRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Hooks for the Chat module. Logic lives in {@link ChatRenderer}; with the module off every hook is a no-op,
 * so chat is pure vanilla. Targets were checked against 1.21.1 bytecode for {@code ChatHud#render}:
 * {@code MatrixStack.translate} ordinal 0 is the {@code translate(4, 0, 0)} before the line loop,
 * {@code DrawContext.fill(IIIII)V} ordinal 0 is the per-line background (1 = indicator, 2 = queue line),
 * and the {@code OrderedText} {@code drawTextWithShadow} is the line text.
 */
@Mixin(ChatHud.class)
public abstract class ChatHudMixin {
	@Shadow
	@Final
	private List<ChatHudLine.Visible> visibleMessages;

	@Shadow
	private int scrolledLines;

	@Shadow
	public abstract int getVisibleLineCount();

	@Shadow
	public abstract double getChatScale();

	@Shadow
	public abstract int getWidth();

	@Shadow
	private int getLineHeight() {
		throw new AssertionError();
	}

	/** Draw our card right after vanilla sets up the chat matrix, before any line is drawn. */
	@Inject(
			method = "render",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/util/math/MatrixStack;translate(FFF)V",
					ordinal = 0,
					shift = At.Shift.AFTER
			)
	)
	private void promptclient$renderCard(DrawContext context, int currentTick, int mouseX, int mouseY, boolean focused, CallbackInfo ci) {
		if (!ChatRenderer.isActive()) {
			return;
		}

		float scale = (float) getChatScale();
		int chatWidth = MathHelper.ceil(getWidth() / scale);
		int chatBottom = MathHelper.floor((context.getScaledWindowHeight() - 40) / scale);
		ChatRenderer.renderCard(context, visibleMessages, scrolledLines, getVisibleLineCount(), getLineHeight(),
				chatWidth, chatBottom, currentTick, focused);
	}

	/** Skip vanilla's flat per-line background while the card is drawn instead. Zero-allocation wrapper. */
	@WrapWithCondition(
			method = "render",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/gui/DrawContext;fill(IIIII)V",
					ordinal = 0
			)
	)
	private boolean promptclient$skipLineBackground(DrawContext context, int x1, int y1, int x2, int y2, int color) {
		return !ChatRenderer.isActive();
	}

	/** Fade in the text of the newest message. */
	@ModifyArg(
			method = "render",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/gui/DrawContext;drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/OrderedText;III)I"
			),
			index = 4
	)
	private int promptclient$fadeInText(int color, @Local ChatHudLine.Visible line) {
		return ChatRenderer.fadeTextColor(color, line.addedTime());
	}

	@Inject(
			method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
			at = @At("TAIL")
	)
	private void promptclient$onMessageAdded(Text message, MessageSignatureData signature, MessageIndicator indicator, CallbackInfo ci) {
		if (!visibleMessages.isEmpty()) {
			ChatRenderer.onMessageAdded(visibleMessages.get(0).addedTime(), getLineHeight(), scrolledLines > 0);
		}
	}
}

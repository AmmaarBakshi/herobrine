package io.github.ammaarbakshi.herobrine.client.voice;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import io.github.ammaarbakshi.herobrine.HerobrineMod;

/**
 * His words, low in the middle of the screen above the action bar: his name in dark red, the
 * line in pale grey, fading in and out. Shown when the recording is missing, or with subtitles on.
 */
public final class VoiceCaption {
	private static final int MAX_WIDTH = 280;
	private static final int FADE_IN_MILLIS = 250;
	private static final int FADE_OUT_MILLIS = 700;
	private static final int NAME_COLOR = 0xAA0000;
	private static final int TEXT_COLOR = 0xD8D8D8;
	private static final int BACKDROP = 0x000000;
	private static final Component NAME = Component.translatable("entity.herobrine.herobrine");

	private static @Nullable Component text;
	private static List<FormattedCharSequence> lines = List.of();
	private static long shownAt;
	private static long durationMillis;

	private VoiceCaption() {
	}

	static void init() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.OVERLAY_MESSAGE, HerobrineMod.id("voice_caption"), VoiceCaption::extract);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> text = null);
	}

	static void show(Component caption, int durationTicks) {
		text = caption;
		lines = List.of();
		shownAt = Util.getMillis();
		durationMillis = durationTicks * 50L;
	}

	private static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Component current = text;
		if (current == null) {
			return;
		}
		long age = Util.getMillis() - shownAt;
		if (age > durationMillis) {
			text = null;
			return;
		}
		float alpha = Math.min(Mth.clamp(age / (float) FADE_IN_MILLIS, 0.0F, 1.0F),
				Mth.clamp((durationMillis - age) / (float) FADE_OUT_MILLIS, 0.0F, 1.0F));
		if (alpha <= 0.02F) {
			return;
		}
		Font font = Minecraft.getInstance().font;
		if (lines.isEmpty()) {
			lines = font.split(current, MAX_WIDTH);
		}
		int width = font.width(NAME);
		for (FormattedCharSequence line : lines) {
			width = Math.max(width, font.width(line));
		}
		int lineStep = font.lineHeight + 2;
		int height = lineStep * (lines.size() + 1);
		int centerX = graphics.guiWidth() / 2;
		int top = graphics.guiHeight() - 96 - height;

		graphics.fill(centerX - width / 2 - 6, top - 4, centerX + width / 2 + 6, top + height + 2, ARGB.color(alpha * 0.55F, BACKDROP));
		graphics.centeredText(font, NAME, centerX, top, ARGB.color(alpha, NAME_COLOR));
		int y = top + lineStep;
		for (FormattedCharSequence line : lines) {
			graphics.centeredText(font, line, centerX, y, ARGB.color(alpha, TEXT_COLOR));
			y += lineStep;
		}
	}
}

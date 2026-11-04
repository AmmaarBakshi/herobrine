package io.github.ammaarbakshi.herobrine.client.hud;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import io.github.ammaarbakshi.herobrine.HerobrineMod;
import io.github.ammaarbakshi.herobrine.mirror.ProfileView;
import io.github.ammaarbakshi.herobrine.net.DebugProfilePayload;

/**
 * Live trait values in the top-left corner while the server's {@code debugOverlay} is on.
 * The server decides: it only sends updates when enabled, and the HUD hides itself once
 * updates stop arriving.
 */
public final class DebugOverlay {
	private static final long STALE_MILLIS = 15_000;
	private static final int KNOWN = 0xFFFFFFFF;
	private static final int UNKNOWN = 0xFF9A9A9A;
	private static final int HEADER = 0xFFFF5555;
	private static final int BACKDROP = 0x90000000;

	private static @Nullable ProfileView view;
	private static long receivedAt;

	private DebugOverlay() {
	}

	/** The last update from the server, or null if none since joining. */
	public static @Nullable ProfileView latest() {
		return view;
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(DebugProfilePayload.TYPE, (payload, context) -> {
			view = payload.view();
			receivedAt = Util.getMillis();
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> view = null);
		HudElementRegistry.attachElementAfter(VanillaHudElements.SUBTITLES, HerobrineMod.id("debug_profile"), DebugOverlay::extract);
	}

	private static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		ProfileView current = view;
		Minecraft client = Minecraft.getInstance();
		if (current == null || Util.getMillis() - receivedAt > STALE_MILLIS || client.debugEntries.isOverlayVisible()) {
			return;
		}
		Font font = client.font;
		int x = 4;
		int y = 4;
		int lines = current.traits().size() + 3;
		graphics.fill(x - 2, y - 2, x + 170, y + lines * (font.lineHeight + 1) + 1, BACKDROP);

		Component header = Component.translatable(current.inCombat() ? "herobrine.debug.header.combat" : "herobrine.debug.header");
		graphics.text(font, header, x, y, HEADER, true);
		y += font.lineHeight + 1;
		graphics.text(font, String.format(Locale.ROOT, "confidence %.0f%%", current.confidence() * 100), x, y, KNOWN, true);
		y += font.lineHeight + 1;
		graphics.text(font, "weapon " + name(current.topWeapon()) + "  heal " + name(current.topHealItem()), x, y,
				current.weaponKnown() ? KNOWN : UNKNOWN, true);
		y += font.lineHeight + 1;
		for (ProfileView.Entry entry : current.traits()) {
			String line = String.format(Locale.ROOT, "%-20s %7.2f  (%d)", entry.key(), entry.value(), entry.samples());
			graphics.text(font, line, x, y, entry.known() ? KNOWN : UNKNOWN, true);
			y += font.lineHeight + 1;
		}
	}

	private static String name(Enum<?> value) {
		return value.name().toLowerCase(Locale.ROOT);
	}
}

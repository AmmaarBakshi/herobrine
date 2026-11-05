package io.github.ammaarbakshi.herobrine.client.voice;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import io.github.ammaarbakshi.herobrine.HerobrineMod;
import io.github.ammaarbakshi.herobrine.net.VoiceLinePayload;

/**
 * Plays his lines. If the recording exists it is played and, with subtitles on, captioned; if it
 * is missing (not recorded yet, or a resource pack removed it) the words are shown instead, so a
 * line is never silently lost. One line at a time: a new one cuts off the old.
 */
public final class VoicePlayer {
	private static @Nullable VoiceSoundInstance playing;
	private static @Nullable String lastLineId;

	private VoicePlayer() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(VoiceLinePayload.TYPE, (payload, context) -> play(context.client(), payload));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> stop(client));
		VoiceCaption.init();
	}

	static void play(Minecraft client, VoiceLinePayload payload) {
		stop(client);
		lastLineId = payload.id();
		Identifier audio = Identifier.tryParse(payload.audio());
		boolean recorded = audio != null && hasRecording(client, audio);
		if (recorded) {
			playing = new VoiceSoundInstance(audio);
			client.getSoundManager().play(playing);
		} else {
			HerobrineMod.LOGGER.debug("No recording for voice line {} ({}), showing its caption", payload.id(), payload.audio());
		}
		if (!recorded || client.options.showSubtitles().get()) {
			Component text = Component.translatableWithFallback("herobrine.voice." + payload.id(), payload.text());
			VoiceCaption.show(text, payload.durationTicks());
		}
	}

	/** True if {@code assets/<ns>/sounds/<path>.ogg} exists in any loaded resource pack. */
	public static boolean hasRecording(Minecraft client, Identifier audio) {
		return client.getResourceManager().getResource(Sound.SOUND_LISTER.idToFile(audio)).isPresent();
	}

	private static void stop(Minecraft client) {
		if (playing != null) {
			client.getSoundManager().stop(playing);
			playing = null;
		}
	}

	/** The id of the last line received; for game tests. */
	public static @Nullable String lastLineId() {
		return lastLineId;
	}
}

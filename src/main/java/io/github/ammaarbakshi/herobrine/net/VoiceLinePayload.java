package io.github.ammaarbakshi.herobrine.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import io.github.ammaarbakshi.herobrine.HerobrineMod;

/**
 * Server to client: Herobrine says one line to this player only.
 *
 * @param id            line id; the caption uses lang key {@code herobrine.voice.<id>} if present
 * @param audio         recording to play, {@code namespace:path} inside {@code sounds/}, no extension
 * @param text          the English words, shown if the lang key is missing
 * @param durationTicks how long to keep the caption up
 */
public record VoiceLinePayload(String id, String audio, String text, int durationTicks) implements CustomPacketPayload {
	public static final Type<VoiceLinePayload> TYPE = new Type<>(HerobrineMod.id("voice_line"));
	public static final StreamCodec<FriendlyByteBuf, VoiceLinePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(128), VoiceLinePayload::id,
			ByteBufCodecs.stringUtf8(256), VoiceLinePayload::audio,
			ByteBufCodecs.stringUtf8(1024), VoiceLinePayload::text,
			ByteBufCodecs.VAR_INT, VoiceLinePayload::durationTicks,
			VoiceLinePayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

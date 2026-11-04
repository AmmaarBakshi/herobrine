package io.github.ammaarbakshi.herobrine.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import io.github.ammaarbakshi.herobrine.HerobrineMod;

/**
 * Server to client: a client-only effect of the confrontation. {@link Cue#SILENCE} stops the
 * music and ambient sounds as he arrives; the server can't do that on its own.
 */
public record ConfrontationCuePayload(Cue cue) implements CustomPacketPayload {
	public enum Cue {
		SILENCE
	}

	public static final Type<ConfrontationCuePayload> TYPE = new Type<>(HerobrineMod.id("confrontation_cue"));
	public static final StreamCodec<FriendlyByteBuf, ConfrontationCuePayload> CODEC = ByteBufCodecs.VAR_INT
			.<FriendlyByteBuf>cast()
			.map(i -> new ConfrontationCuePayload(Cue.values()[Math.floorMod(i, Cue.values().length)]), p -> p.cue().ordinal());

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

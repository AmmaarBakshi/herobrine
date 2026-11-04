package io.github.ammaarbakshi.herobrine.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import io.github.ammaarbakshi.herobrine.HerobrineMod;
import io.github.ammaarbakshi.herobrine.mirror.ProfileView;

/** Server to client: the player's own profile, for the Mirror Shard's "What he learned" screen. */
public record MirrorProfilePayload(ProfileView view, int wins, int losses) implements CustomPacketPayload {
	public static final Type<MirrorProfilePayload> TYPE = new Type<>(HerobrineMod.id("mirror_profile"));
	public static final StreamCodec<FriendlyByteBuf, MirrorProfilePayload> CODEC = StreamCodec.composite(
			ProfileViewCodec.CODEC, MirrorProfilePayload::view,
			ByteBufCodecs.VAR_INT, MirrorProfilePayload::wins,
			ByteBufCodecs.VAR_INT, MirrorProfilePayload::losses,
			MirrorProfilePayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

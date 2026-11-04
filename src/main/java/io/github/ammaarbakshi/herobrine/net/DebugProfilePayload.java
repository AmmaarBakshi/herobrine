package io.github.ammaarbakshi.herobrine.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import io.github.ammaarbakshi.herobrine.HerobrineMod;
import io.github.ammaarbakshi.herobrine.mirror.ProfileView;

/** Server to client: live profile values for the debug HUD (only sent when {@code debugOverlay} is on). */
public record DebugProfilePayload(ProfileView view) implements CustomPacketPayload {
	public static final Type<DebugProfilePayload> TYPE = new Type<>(HerobrineMod.id("debug_profile"));
	public static final StreamCodec<FriendlyByteBuf, DebugProfilePayload> CODEC =
			ProfileViewCodec.CODEC.map(DebugProfilePayload::new, DebugProfilePayload::view);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

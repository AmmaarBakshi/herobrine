package io.github.ammaarbakshi.herobrine.net;

import net.minecraft.server.level.ServerPlayer;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import io.github.ammaarbakshi.herobrine.config.HerobrineConfig;
import io.github.ammaarbakshi.herobrine.encounter.EncounterHistory;
import io.github.ammaarbakshi.herobrine.mirror.ProfileView;
import io.github.ammaarbakshi.herobrine.registry.ModAttachments;

/** Registers every payload type. Called from common init so both sides know the ids. */
public final class ModNetworking {
	private ModNetworking() {
	}

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(DebugProfilePayload.TYPE, DebugProfilePayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(MirrorProfilePayload.TYPE, MirrorProfilePayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ConfrontationCuePayload.TYPE, ConfrontationCuePayload.CODEC);
	}

	/** Sends {@code player} their own profile for the Mirror Shard screen. Skipped for vanilla clients. */
	public static void sendMirrorProfile(ServerPlayer player) {
		if (!ServerPlayNetworking.canSend(player, MirrorProfilePayload.TYPE)) {
			return;
		}
		HerobrineConfig config = HerobrineConfig.get();
		EncounterHistory history = ModAttachments.encounters(player);
		ProfileView view = ProfileView.of(ModAttachments.profile(player), config.minTraitSamples, history.total(), false);
		ServerPlayNetworking.send(player, new MirrorProfilePayload(view, history.wins(), history.losses()));
	}

	public static void sendCue(ServerPlayer player, ConfrontationCuePayload.Cue cue) {
		if (ServerPlayNetworking.canSend(player, ConfrontationCuePayload.TYPE)) {
			ServerPlayNetworking.send(player, new ConfrontationCuePayload(cue));
		}
	}
}

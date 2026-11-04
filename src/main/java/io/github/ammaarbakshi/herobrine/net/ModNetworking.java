package io.github.ammaarbakshi.herobrine.net;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/** Registers every payload type. Called from common init so both sides know the ids. */
public final class ModNetworking {
	private ModNetworking() {
	}

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(DebugProfilePayload.TYPE, DebugProfilePayload.CODEC);
	}
}

package io.github.ammaarbakshi.herobrine.client;

import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.sounds.SoundSource;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import io.github.ammaarbakshi.herobrine.client.hud.DebugOverlay;
import io.github.ammaarbakshi.herobrine.client.render.HerobrineRenderer;
import io.github.ammaarbakshi.herobrine.client.screen.MirrorProfileScreen;
import io.github.ammaarbakshi.herobrine.net.ConfrontationCuePayload;
import io.github.ammaarbakshi.herobrine.net.MirrorProfilePayload;
import io.github.ammaarbakshi.herobrine.registry.ModEntities;

/** Client-only entrypoint: renderers, screens and HUD are registered from here. */
public final class HerobrineClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRenderers.register(ModEntities.HEROBRINE, HerobrineRenderer::new);
		DebugOverlay.init();
		ClientPlayNetworking.registerGlobalReceiver(MirrorProfilePayload.TYPE,
				(payload, context) -> context.client().gui.setScreen(new MirrorProfileScreen(payload)));
		ClientPlayNetworking.registerGlobalReceiver(ConfrontationCuePayload.TYPE, (payload, context) -> {
			if (payload.cue() == ConfrontationCuePayload.Cue.SILENCE) {
				// He arrives in silence: no music, no ambience.
				context.client().getMusicManager().stopPlaying();
				context.client().getSoundManager().stop(null, SoundSource.MUSIC);
				context.client().getSoundManager().stop(null, SoundSource.AMBIENT);
			}
		});
	}
}

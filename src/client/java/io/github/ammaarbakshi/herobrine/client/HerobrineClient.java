package io.github.ammaarbakshi.herobrine.client;

import net.minecraft.client.renderer.entity.EntityRenderers;

import net.fabricmc.api.ClientModInitializer;

import io.github.ammaarbakshi.herobrine.client.render.HerobrineRenderer;
import io.github.ammaarbakshi.herobrine.registry.ModEntities;

/** Client-only entrypoint: renderers, screens and HUD are registered from here. */
public final class HerobrineClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRenderers.register(ModEntities.HEROBRINE, HerobrineRenderer::new);
	}
}

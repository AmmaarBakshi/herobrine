package io.github.ammaarbakshi.herobrine.client.render;

import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

/**
 * Render state for Herobrine. Deliberately not an {@code AvatarRenderState}: the entity render
 * dispatcher routes every avatar state to the vanilla player renderer, which would skip our
 * texture and eye layer.
 */
public final class HerobrineRenderState extends HumanoidRenderState {
}

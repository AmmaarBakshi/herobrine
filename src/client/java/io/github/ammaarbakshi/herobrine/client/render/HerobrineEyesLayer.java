package io.github.ammaarbakshi.herobrine.client.render;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;

import io.github.ammaarbakshi.herobrine.HerobrineMod;

/** Full-bright eyes (same render type as enderman eyes): visible even in total darkness. */
public final class HerobrineEyesLayer extends EyesLayer<HerobrineRenderState, HumanoidModel<HerobrineRenderState>> {
	private static final RenderType EYES = RenderTypes.eyes(HerobrineMod.id("textures/entity/herobrine/herobrine_eyes.png"));

	public HerobrineEyesLayer(RenderLayerParent<HerobrineRenderState, HumanoidModel<HerobrineRenderState>> renderer) {
		super(renderer);
	}

	@Override
	public RenderType renderType() {
		return EYES;
	}
}

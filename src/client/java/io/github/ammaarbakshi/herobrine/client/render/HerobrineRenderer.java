package io.github.ammaarbakshi.herobrine.client.render;

import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;

import io.github.ammaarbakshi.herobrine.HerobrineMod;
import io.github.ammaarbakshi.herobrine.entity.HerobrineEntity;

/**
 * Draws Herobrine with the vanilla player mesh (wide arms, with jacket/sleeve/pants overlays) so he
 * moves, holds items and wears armor exactly like the player he copies. The skin is original;
 * see tools/gen_textures.py.
 */
public final class HerobrineRenderer
		extends HumanoidMobRenderer<HerobrineEntity, HerobrineRenderState, HumanoidModel<HerobrineRenderState>> {
	private static final Identifier TEXTURE = HerobrineMod.id("textures/entity/herobrine/herobrine.png");
	/**
	 * Eyes-only mode draws the body with the eyes texture: every other pixel is transparent and
	 * cut away, so nothing of him shows but the eyes (which the eyes layer then lights up).
	 */
	private static final Identifier EYES_TEXTURE = HerobrineMod.id("textures/entity/herobrine/herobrine_eyes.png");

	public HerobrineRenderer(EntityRendererProvider.Context context) {
		super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);
		addLayer(new HumanoidArmorLayer<>(this,
				ArmorModelSet.bake(ModelLayers.PLAYER_ARMOR, context.getModelSet(), HumanoidModel::new),
				context.getEquipmentRenderer()));
		addLayer(new HerobrineEyesLayer(this));
	}

	@Override
	public HerobrineRenderState createRenderState() {
		return new HerobrineRenderState();
	}

	@Override
	public void extractRenderState(HerobrineEntity entity, HerobrineRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.visibility = entity.visibility(partialTicks);
		state.eyesOnly = entity.eyesOnly();
	}

	@Override
	public Identifier getTextureLocation(HerobrineRenderState state) {
		return state.eyesOnly ? EYES_TEXTURE : TEXTURE;
	}

	/** Translucent while fading, so he can thin out into the air instead of popping. */
	@Override
	protected RenderType getRenderType(HerobrineRenderState state, boolean isBodyVisible, boolean forceTransparent, boolean appearGlowing) {
		if (isBodyVisible && state.visibility < 1.0F) {
			return RenderTypes.entityTranslucentCull(getTextureLocation(state));
		}
		return super.getRenderType(state, isBodyVisible, forceTransparent, appearGlowing);
	}

	@Override
	protected int getModelTint(HerobrineRenderState state) {
		return state.visibility < 1.0F ? ARGB.white(state.visibility) : super.getModelTint(state);
	}

	@Override
	protected float getShadowRadius(HerobrineRenderState state) {
		return state.eyesOnly ? 0.0F : super.getShadowRadius(state) * state.visibility;
	}

	/** Same poses a player gets (shield block, bow draw, trident wind-up), which mobs lack by default. */
	@Override
	protected HumanoidModel.ArmPose getArmPose(HerobrineEntity mob, HumanoidArm arm) {
		InteractionHand hand = mob.getMainArm() == arm ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
		ItemStack stack = mob.getItemInHand(hand);
		if (!stack.isEmpty() && mob.isUsingItem() && mob.getUsedItemHand() == hand) {
			ItemUseAnimation animation = stack.getUseAnimation();
			if (animation == ItemUseAnimation.BLOCK) {
				return HumanoidModel.ArmPose.BLOCK;
			}
			if (animation == ItemUseAnimation.BOW) {
				return HumanoidModel.ArmPose.BOW_AND_ARROW;
			}
			if (animation == ItemUseAnimation.TRIDENT) {
				return HumanoidModel.ArmPose.THROW_TRIDENT;
			}
		}
		HumanoidModel.ArmPose pose = super.getArmPose(mob, arm);
		if (pose == HumanoidModel.ArmPose.EMPTY && !stack.isEmpty()) {
			return HumanoidModel.ArmPose.ITEM;
		}
		return pose;
	}
}

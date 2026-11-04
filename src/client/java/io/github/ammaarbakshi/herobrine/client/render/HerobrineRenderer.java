package io.github.ammaarbakshi.herobrine.client.render;

import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.Identifier;
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
	public Identifier getTextureLocation(HerobrineRenderState state) {
		return TEXTURE;
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

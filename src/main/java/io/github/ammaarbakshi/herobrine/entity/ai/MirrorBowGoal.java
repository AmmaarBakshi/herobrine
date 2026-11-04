package io.github.ammaarbakshi.herobrine.entity.ai;

import java.util.EnumSet;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import io.github.ammaarbakshi.herobrine.entity.HerobrineEntity;
import io.github.ammaarbakshi.herobrine.mirror.TacticPlanner.Tactic;
import io.github.ammaarbakshi.herobrine.mirror.TraitId;

/**
 * Shoots like they shoot: draws for as long as they usually draw (a quick-tapper gets weak,
 * fast arrows), at player arrow speed. Arrows can't be picked up, so a fight never mints loot.
 */
public final class MirrorBowGoal extends MirrorGoal {
	private static final double GRAVITY = 0.05;
	/** Arrows slow down in flight; aim a little higher than drag-free physics says. */
	private static final double DRAG_FUDGE = 1.2;

	private int drawTarget;
	private int pause;

	public MirrorBowGoal(HerobrineEntity herobrine) {
		super(herobrine, EnumSet.of(Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		MirrorBrain brain = brain();
		return brain != null && brain.hasBow() && !brain.pillaring() && active(Tactic.RANGED);
	}

	@Override
	public void start() {
		MirrorBrain brain = brain();
		if (brain != null) {
			brain.holdBow();
		}
		pause = 5;
	}

	@Override
	public void stop() {
		if (herobrine.isUsingItem() && herobrine.getUsedItemHand() == InteractionHand.MAIN_HAND) {
			herobrine.stopUsingItem();
		}
		MirrorBrain brain = brain();
		if (brain != null) {
			brain.holdWeapon();
		}
	}

	@Override
	public void tick() {
		MirrorBrain brain = brain();
		ServerPlayer target = target();
		if (brain == null || target == null || !(herobrine.level() instanceof ServerLevel level)) {
			return;
		}
		herobrine.getLookControl().setLookAt(target, 30.0F, 30.0F);
		boolean drawing = herobrine.isUsingItem() && herobrine.getUseItem().is(Items.BOW);
		if (!herobrine.getSensing().hasLineOfSight(target)) {
			if (drawing) {
				herobrine.stopUsingItem();
			}
			return;
		}
		if (!drawing) {
			if (--pause > 0 || herobrine.isUsingItem()) {
				return;
			}
			if (!herobrine.getMainHandItem().is(Items.BOW)) {
				brain.holdBow();
			}
			herobrine.startUsingItem(InteractionHand.MAIN_HAND);
			int usual = (int) Math.round(brain.value(TraitId.BOW_DRAW_TICKS));
			drawTarget = Mth.clamp(usual + herobrine.getRandom().nextInt(7) - 3, 3, 40);
			return;
		}
		if (herobrine.getTicksUsingItem() >= drawTarget) {
			shoot(level, brain, target, BowItem.getPowerForTime(herobrine.getTicksUsingItem()));
			herobrine.stopUsingItem();
			pause = 4 + herobrine.getRandom().nextInt(6);
		}
	}

	private void shoot(ServerLevel level, MirrorBrain brain, ServerPlayer target, float power) {
		ItemStack bow = herobrine.getMainHandItem();
		ItemStack ammo = new ItemStack(Items.ARROW);
		AbstractArrow arrow = ProjectileUtil.getMobArrow(herobrine, ammo, power, bow);
		arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
		if (power >= 1.0F) {
			arrow.setCritArrow(true);
		}
		float velocity = Math.max(0.5F, power * 3.0F);
		double dx = target.getX() - herobrine.getX();
		double dy = target.getY(0.3333333333333333) - arrow.getY();
		double dz = target.getZ() - herobrine.getZ();
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		// Small-angle ballistic lift: tan(theta) ~ g d / (2 v^2).
		double lift = horizontal * (GRAVITY * horizontal / (2.0 * velocity * velocity)) * DRAG_FUDGE;
		float inaccuracy = 2.0F + (1.0F - power) * 6.0F;
		Projectile.spawnProjectileUsingShoot(arrow, level, ammo, dx, dy + lift, dz, velocity, inaccuracy);
		level.playSound(null, herobrine.getX(), herobrine.getY(), herobrine.getZ(), SoundEvents.ARROW_SHOOT, herobrine.getSoundSource(),
				1.0F, 1.0F / (herobrine.getRandom().nextFloat() * 0.4F + 1.2F) + power * 0.5F);
		brain.shots++;
	}
}

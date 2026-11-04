package io.github.ammaarbakshi.herobrine.entity.ai;

import java.util.EnumSet;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import io.github.ammaarbakshi.herobrine.entity.HerobrineEntity;
import io.github.ammaarbakshi.herobrine.mirror.TacticPlanner.Tactic;

/**
 * Heals with what they heal with, when they would: backs off while eating or drinking (vanilla
 * plays the sounds and particles), then applies the item's effect. Limited to the configured
 * number of heals per fight.
 */
public final class MirrorHealGoal extends MirrorGoal {
	/** Vanilla consumables finish at 32 ticks; he stops just short and applies the effect himself. */
	private static final int CONSUME_TICKS = 30;
	private static final int GIVE_UP_TICKS = 80;

	private int ticks;
	private int side = 1;

	public MirrorHealGoal(HerobrineEntity herobrine) {
		super(herobrine, EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		MirrorBrain brain = brain();
		return brain != null && active(Tactic.HEAL);
	}

	@Override
	public void start() {
		MirrorBrain brain = brain();
		ticks = 0;
		side = herobrine.getRandom().nextBoolean() ? 1 : -1;
		herobrine.getNavigation().stop();
		herobrine.setSprinting(false);
		if (herobrine.isUsingItem()) {
			herobrine.stopUsingItem();
		}
		if (brain != null) {
			brain.holdHealItem();
		}
		herobrine.startUsingItem(InteractionHand.MAIN_HAND);
	}

	@Override
	public void stop() {
		if (herobrine.isUsingItem()) {
			herobrine.stopUsingItem();
		}
		MirrorBrain brain = brain();
		if (brain != null) {
			brain.holdWeapon();
			if (brain.tactic() == Tactic.HEAL) {
				brain.setTactic(Tactic.MELEE);
			}
		}
	}

	@Override
	public void tick() {
		MirrorBrain brain = brain();
		ServerPlayer target = target();
		if (brain == null || target == null) {
			return;
		}
		ticks++;
		herobrine.getLookControl().setLookAt(target, 30.0F, 30.0F);
		herobrine.lookAt(target, 30.0F, 30.0F);
		// Players eat while walking backwards, slowed by the item in use.
		move().strafeAt(herobrine.distanceTo(target) < 6.0 ? -1.0F : 0.0F, side * 0.4F, 0.5);

		if (ticks >= CONSUME_TICKS && herobrine.isUsingItem()) {
			herobrine.stopUsingItem();
			applyHeal(brain);
			brain.onHealed(now());
			brain.setTactic(Tactic.MELEE);
		} else if (ticks >= GIVE_UP_TICKS || (!herobrine.isUsingItem() && ticks > 2)) {
			// Interrupted (item swapped out from under him): try again later.
			brain.setTactic(Tactic.MELEE);
		}
	}

	private void applyHeal(MirrorBrain brain) {
		switch (brain.healKind()) {
			case GOLDEN_APPLE -> {
				herobrine.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
				herobrine.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 2400, 0));
			}
			case POTION -> herobrine.heal(4.0F);
			case FOOD -> herobrine.heal(4.0F);
		}
	}
}

package io.github.ammaarbakshi.herobrine.entity.ai;

import java.util.EnumSet;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import io.github.ammaarbakshi.herobrine.entity.HerobrineEntity;
import io.github.ammaarbakshi.herobrine.mirror.TacticPlanner.Tactic;
import io.github.ammaarbakshi.herobrine.mirror.TraitId;

/**
 * Raises the shield the way they do: as often as they block, after their own reaction delay.
 * A "threat" is the player facing him with a charged swing in reach, or a bow drawn at him;
 * only the moment a threat appears is reacted to, like a person would.
 */
public final class MirrorShieldGoal extends MirrorGoal {
	private static final double MELEE_THREAT_RANGE = 4.5;
	private static final double FACING_DOT = 0.9;
	private static final int MIN_HOLD = 12;
	private static final int MAX_HOLD = 50;
	private static final int LOWER_AFTER_CALM = 8;

	private boolean lastThreat;
	private long raiseAt = -1;
	private int held;
	private int calm;

	public MirrorShieldGoal(HerobrineEntity herobrine) {
		super(herobrine, EnumSet.noneOf(Flag.class));
	}

	@Override
	public boolean canUse() {
		MirrorBrain brain = brain();
		return brain != null && brain.hasShield() && !brain.pillaring() && active(Tactic.MELEE, Tactic.RANGED);
	}

	@Override
	public void stop() {
		lower();
		raiseAt = -1;
		lastThreat = false;
	}

	@Override
	public void tick() {
		MirrorBrain brain = brain();
		ServerPlayer target = target();
		if (brain == null || target == null) {
			return;
		}
		boolean threat = isThreat(target);
		long now = now();
		if (threat && !lastThreat && raiseAt < 0 && !blocking()
				&& herobrine.getRandom().nextDouble() < brain.value(TraitId.SHIELD_USE_RATE)) {
			int reaction = (int) Math.round(brain.value(TraitId.SHIELD_REACTION_TICKS)) + herobrine.getRandom().nextInt(5) - 2;
			raiseAt = now + Math.max(1, reaction);
		}
		lastThreat = threat;

		if (raiseAt >= 0 && now >= raiseAt) {
			raiseAt = -1;
			if (!herobrine.isUsingItem()) {
				herobrine.startUsingItem(InteractionHand.OFF_HAND);
				held = 0;
				calm = 0;
				brain.shieldRaises++;
			}
		}
		if (blocking()) {
			held++;
			calm = threat ? 0 : calm + 1;
			if (held >= MAX_HOLD || (held >= MIN_HOLD && calm >= LOWER_AFTER_CALM)) {
				lower();
			}
		}
	}

	private boolean blocking() {
		return herobrine.isUsingItem() && herobrine.getUsedItemHand() == InteractionHand.OFF_HAND;
	}

	private void lower() {
		if (blocking()) {
			herobrine.stopUsingItem();
		}
	}

	private boolean isThreat(ServerPlayer target) {
		Vec3 toHim = herobrine.getEyePosition().subtract(target.getEyePosition());
		double distance = toHim.length();
		if (distance < 1.0E-4 || target.getViewVector(1.0F).dot(toHim.scale(1.0 / distance)) < FACING_DOT) {
			return false;
		}
		if (target.isUsingItem() && target.getUseItem().is(Items.BOW) && target.getTicksUsingItem() >= 8) {
			return true;
		}
		return distance <= MELEE_THREAT_RANGE && target.getAttackStrengthScale(0.5F) >= 0.9F;
	}
}

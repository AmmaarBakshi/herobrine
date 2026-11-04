package io.github.ammaarbakshi.herobrine.entity.ai;

import java.util.EnumSet;

import net.minecraft.server.level.ServerPlayer;

import io.github.ammaarbakshi.herobrine.entity.HerobrineEntity;
import io.github.ammaarbakshi.herobrine.mirror.TacticPlanner;
import io.github.ammaarbakshi.herobrine.mirror.TacticPlanner.Tactic;
import io.github.ammaarbakshi.herobrine.mirror.TraitId;

/**
 * Spacing and footwork: keeps the player's preferred distance and strafes the way they do
 * (their side, their intensity, their jumping). Paths in when far or out of sight.
 */
public final class StrafeGoal extends MirrorGoal {
	/** Melee spacing never goes beyond reach, whatever their average range says. */
	private static final double MAX_MELEE_RANGE = 2.8;
	private static final double MIN_RANGED_RANGE = 8.0;
	private static final double STRAFE_SPEED = 0.9;

	private int side = 1;
	private int sideTimer;

	public StrafeGoal(HerobrineEntity herobrine) {
		super(herobrine, EnumSet.of(Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		MirrorBrain brain = brain();
		return brain != null && !brain.pillaring() && active(Tactic.MELEE, Tactic.RANGED);
	}

	@Override
	public void stop() {
		herobrine.getNavigation().stop();
		herobrine.setSprinting(false);
	}

	@Override
	public void tick() {
		MirrorBrain brain = brain();
		ServerPlayer target = target();
		if (brain == null || target == null) {
			return;
		}
		double distance = herobrine.distanceTo(target);
		boolean ranged = brain.tactic() == Tactic.RANGED;
		double preferred = brain.habits().preferredRange();
		double desired = ranged ? Math.max(preferred, MIN_RANGED_RANGE) : Math.max(1.5, Math.min(preferred, MAX_MELEE_RANGE));
		herobrine.getLookControl().setLookAt(target, 30.0F, 30.0F);

		if (distance > desired + 6.0 || !herobrine.getSensing().hasLineOfSight(target)) {
			// Too far to dance: walk in (sprinting, if that's how they close distance).
			boolean sprint = !ranged && brain.value(TraitId.SPRINT_HIT_RATE) > 0.3;
			herobrine.setSprinting(sprint);
			herobrine.getNavigation().moveTo(target, sprint ? 1.3 : 1.0);
			return;
		}
		herobrine.getNavigation().stop();
		if (--sideTimer <= 0) {
			side = TacticPlanner.strafeSide(brain.value(TraitId.STRAFE_BIAS), rng());
			sideTimer = 20 + herobrine.getRandom().nextInt(25);
		}
		float forwards = distance > desired + 0.75 ? 1.0F : distance < desired - 1.0 ? -1.0F : 0.0F;
		float right = (float) (side * brain.value(TraitId.STRAFE_INTENSITY));
		move().strafeAt(forwards, right, STRAFE_SPEED);
		herobrine.lookAt(target, 30.0F, 30.0F);

		if (herobrine.onGround() && herobrine.getRandom().nextDouble() < brain.value(TraitId.JUMP_FREQUENCY) / 20.0) {
			herobrine.getJumpControl().jump();
		}
	}
}

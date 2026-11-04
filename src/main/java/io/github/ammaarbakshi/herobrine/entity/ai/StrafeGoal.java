package io.github.ammaarbakshi.herobrine.entity.ai;

import java.util.EnumSet;

import net.minecraft.server.level.ServerPlayer;

import io.github.ammaarbakshi.herobrine.entity.HerobrineEntity;
import io.github.ammaarbakshi.herobrine.mirror.CounterStrategy;
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
	/** Gap closer: approaching (rolled once per approach) and the current zig-zag direction. */
	private boolean approaching;
	private boolean gapClosing;
	private int zig = 1;

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

		boolean lineOfSight = herobrine.getSensing().hasLineOfSight(target);
		if (distance > desired + 6.0 || !lineOfSight) {
			if (!approaching) {
				approaching = true;
				gapClosing = !ranged && brain.counter(CounterStrategy.GAP_CLOSER, rng());
				if (gapClosing) {
					brain.countered(CounterStrategy.GAP_CLOSER);
				}
			}
			if (gapClosing && lineOfSight && !herobrine.horizontalCollision) {
				// Counter to kiting: sprint straight at them, zig-zagging to spoil their aim.
				if (herobrine.tickCount % 8 == 0) {
					zig = -zig;
				}
				herobrine.getNavigation().stop();
				herobrine.setSprinting(true);
				move().strafeAt(1.0F, zig * 0.7F, 1.35);
				herobrine.lookAt(target, 30.0F, 30.0F);
				return;
			}
			// Too far to dance: walk in (sprinting, if that's how they close distance).
			boolean sprint = gapClosing || (!ranged && brain.value(TraitId.SPRINT_HIT_RATE) > 0.3);
			herobrine.setSprinting(sprint);
			herobrine.getNavigation().moveTo(target, sprint ? 1.3 : 1.0);
			return;
		}
		approaching = false;
		herobrine.getNavigation().stop();
		if (--sideTimer <= 0) {
			double bias = brain.value(TraitId.STRAFE_BIAS);
			int lead = CounterStrategy.strafeLeadSide(bias);
			if (lead != 0 && brain.counter(CounterStrategy.STRAFE_LEAD, rng())) {
				// Facing each other, their right is his left: moving there keeps him in their path.
				side = -lead;
				brain.countered(CounterStrategy.STRAFE_LEAD);
			} else {
				side = TacticPlanner.strafeSide(bias, rng());
			}
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

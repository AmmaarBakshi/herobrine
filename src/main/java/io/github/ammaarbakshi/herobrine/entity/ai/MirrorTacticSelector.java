package io.github.ammaarbakshi.herobrine.entity.ai;

import java.util.EnumSet;

import net.minecraft.server.level.ServerPlayer;

import io.github.ammaarbakshi.herobrine.entity.HerobrineEntity;
import io.github.ammaarbakshi.herobrine.mirror.TacticPlanner;
import io.github.ammaarbakshi.herobrine.mirror.TacticPlanner.Tactic;

/**
 * The utility AI's clock: every half second, asks {@link TacticPlanner} what to do given the
 * player's habits and the fight so far. The other goals act on the answer.
 */
public final class MirrorTacticSelector extends MirrorGoal {
	private static final int DECIDE_INTERVAL = 10;

	public MirrorTacticSelector(HerobrineEntity herobrine) {
		super(herobrine, EnumSet.noneOf(Flag.class));
	}

	@Override
	public boolean canUse() {
		return brain() != null;
	}

	@Override
	public void tick() {
		MirrorBrain brain = brain();
		if (brain == null) {
			return;
		}
		brain.tickTactic();
		ServerPlayer target = brain.target();
		herobrine.setTarget(target);
		if (target == null || herobrine.ageInMode() % DECIDE_INTERVAL != 0 || brain.pillaring()) {
			return;
		}
		long now = now();
		TacticPlanner.Situation situation = new TacticPlanner.Situation(
				herobrine.getHealth() / herobrine.getMaxHealth(),
				herobrine.distanceTo(target),
				brain.healsLeft(),
				brain.healOnCooldown(now),
				brain.retreatOnCooldown(now),
				brain.hasBow(),
				brain.tactic(),
				brain.ticksInTactic());
		Tactic next = TacticPlanner.decide(brain.habits(), situation, rng());
		brain.setTactic(next);
	}
}

package io.github.ammaarbakshi.herobrine.entity.ai;

import java.util.EnumSet;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import io.github.ammaarbakshi.herobrine.entity.HerobrineEntity;
import io.github.ammaarbakshi.herobrine.mirror.CounterStrategy;
import io.github.ammaarbakshi.herobrine.mirror.TacticPlanner;
import io.github.ammaarbakshi.herobrine.mirror.TacticPlanner.Tactic;
import io.github.ammaarbakshi.herobrine.sighting.SpawnFinder;

/**
 * The utility AI's clock: every half second, asks {@link TacticPlanner} what to do given the
 * player's habits and the fight so far. The other goals act on the answer.
 *
 * <p>Also watches for the player teleporting (a pearl) so the pearl-follow counter can throw
 * one after them.
 */
public final class MirrorTacticSelector extends MirrorGoal {
	private static final int DECIDE_INTERVAL = 10;
	/** A jump this far in one tick is a teleport, not movement. */
	private static final double TELEPORT_DISTANCE = 6.0;
	/** Beyond this the confrontation's own flee rule brings him. */
	private static final double FOLLOW_MAX_DISTANCE = 60.0;
	private static final int FOLLOW_DELAY = 8;

	private @Nullable Vec3 lastTargetPos;
	private long followAt = -1;

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
		if (target == null) {
			lastTargetPos = null;
			return;
		}
		tickPearlFollow(brain, target);
		if (herobrine.ageInMode() % DECIDE_INTERVAL != 0 || brain.pillaring()) {
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

	private void tickPearlFollow(MirrorBrain brain, ServerPlayer target) {
		Vec3 pos = target.position();
		long now = now();
		if (lastTargetPos != null && followAt < 0 && brain.plan().has(CounterStrategy.PEARL_FOLLOW)) {
			double jump = pos.distanceTo(lastTargetPos);
			if (jump > TELEPORT_DISTANCE && herobrine.distanceTo(target) < FOLLOW_MAX_DISTANCE
					&& brain.counter(CounterStrategy.PEARL_FOLLOW, rng())) {
				MirrorRetreatGoal.throwPearl(herobrine);
				followAt = now + FOLLOW_DELAY;
			}
		}
		lastTargetPos = pos;
		if (followAt >= 0 && now >= followAt) {
			followAt = -1;
			Vec3 spot = spotNear(target);
			if (spot != null) {
				MirrorRetreatGoal.land(herobrine, spot);
				brain.countered(CounterStrategy.PEARL_FOLLOW);
			}
		}
	}

	/** Standing room a couple of blocks from where they landed. */
	private @Nullable Vec3 spotNear(ServerPlayer target) {
		if (!(herobrine.level() instanceof ServerLevel level)) {
			return null;
		}
		for (int attempt = 0; attempt < 8; attempt++) {
			double angle = herobrine.getRandom().nextDouble() * Math.PI * 2.0;
			int x = Mth.floor(target.getX() + Math.cos(angle) * 2.5);
			int z = Mth.floor(target.getZ() + Math.sin(angle) * 2.5);
			BlockPos feet = SpawnFinder.findStandable(level, x, z, target.getBlockY(), 3, false);
			if (feet != null) {
				return Vec3.atBottomCenterOf(feet);
			}
		}
		return null;
	}
}

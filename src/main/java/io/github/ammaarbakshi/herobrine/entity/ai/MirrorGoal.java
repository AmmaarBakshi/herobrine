package io.github.ammaarbakshi.herobrine.entity.ai;

import java.util.EnumSet;
import java.util.random.RandomGenerator;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.goal.Goal;

import io.github.ammaarbakshi.herobrine.entity.HerobrineEntity;
import io.github.ammaarbakshi.herobrine.mirror.TacticPlanner.Tactic;

/** Base for boss-mode goals: each one runs only while the brain's tactic calls for it. */
abstract class MirrorGoal extends Goal {
	protected final HerobrineEntity herobrine;

	MirrorGoal(HerobrineEntity herobrine, EnumSet<Flag> flags) {
		this.herobrine = herobrine;
		setFlags(flags);
	}

	protected @Nullable MirrorBrain brain() {
		return herobrine.mode() == HerobrineEntity.Mode.BOSS ? herobrine.brain() : null;
	}

	protected @Nullable ServerPlayer target() {
		MirrorBrain brain = brain();
		return brain == null ? null : brain.target();
	}

	/** True when in boss mode, with a valid target, and the tactic is one of {@code tactics}. */
	protected boolean active(Tactic... tactics) {
		MirrorBrain brain = brain();
		if (brain == null || brain.target() == null) {
			return false;
		}
		for (Tactic tactic : tactics) {
			if (brain.tactic() == tactic) {
				return true;
			}
		}
		return false;
	}

	protected long now() {
		return herobrine.level().getGameTime();
	}

	/** The entity's random source, as the {@link RandomGenerator} the pure planner takes. */
	protected RandomGenerator rng() {
		return herobrine.getRandom()::nextLong;
	}

	protected HerobrineMoveControl move() {
		return (HerobrineMoveControl) herobrine.getMoveControl();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		// Combat timing (hits, draws, shield reactions) is measured in single ticks.
		return true;
	}
}

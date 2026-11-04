package io.github.ammaarbakshi.herobrine.entity.ai;

import java.util.EnumSet;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.goal.Goal;

import io.github.ammaarbakshi.herobrine.entity.HerobrineEntity;
import io.github.ammaarbakshi.herobrine.entity.HerobrineEntity.Mode;

/**
 * Stands perfectly still and tracks the player with his head. Used by every non-combat mode;
 * the stillness is the point, so it claims MOVE as well as LOOK to block any wandering.
 */
public final class StareGoal extends Goal {
	/** Slow head turn: he follows you, he doesn't snap to you. */
	private static final float HEAD_TURN_SPEED = 6.0F;
	private static final double IDLE_RANGE = 32.0;

	private final HerobrineEntity herobrine;
	private @Nullable Player target;

	public StareGoal(HerobrineEntity herobrine) {
		this.herobrine = herobrine;
		setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
	}

	@Override
	public boolean canUse() {
		if (herobrine.mode() == Mode.BOSS || herobrine.mode() == Mode.GHOST) {
			return false;
		}
		target = findTarget();
		return target != null;
	}

	@Override
	public boolean canContinueToUse() {
		return canUse();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void start() {
		herobrine.getNavigation().stop();
	}

	@Override
	public void tick() {
		if (target != null) {
			herobrine.getLookControl().setLookAt(target, HEAD_TURN_SPEED, herobrine.getMaxHeadXRot());
		}
	}

	private @Nullable Player findTarget() {
		Player watched = herobrine.watchedPlayer();
		if (watched != null) {
			return watched;
		}
		if (herobrine.mode() == Mode.IDLE) {
			return herobrine.level().getNearestPlayer(herobrine, IDLE_RANGE);
		}
		return null;
	}
}

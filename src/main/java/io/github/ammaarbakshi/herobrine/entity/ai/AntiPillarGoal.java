package io.github.ammaarbakshi.herobrine.entity.ai;

import java.util.EnumSet;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.Vec3;

import io.github.ammaarbakshi.herobrine.config.HerobrineConfig.Griefing;
import io.github.ammaarbakshi.herobrine.entity.HerobrineEntity;
import io.github.ammaarbakshi.herobrine.haunt.WorldGuard;
import io.github.ammaarbakshi.herobrine.mirror.CombatState;
import io.github.ammaarbakshi.herobrine.mirror.CounterStrategy;
import io.github.ammaarbakshi.herobrine.mirror.TacticPlanner.Tactic;
import io.github.ammaarbakshi.herobrine.registry.ModAttachments;

/**
 * The anti-pillar counter: when a habitual pillarer towers up, he walks to the base and
 * breaks the highest block he can reach, usually the one under their feet, so they come down
 * (the block drops, as if mined). Only blocks the recorder saw them place under their own
 * feet are touched. With nothing he may break, he takes the bow.
 */
public final class AntiPillarGoal extends MirrorGoal {
	private static final double MIN_HEIGHT_ABOVE = 2.0;
	private static final double MAX_HORIZONTAL = 10.0;
	private static final double REACH = 4.5;
	private static final int BREAK_TICKS = 12;
	private static final int REPATH_TICKS = 10;

	/** One roll per time they go up; reset once they're back down. */
	private boolean rolled;
	private boolean engaged;
	private @Nullable BlockPos breaking;
	private int breakTicks;
	private int repathIn;

	public AntiPillarGoal(HerobrineEntity herobrine) {
		super(herobrine, EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		MirrorBrain brain = brain();
		ServerPlayer target = target();
		if (brain == null || target == null || !brain.plan().has(CounterStrategy.ANTI_PILLAR) || brain.pillaring()
				|| !active(Tactic.MELEE, Tactic.RANGED)) {
			return false;
		}
		boolean up = target.getY() - herobrine.getY() >= MIN_HEIGHT_ABOVE && horizontal(target.position()) <= MAX_HORIZONTAL;
		if (!up) {
			rolled = false;
			engaged = false;
			return false;
		}
		if (!rolled) {
			rolled = true;
			engaged = brain.counter(CounterStrategy.ANTI_PILLAR, rng());
			if (engaged) {
				brain.countered(CounterStrategy.ANTI_PILLAR);
			}
		}
		return engaged;
	}

	@Override
	public void start() {
		breaking = null;
		breakTicks = 0;
		repathIn = 0;
	}

	@Override
	public void stop() {
		if (breaking != null && herobrine.level() instanceof ServerLevel level) {
			level.destroyBlockProgress(herobrine.getId(), breaking, -1);
		}
		breaking = null;
		herobrine.getNavigation().stop();
	}

	@Override
	public void tick() {
		MirrorBrain brain = brain();
		ServerPlayer target = target();
		if (brain == null || target == null || !(herobrine.level() instanceof ServerLevel level)) {
			return;
		}
		herobrine.getLookControl().setLookAt(target, 30.0F, 30.0F);
		CombatState combat = ModAttachments.combat(target);
		List<BlockPos> standing = WorldGuard.allows(Griefing.NATURAL_ONLY) ? standing(level, combat.pillarBlocks()) : List.of();
		if (standing.isEmpty()) {
			// Nothing he may break (or it's all gone): shoot them down instead.
			if (brain.hasBow()) {
				brain.setTactic(Tactic.RANGED);
			}
			engaged = false;
			return;
		}
		Vec3 eye = herobrine.getEyePosition();
		BlockPos block = null;
		BlockPos nearest = standing.getFirst();
		for (BlockPos pos : standing) {
			double distance = eye.distanceTo(Vec3.atCenterOf(pos));
			if (distance <= REACH && (block == null || pos.getY() > block.getY())) {
				block = pos;
			}
			if (distance < eye.distanceTo(Vec3.atCenterOf(nearest))) {
				nearest = pos;
			}
		}
		if (block == null) {
			if (--repathIn <= 0 || herobrine.getNavigation().isDone()) {
				herobrine.getNavigation().moveTo(nearest.getX() + 0.5, nearest.getY(), nearest.getZ() + 0.5, 1.2);
				repathIn = REPATH_TICKS;
			}
			return;
		}
		repathIn = 0;
		Vec3 centre = Vec3.atCenterOf(block);
		herobrine.getNavigation().stop();
		herobrine.getLookControl().setLookAt(centre.x, centre.y, centre.z);
		if (!block.equals(breaking)) {
			breaking = block;
			breakTicks = 0;
		}
		breakTicks++;
		if (breakTicks % 4 == 0) {
			herobrine.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
		}
		level.destroyBlockProgress(herobrine.getId(), block, Math.min(9, breakTicks * 10 / BREAK_TICKS));
		if (breakTicks >= BREAK_TICKS) {
			level.destroyBlockProgress(herobrine.getId(), block, -1);
			level.destroyBlock(block, true, herobrine);
			combat.forgetPillarBlock(block);
			breaking = null;
			breakTicks = 0;
		}
	}

	/** Their pillar blocks that are still there. */
	private static List<BlockPos> standing(ServerLevel level, List<BlockPos> blocks) {
		return blocks.stream().filter(pos -> level.isLoaded(pos) && !level.getBlockState(pos).isAir()).toList();
	}

	private double horizontal(Vec3 pos) {
		double dx = pos.x - herobrine.getX();
		double dz = pos.z - herobrine.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}
}

package io.github.ammaarbakshi.herobrine.entity.ai;

import java.util.EnumSet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import io.github.ammaarbakshi.herobrine.entity.HerobrineEntity;
import io.github.ammaarbakshi.herobrine.mirror.TacticPlanner.Tactic;

/**
 * The pillar escape: jump-places a short tower of the player's favourite block under his own
 * feet, then fights from the top (bow if he has one). Every block is remembered and removed
 * when the fight ends, and blocks only ever go into air.
 */
public final class PillarGoal extends MirrorGoal {
	public static final int MIN_HEIGHT = 3;
	public static final int MAX_HEIGHT = 4;
	private static final int TIMEOUT = 100;

	private BlockPos column = BlockPos.ZERO;
	private int height;
	private int placed;
	private int ticks;

	public PillarGoal(HerobrineEntity herobrine) {
		super(herobrine, EnumSet.of(Flag.MOVE, Flag.JUMP));
	}

	/** Solid ground under him (he may be mid-hop) and air for the tower plus his own height. */
	public static boolean hasRoom(HerobrineEntity herobrine) {
		BlockPos feet = groundFeet(herobrine);
		for (int dy = 0; dy < MAX_HEIGHT + 2; dy++) {
			BlockPos pos = feet.above(dy);
			BlockState state = herobrine.level().getBlockState(pos);
			if (!state.isAir()) {
				return false;
			}
		}
		return herobrine.level().getBlockState(feet.below()).isFaceSturdy(herobrine.level(), feet.below(), Direction.UP);
	}

	/** The air block his feet would occupy standing on the ground under him (within 2 blocks). */
	private static BlockPos groundFeet(HerobrineEntity herobrine) {
		BlockPos feet = herobrine.blockPosition();
		for (int i = 0; i < 2 && herobrine.level().getBlockState(feet.below()).isAir(); i++) {
			feet = feet.below();
		}
		return feet;
	}

	@Override
	public boolean canUse() {
		MirrorBrain brain = brain();
		return brain != null && brain.pillaring() && target() != null;
	}

	@Override
	public void start() {
		column = groundFeet(herobrine);
		height = MIN_HEIGHT + herobrine.getRandom().nextInt(MAX_HEIGHT - MIN_HEIGHT + 1);
		placed = 0;
		ticks = 0;
		herobrine.getNavigation().stop();
		herobrine.setSprinting(false);
		// Centre on the column so each block lands squarely under him.
		herobrine.teleportTo(column.getX() + 0.5, herobrine.getY(), column.getZ() + 0.5);
	}

	@Override
	public void stop() {
		MirrorBrain brain = brain();
		if (brain != null && brain.pillaring()) {
			brain.setPillaring(false);
			brain.onRetreatEnded(now());
			brain.setTactic(brain.hasBow() ? Tactic.RANGED : Tactic.MELEE);
		}
	}

	@Override
	public void tick() {
		MirrorBrain brain = brain();
		ServerPlayer target = target();
		if (brain == null || target == null || !(herobrine.level() instanceof ServerLevel level)) {
			return;
		}
		ticks++;
		herobrine.getLookControl().setLookAt(target, 30.0F, 30.0F);
		Vec3 motion = herobrine.getDeltaMovement();
		herobrine.setDeltaMovement(0.0, motion.y, 0.0);

		if (placed >= height || ticks >= TIMEOUT) {
			if (herobrine.onGround()) {
				brain.setPillaring(false);
				brain.onRetreatEnded(now());
				brain.setTactic(brain.hasBow() ? Tactic.RANGED : Tactic.MELEE);
			}
			return;
		}
		if (herobrine.onGround()) {
			herobrine.getJumpControl().jump();
			return;
		}
		BlockPos next = column.above(placed);
		// Place once his feet have cleared the top of the block-to-be.
		if (herobrine.getY() >= next.getY() + 1.0 && level.getBlockState(next).isAir()) {
			BlockState block = brain.pillarBlock();
			level.setBlock(next, block, Block.UPDATE_ALL);
			brain.rememberPlaced(next);
			herobrine.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
			SoundType sound = block.getSoundType();
			level.playSound(null, next, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
			placed++;
		} else if (!level.getBlockState(next).isAir() && !level.getBlockState(next).is(brain.pillarBlock().getBlock())) {
			// Something got in the way: stop where he is.
			height = placed;
		}
	}
}

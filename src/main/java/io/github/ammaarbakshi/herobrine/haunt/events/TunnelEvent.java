package io.github.ammaarbakshi.herobrine.haunt.events;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import io.github.ammaarbakshi.herobrine.config.HerobrineConfig.Griefing;
import io.github.ammaarbakshi.herobrine.haunt.WorldEditLog;
import io.github.ammaarbakshi.herobrine.haunt.WorldGuard;
import io.github.ammaarbakshi.herobrine.registry.ModTags;

/**
 * A perfectly straight 2x2 tunnel through natural stone near where the player has been mining.
 * The tunnel stops at the first block that isn't plain rock (ores, caves, water) so it never
 * floods anything or eats valuables; if that leaves it shorter than 10 blocks it is undone.
 */
final class TunnelEvent implements HauntEvent {
	private static final int MIN_LENGTH = 10;
	private static final int MAX_LENGTH = 30;
	private static final int ATTEMPTS = 6;

	@Override
	public String id() {
		return "tunnel";
	}

	@Override
	public Griefing requiredGriefing() {
		return Griefing.NATURAL_ONLY;
	}

	@Override
	public int weight(EventContext ctx) {
		return ctx.trail().mined().isEmpty() ? 0 : 8;
	}

	@Override
	public boolean fire(EventContext ctx) {
		ServerLevel level = ctx.level();
		RandomSource random = ctx.random();
		List<BlockPos> mined = ctx.trail().mined();
		BlockPos fallback = ctx.player().blockPosition();
		for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
			BlockPos base = mined.isEmpty() ? fallback : mined.get(random.nextInt(Math.min(4, mined.size())));
			Direction toStart = Direction.Plane.HORIZONTAL.getRandomDirection(random);
			BlockPos start = base.relative(toStart, 6 + random.nextInt(9)).above(random.nextInt(3) - 1);
			Direction dir = Direction.Plane.HORIZONTAL.getRandomDirection(random);
			int length = MIN_LENGTH + random.nextInt(MAX_LENGTH - MIN_LENGTH + 1);
			if (tryCarve(ctx, level, start, dir, length)) {
				return true;
			}
		}
		return false;
	}

	private boolean tryCarve(EventContext ctx, ServerLevel level, BlockPos start, Direction dir, int length) {
		Direction side = dir.getClockWise();
		BoundingBox box = BoundingBox.fromCorners(start, start.relative(dir, length - 1).relative(side).above());
		if (!level.isLoaded(start) || WorldGuard.isProtected(level, box)) {
			return false;
		}
		WorldEditLog.Recorder edit = ctx.log().begin(level, id());
		int carved = 0;
		for (int i = 0; i < length; i++) {
			BlockPos slice = start.relative(dir, i);
			if (!sliceIsCarvable(level, slice, side)) {
				break;
			}
			for (int w = 0; w < 2; w++) {
				for (int h = 0; h < 2; h++) {
					edit.set(slice.relative(side, w).above(h), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
				}
			}
			carved++;
		}
		if (carved < MIN_LENGTH) {
			edit.rollback();
			return false;
		}
		return edit.commit();
	}

	/** All four blocks are plain rock, unseen, none touches a fluid, and nothing above can fall in. */
	private static boolean sliceIsCarvable(ServerLevel level, BlockPos slice, Direction side) {
		for (int w = 0; w < 2; w++) {
			for (int h = 0; h < 2; h++) {
				BlockPos pos = slice.relative(side, w).above(h);
				if (!level.isLoaded(pos) || !level.getBlockState(pos).is(ModTags.TUNNEL_CARVABLE) || WorldGuard.isWatched(level, pos)) {
					return false;
				}
				for (Direction d : Direction.values()) {
					if (!level.getFluidState(pos.relative(d)).isEmpty()) {
						return false;
					}
				}
				// Sand or gravel would drop into the tunnel, and undo couldn't put it back.
				if (h == 1 && level.getBlockState(pos.above()).getBlock() instanceof FallingBlock) {
					return false;
				}
			}
		}
		return true;
	}
}

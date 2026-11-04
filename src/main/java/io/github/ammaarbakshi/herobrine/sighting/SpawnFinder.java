package io.github.ammaarbakshi.herobrine.sighting;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/** Bounded searches for a spot a player-sized figure can stand on. Never loads chunks. */
public final class SpawnFinder {
	private SpawnFinder() {
	}

	/** Solid floor below, two blocks of open, dry space above it. */
	public static boolean isStandable(ServerLevel level, BlockPos feet) {
		if (!level.isLoaded(feet)) {
			return false;
		}
		BlockPos below = feet.below();
		BlockState floor = level.getBlockState(below);
		if (!floor.isFaceSturdy(level, below, Direction.UP)) {
			return false;
		}
		return isOpen(level, feet) && isOpen(level, feet.above());
	}

	private static boolean isOpen(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty();
	}

	/**
	 * Finds standing room in column (x, z). On the surface the heightmap answers in O(1);
	 * underground, scans at most {@code range} blocks up and down from {@code aroundY}.
	 */
	public static @Nullable BlockPos findStandable(ServerLevel level, int x, int z, int aroundY, int range, boolean surface) {
		if (!level.hasChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z))) {
			return null;
		}
		if (surface) {
			int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			BlockPos pos = new BlockPos(x, top, z);
			if (Math.abs(top - aroundY) <= range * 3 && isStandable(level, pos)) {
				return pos;
			}
			return null;
		}
		for (int dy = 0; dy <= range; dy++) {
			BlockPos up = new BlockPos(x, aroundY + dy, z);
			if (isStandable(level, up)) {
				return up;
			}
			if (dy > 0) {
				BlockPos down = new BlockPos(x, aroundY - dy, z);
				if (isStandable(level, down)) {
					return down;
				}
			}
		}
		return null;
	}
}

package io.github.ammaarbakshi.herobrine.haunt.events;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import io.github.ammaarbakshi.herobrine.config.HerobrineConfig.Griefing;
import io.github.ammaarbakshi.herobrine.haunt.WorldEditLog;
import io.github.ammaarbakshi.herobrine.haunt.WorldGuard;

/**
 * Strips every leaf off one natural tree near the player, leaving a bare trunk. Only
 * non-persistent leaves count, so hedges and builds made of player-placed leaves are safe.
 */
final class LeaflessTreeEvent implements HauntEvent {
	private static final int ATTEMPTS = 48;
	private static final int CANOPY_RADIUS = 6;
	private static final int MAX_LEAVES = 600;

	@Override
	public String id() {
		return "leafless_tree";
	}

	@Override
	public Griefing requiredGriefing() {
		return Griefing.NATURAL_ONLY;
	}

	@Override
	public int weight(EventContext ctx) {
		return ctx.level().canSeeSky(ctx.player().blockPosition().above()) ? 8 : 0;
	}

	@Override
	public boolean fire(EventContext ctx) {
		ServerLevel level = ctx.level();
		RandomSource random = ctx.random();
		for (int i = 0; i < ATTEMPTS; i++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double distance = 10 + random.nextDouble() * 22;
			int x = Mth.floor(ctx.player().getX() + Math.cos(angle) * distance);
			int z = Mth.floor(ctx.player().getZ() + Math.sin(angle) * distance);
			BlockPos probe = new BlockPos(x, ctx.player().getBlockY(), z);
			if (!level.isLoaded(probe)) {
				continue;
			}
			BlockPos seed = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1, z);
			if (!isNaturalLeaf(level.getBlockState(seed))) {
				continue;
			}
			BlockPos trunk = findTrunkTop(level, seed);
			if (trunk == null) {
				continue;
			}
			List<BlockPos> leaves = collectCanopy(level, seed, trunk);
			if (leaves.isEmpty() || !strip(ctx, level, leaves)) {
				continue;
			}
			return true;
		}
		return false;
	}

	private boolean strip(EventContext ctx, ServerLevel level, List<BlockPos> leaves) {
		BoundingBox box = BoundingBox.encapsulatingPositions(leaves).orElseThrow();
		if (WorldGuard.isProtected(level, box)) {
			return false;
		}
		for (BlockPos pos : leaves) {
			if (WorldGuard.isWatched(level, pos)) {
				return false;
			}
		}
		WorldEditLog.Recorder edit = ctx.log().begin(level, id());
		for (BlockPos pos : leaves) {
			// Clients only: no neighbour updates, so neighbouring trees don't start to decay.
			edit.set(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
		return edit.commit();
	}

	private static boolean isNaturalLeaf(BlockState state) {
		return state.is(BlockTags.LEAVES) && state.hasProperty(LeavesBlock.PERSISTENT) && !state.getValue(LeavesBlock.PERSISTENT);
	}

	/** Nearest log under/around the canopy seed, followed up to the top of its trunk. */
	private static @Nullable BlockPos findTrunkTop(ServerLevel level, BlockPos seed) {
		for (int dy = 0; dy <= 8; dy++) {
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					BlockPos pos = seed.offset(dx, -dy, dz);
					if (level.getBlockState(pos).is(BlockTags.LOGS)) {
						BlockPos top = pos;
						while (level.getBlockState(top.above()).is(BlockTags.LOGS) && top.getY() < seed.getY() + 4) {
							top = top.above();
						}
						return top;
					}
				}
			}
		}
		return null;
	}

	/** Flood fill of connected natural leaves, kept within one tree's reach of its trunk. */
	private static List<BlockPos> collectCanopy(ServerLevel level, BlockPos seed, BlockPos trunk) {
		List<BlockPos> out = new ArrayList<>();
		Set<BlockPos> seen = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		queue.add(seed);
		seen.add(seed);
		while (!queue.isEmpty() && out.size() < MAX_LEAVES) {
			BlockPos pos = queue.poll();
			if (!isNaturalLeaf(level.getBlockState(pos))) {
				continue;
			}
			out.add(pos);
			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = -1; dy <= 1; dy++) {
					for (int dz = -1; dz <= 1; dz++) {
						BlockPos next = pos.offset(dx, dy, dz);
						if (Math.abs(next.getX() - trunk.getX()) > CANOPY_RADIUS || Math.abs(next.getZ() - trunk.getZ()) > CANOPY_RADIUS
								|| Math.abs(next.getY() - trunk.getY()) > CANOPY_RADIUS || !level.isLoaded(next) || !seen.add(next)) {
							continue;
						}
						queue.add(next);
					}
				}
			}
		}
		return out;
	}
}

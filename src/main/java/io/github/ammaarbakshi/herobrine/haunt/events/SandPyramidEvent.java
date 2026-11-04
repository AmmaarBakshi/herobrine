package io.github.ammaarbakshi.herobrine.haunt.events;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags;

import io.github.ammaarbakshi.herobrine.config.HerobrineConfig.Griefing;
import io.github.ammaarbakshi.herobrine.haunt.WorldEditLog;
import io.github.ammaarbakshi.herobrine.haunt.WorldGuard;
import io.github.ammaarbakshi.herobrine.registry.ModTags;

/**
 * A small stepped sand pyramid in a desert or a shallow ocean, 40-100 blocks away. In the
 * ocean it stands on a sand footing built up from the sea floor, so nothing ever falls.
 */
final class SandPyramidEvent implements HauntEvent {
	private static final int HALF = 3;          // 7x7 base
	private static final int LAYERS = HALF + 1; // 7, 5, 3, 1
	private static final int MAX_OCEAN_DEPTH = 5;
	private static final int ATTEMPTS = 16;

	@Override
	public String id() {
		return "sand_pyramid";
	}

	@Override
	public Griefing requiredGriefing() {
		return Griefing.NATURAL_ONLY;
	}

	@Override
	public int weight(EventContext ctx) {
		// Cheap guess: only worth trying when the player is somewhere sandy or by the sea.
		Holder<Biome> here = ctx.level().getBiome(ctx.player().blockPosition());
		return here.is(ConventionalBiomeTags.IS_DESERT) || here.is(ConventionalBiomeTags.IS_OCEAN)
				|| here.is(ConventionalBiomeTags.IS_BEACH) ? 6 : 0;
	}

	@Override
	public boolean fire(EventContext ctx) {
		ServerLevel level = ctx.level();
		RandomSource random = ctx.random();
		for (int i = 0; i < ATTEMPTS; i++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double distance = 40 + random.nextDouble() * 60;
			int x = Mth.floor(ctx.player().getX() + Math.cos(angle) * distance);
			int z = Mth.floor(ctx.player().getZ() + Math.sin(angle) * distance);
			if (tryBuild(ctx, level, x, z)) {
				return true;
			}
		}
		return false;
	}

	private boolean tryBuild(EventContext ctx, ServerLevel level, int x, int z) {
		if (!level.isLoaded(new BlockPos(x, level.getSeaLevel(), z))) {
			return false;
		}
		// Biomes vary with height (caves), so look at the surface: the water top in an ocean.
		Holder<Biome> biome = level.getBiome(new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z));
		int footingFrom;
		int pyramidAt;
		if (biome.is(ConventionalBiomeTags.IS_OCEAN)) {
			int floor = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z);
			int depth = level.getSeaLevel() - floor;
			if (depth <= 0 || depth > MAX_OCEAN_DEPTH) {
				return false;
			}
			footingFrom = floor;
			pyramidAt = level.getSeaLevel();
		} else if (biome.is(ConventionalBiomeTags.IS_DESERT)) {
			int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			if (!isFlatNaturalGround(level, x, top, z)) {
				return false;
			}
			footingFrom = top;
			pyramidAt = top;
		} else {
			return false;
		}

		BoundingBox box = new BoundingBox(x - HALF, footingFrom, z - HALF, x + HALF, pyramidAt + LAYERS, z + HALF);
		if (WorldGuard.isProtected(level, box) || anyCornerWatched(level, box)) {
			return false;
		}

		WorldEditLog.Recorder edit = ctx.log().begin(level, id());
		BlockState sand = Blocks.SAND.defaultBlockState();
		for (int y = footingFrom; y < pyramidAt; y++) {
			fillSquare(edit, level, x, y, z, HALF, sand);
		}
		for (int layer = 0; layer < LAYERS; layer++) {
			fillSquare(edit, level, x, pyramidAt + layer, z, HALF - layer, sand);
		}
		return edit.commit();
	}

	private static void fillSquare(WorldEditLog.Recorder edit, ServerLevel level, int cx, int y, int cz, int half, BlockState state) {
		for (int dx = -half; dx <= half; dx++) {
			for (int dz = -half; dz <= half; dz++) {
				BlockPos pos = new BlockPos(cx + dx, y, cz + dz);
				BlockState current = level.getBlockState(pos);
				// Only fill empty space (air, water, grass): never replace another block.
				if (current.canBeReplaced() && !current.getFluidState().is(FluidTags.LAVA)) {
					edit.set(pos, state, Block.UPDATE_ALL);
				}
			}
		}
	}

	/** The 7x7 base sits on natural ground with at most one block of unevenness. */
	private static boolean isFlatNaturalGround(ServerLevel level, int x, int top, int z) {
		for (int dx = -HALF; dx <= HALF; dx += HALF) {
			for (int dz = -HALF; dz <= HALF; dz += HALF) {
				int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + dx, z + dz);
				if (Math.abs(h - top) > 1 || !level.getBlockState(new BlockPos(x + dx, h - 1, z + dz)).is(ModTags.PYRAMID_GROUND)) {
					return false;
				}
			}
		}
		return true;
	}

	private static boolean anyCornerWatched(ServerLevel level, BoundingBox box) {
		int midX = (box.minX() + box.maxX()) / 2;
		int midZ = (box.minZ() + box.maxZ()) / 2;
		return WorldGuard.isWatched(level, new BlockPos(midX, box.maxY(), midZ))
				|| WorldGuard.isWatched(level, new BlockPos(box.minX(), box.minY() + 1, box.minZ()))
				|| WorldGuard.isWatched(level, new BlockPos(box.maxX(), box.minY() + 1, box.minZ()))
				|| WorldGuard.isWatched(level, new BlockPos(box.minX(), box.minY() + 1, box.maxZ()))
				|| WorldGuard.isWatched(level, new BlockPos(box.maxX(), box.minY() + 1, box.maxZ()));
	}
}

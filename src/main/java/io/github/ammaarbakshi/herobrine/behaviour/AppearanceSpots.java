package io.github.ammaarbakshi.herobrine.behaviour;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import io.github.ammaarbakshi.herobrine.memory.Place;
import io.github.ammaarbakshi.herobrine.registry.ModAttachments;
import io.github.ammaarbakshi.herobrine.sighting.LookDetector;
import io.github.ammaarbakshi.herobrine.sighting.SightingController;
import io.github.ammaarbakshi.herobrine.sighting.SpawnFinder;

/**
 * Where he chooses to stand. A bounded random search around the player (never loads chunks),
 * scored so that he tends to pick places with character: beside a tree trunk, on the far bank of
 * a river, on a ridge, near the player's home, deep in a dark cave. Each spot is tagged with that
 * {@code place:<flavour>} so a line can mention it.
 */
final class AppearanceSpots {
	/** A place to stand; {@code fadeIn} if it is at the edge of view and he should materialise. */
	record Spot(BlockPos feet, boolean fadeIn, String flavour) {
	}

	private static final int ATTEMPTS = 48;
	/** Share of observer appearances that fade in at the edge of the player's view. */
	private static final double EDGE_CHANCE = 0.3;
	private static final double HOME_RANGE = 64.0;

	private AppearanceSpots() {
	}

	static @Nullable Spot observer(ServerPlayer player) {
		ServerLevel level = player.level();
		RandomSource random = player.getRandom();
		boolean surface = level.canSeeSky(player.blockPosition().above());
		Place home = ModAttachments.memory(player).home();
		String dimension = level.dimension().identifier().toString();
		boolean homeNear = home != null && home.distanceSqr(dimension, player.getX(), player.getY(), player.getZ()) < HOME_RANGE * HOME_RANGE;

		Spot best = null;
		double bestScore = Double.NEGATIVE_INFINITY;
		for (int i = 0; i < ATTEMPTS; i++) {
			boolean edge = random.nextDouble() < EDGE_CHANCE;
			double side = random.nextBoolean() ? 1 : -1;
			double offset = edge ? (33 + random.nextDouble() * 14) * side : (70 + random.nextDouble() * 100) * side;
			double distance = surface ? 16 + random.nextDouble() * 26 : 10 + random.nextDouble() * 16;
			BlockPos feet = column(player, offset, distance, surface ? 10 : 8, surface);
			if (feet == null) {
				continue;
			}
			Vec3 eye = Vec3.atBottomCenterOf(feet).add(0, 1.62, 0);
			if (!LookDetector.hasLineOfSight(player, eye)) {
				continue;
			}
			double dot = LookDetector.viewDot(player, eye);
			if (edge ? dot < 0.5 || dot > 0.88 : dot > LookDetector.FOV_DOT) {
				continue;
			}
			double score = random.nextDouble();
			String flavour = surface ? "open" : "cave";
			double elevation = Mth.clamp(feet.getY() - player.getY(), 0, 12);
			score += elevation * 0.35;
			if (elevation >= 6) {
				flavour = "ridge";
			}
			if (!surface) {
				score += (15 - level.getMaxLocalRawBrightness(feet)) * 0.25;
			}
			if (besideTree(level, feet)) {
				score += 4;
				flavour = "tree";
			}
			if (surface && acrossWater(level, player, feet)) {
				score += 5;
				flavour = "river";
			}
			if (homeNear && home.distanceSqr(dimension, feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5) < 24 * 24) {
				score += 3;
				flavour = "home";
			}
			if (score > bestScore) {
				bestScore = score;
				best = new Spot(feet, edge, flavour);
			}
		}
		return best;
	}

	/** A dark spot 10-22 blocks away for the eyes, out of view or just at its edge. */
	static @Nullable Spot eyes(ServerPlayer player) {
		ServerLevel level = player.level();
		RandomSource random = player.getRandom();
		boolean surface = level.canSeeSky(player.blockPosition().above());
		Spot best = null;
		double bestScore = Double.NEGATIVE_INFINITY;
		for (int i = 0; i < ATTEMPTS; i++) {
			double side = random.nextBoolean() ? 1 : -1;
			double offset = (40 + random.nextDouble() * 130) * side;
			double distance = 10 + random.nextDouble() * 12;
			BlockPos feet = column(player, offset, distance, 8, surface);
			if (feet == null || !SightingController.isDarkForEyes(level, feet)) {
				continue;
			}
			Vec3 eye = Vec3.atBottomCenterOf(feet).add(0, 1.62, 0);
			if (!LookDetector.hasLineOfSight(player, eye) || LookDetector.viewDot(player, eye) > 0.8) {
				continue;
			}
			double score = (15 - level.getMaxLocalRawBrightness(feet.above())) + random.nextDouble() * 2;
			if (besideTree(level, feet)) {
				score += 2;
			}
			if (score > bestScore) {
				bestScore = score;
				best = new Spot(feet, false, surface ? (besideTree(level, feet) ? "tree" : "dark") : "cave");
			}
		}
		return best;
	}

	private static @Nullable BlockPos column(ServerPlayer player, double yawOffset, double distance, int range, boolean surface) {
		double angle = Math.toRadians(player.getYRot() + yawOffset);
		int x = Mth.floor(player.getX() - Math.sin(angle) * distance);
		int z = Mth.floor(player.getZ() + Math.cos(angle) * distance);
		return SpawnFinder.findStandable(player.level(), x, z, player.getBlockY(), range, surface);
	}

	/** A log right beside him at body height: he stands at a trunk, half in its cover. */
	private static boolean besideTree(ServerLevel level, BlockPos feet) {
		for (int dy = 0; dy <= 1; dy++) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					if ((dx != 0 || dz != 0) && level.getBlockState(feet.offset(dx, dy, dz)).is(BlockTags.LOGS)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/** At least two water columns between the player and the spot: he is on the other bank. */
	private static boolean acrossWater(ServerLevel level, ServerPlayer player, BlockPos feet) {
		int water = 0;
		for (int step = 1; step <= 7; step++) {
			double t = step / 8.0;
			int x = Mth.floor(Mth.lerp(t, player.getX(), feet.getX() + 0.5));
			int z = Mth.floor(Mth.lerp(t, player.getZ(), feet.getZ() + 0.5));
			if (!level.hasChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z))) {
				return false;
			}
			int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
			if (level.getFluidState(new BlockPos(x, top - 1, z)).is(FluidTags.WATER) && ++water >= 2) {
				return true;
			}
		}
		return false;
	}
}

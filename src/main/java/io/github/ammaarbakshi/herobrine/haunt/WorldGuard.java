package io.github.ammaarbakshi.herobrine.haunt;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

import io.github.ammaarbakshi.herobrine.config.HerobrineConfig;
import io.github.ammaarbakshi.herobrine.config.HerobrineConfig.Griefing;
import io.github.ammaarbakshi.herobrine.sighting.LookDetector;

/**
 * The world-edit safety rules, in one place:
 * <ul>
 *   <li>the {@code griefing} config level must allow the kind of edit;</li>
 *   <li>nothing within {@code protectedRadius} of a bed or any block entity (chests,
 *       furnaces, signs...). Beds have no block entity any more, so they are found through the
 *       {@code home} point-of-interest index, which villagers use to find beds;</li>
 *   <li>nothing any player can currently see: changes happen while nobody is looking.</li>
 * </ul>
 * Natural-block checks are per event (via block tags), since "natural" means different things
 * for a tunnel and a tree.
 */
public final class WorldGuard {
	/** Players farther than this can't meaningfully see a single block change. */
	private static final double WATCH_RANGE = 160.0;

	private WorldGuard() {
	}

	public static boolean allows(Griefing required) {
		HerobrineConfig config = HerobrineConfig.get();
		return config.enabled && config.griefing.ordinal() >= required.ordinal();
	}

	/**
	 * True if any bed or block entity lies within the protected radius of {@code area}.
	 * Unloaded chunks count as protected: we can't prove they are safe.
	 */
	public static boolean isProtected(ServerLevel level, BoundingBox area) {
		int r = HerobrineConfig.get().protectedRadius;
		if (r <= 0) {
			return false;
		}
		BoundingBox zone = area.inflatedBy(r);
		int minCx = SectionPos.blockToSectionCoord(zone.minX());
		int maxCx = SectionPos.blockToSectionCoord(zone.maxX());
		int minCz = SectionPos.blockToSectionCoord(zone.minZ());
		int maxCz = SectionPos.blockToSectionCoord(zone.maxZ());
		for (int cx = minCx; cx <= maxCx; cx++) {
			for (int cz = minCz; cz <= maxCz; cz++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
				if (chunk == null) {
					return true;
				}
				for (BlockPos pos : chunk.getBlockEntities().keySet()) {
					if (zone.isInside(pos)) {
						return true;
					}
				}
			}
		}
		// Every chunk is loaded at this point, so the lookup never touches the disk.
		int radius = Math.max(zone.getXSpan(), zone.getZSpan()) / 2 + 1;
		return level.getPoiManager()
				.getInSquare(type -> type.is(PoiTypes.HOME), zone.getCenter(), radius, PoiManager.Occupancy.ANY)
				.anyMatch(poi -> zone.isInside(poi.getPos()));
	}

	public static boolean isProtected(ServerLevel level, BlockPos pos) {
		return isProtected(level, new BoundingBox(pos));
	}

	/** True if any player in range has this block on screen with a clear line of sight. */
	public static boolean isWatched(ServerLevel level, BlockPos pos) {
		Vec3 center = Vec3.atCenterOf(pos);
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || player.distanceToSqr(center) > WATCH_RANGE * WATCH_RANGE) {
				continue;
			}
			if (LookDetector.canSee(player, center)) {
				return true;
			}
		}
		return false;
	}
}

package io.github.ammaarbakshi.herobrine.sighting;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import io.github.ammaarbakshi.herobrine.entity.HerobrineEntity;
import io.github.ammaarbakshi.herobrine.entity.HerobrineEntity.Mode;
import io.github.ammaarbakshi.herobrine.haunt.HauntLevel;
import io.github.ammaarbakshi.herobrine.haunt.PrivateSounds;
import io.github.ammaarbakshi.herobrine.registry.ModAttachments;
import io.github.ammaarbakshi.herobrine.registry.ModEntities;
import io.github.ammaarbakshi.herobrine.registry.ModSounds;

/**
 * Owns every non-combat appearance. At most one per player; only that player's view and
 * position drive the vanish rules. Ticks only the active apparitions, never the world.
 */
public final class SightingController {
	/** Per-tick behaviour for scripted appearances (ghost replay). Returns false when finished. */
	@FunctionalInterface
	public interface Script {
		boolean tick(HerobrineEntity herobrine, ServerPlayer player);
	}

	private static final int DISTANT_MIN = 24;
	private static final int DISTANT_MAX = 60;
	private static final int DISTANT_VANISH_RANGE = 16;
	private static final int DISTANT_MAX_AGE = 20 * 20;
	private static final int STALK_MAX_AGE = 15 * 20;
	private static final int JUMPSCARE_TICKS = 10;
	/** Looking slightly before he is on screen makes him vanish: the turn reveals nothing. */
	private static final double STALK_VANISH_DOT = 0.25;
	private static final double LOOK_REWARD = 5.0;
	private static final int SEARCH_ATTEMPTS = 40;

	private static final Map<UUID, Active> ACTIVE = new HashMap<>();

	private SightingController() {
	}

	private static final class Active {
		final HerobrineEntity entity;
		final Mode mode;
		final int stareTicksNeeded;
		final int maxAge;
		final @Nullable Script script;
		int stareTicks;
		boolean rewarded;

		Active(HerobrineEntity entity, Mode mode, int stareTicksNeeded, int maxAge, @Nullable Script script) {
			this.entity = entity;
			this.mode = mode;
			this.stareTicksNeeded = stareTicksNeeded;
			this.maxAge = maxAge;
			this.script = script;
		}
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(SightingController::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> clear());
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> end(handler.player.getUUID()));
	}

	public static boolean isActive(ServerPlayer player) {
		return ACTIVE.containsKey(player.getUUID());
	}

	public static @Nullable HerobrineEntity activeEntity(ServerPlayer player) {
		Active active = ACTIVE.get(player.getUUID());
		return active == null ? null : active.entity;
	}

	public static void end(UUID playerId) {
		Active active = ACTIVE.remove(playerId);
		if (active != null && !active.entity.isRemoved()) {
			active.entity.vanish();
		}
	}

	private static void clear() {
		ACTIVE.values().forEach(a -> a.entity.vanish());
		ACTIVE.clear();
	}

	// --- Spawning ----------------------------------------------------------------------------

	/** Far away, at the edge of view, in line of sight: there when they turn their head. */
	public static boolean spawnDistant(ServerPlayer player) {
		if (isActive(player)) {
			return false;
		}
		BlockPos best = findHiddenSpot(player, DISTANT_MIN, DISTANT_MAX);
		if (best == null) {
			return false;
		}
		int stare = 10 + player.getRandom().nextInt(21); // 0.5 - 1.5 s
		return spawn(player, best, Mode.SIGHTING, stare, DISTANT_MAX_AGE, null);
	}

	/**
	 * A standable spot {@code minDistance..maxDistance} away, just outside the player's view
	 * (70-160 degrees off where they look) but in clear line of sight, so turning their head
	 * reveals it. Prefers high ground and darkness: ridgelines, cave mouths.
	 */
	public static @Nullable BlockPos findHiddenSpot(ServerPlayer player, double minDistance, double maxDistance) {
		ServerLevel level = player.level();
		RandomSource random = player.getRandom();
		boolean surface = level.canSeeSky(player.blockPosition().above());
		BlockPos best = null;
		double bestScore = Double.NEGATIVE_INFINITY;
		for (int i = 0; i < SEARCH_ATTEMPTS; i++) {
			double offset = (70 + random.nextDouble() * 90) * (random.nextBoolean() ? 1 : -1);
			double distance = minDistance + random.nextDouble() * (maxDistance - minDistance);
			BlockPos feet = columnAt(player, offset, distance, surface ? 12 : 8, surface);
			if (feet == null) {
				continue;
			}
			Vec3 eye = Vec3.atBottomCenterOf(feet).add(0, 1.62, 0);
			if (LookDetector.inFieldOfView(player, eye) || !LookDetector.hasLineOfSight(player, eye)) {
				continue;
			}
			double elevation = Mth.clamp(feet.getY() - player.getY(), 0, 10);
			double darkness = 15 - level.getMaxLocalRawBrightness(feet);
			double score = elevation * 0.6 + darkness * 0.3 + random.nextDouble();
			if (score > bestScore) {
				bestScore = score;
				best = feet;
			}
		}
		return best;
	}

	/** Close behind the player, outside their view; gone the moment they turn around. */
	public static boolean spawnStalker(ServerPlayer player) {
		if (isActive(player)) {
			return false;
		}
		RandomSource random = player.getRandom();
		for (int i = 0; i < SEARCH_ATTEMPTS; i++) {
			double offset = 180 + (random.nextDouble() - 0.5) * 70;
			double distance = 6 + random.nextDouble() * 4;
			BlockPos feet = columnAt(player, offset, distance, 3, false);
			if (feet == null) {
				continue;
			}
			Vec3 eye = Vec3.atBottomCenterOf(feet).add(0, 1.62, 0);
			if (LookDetector.viewDot(player, eye) > 0.0 || !LookDetector.hasLineOfSight(player, eye)) {
				continue;
			}
			if (spawn(player, feet, Mode.STALKING, 0, STALK_MAX_AGE, null)) {
				// One footstep right where he stands: the cue to turn around.
				PrivateSounds.play(player, SoundEvents.STONE_STEP, SoundSource.HOSTILE, eye, 0.6F, 0.9F);
				return true;
			}
		}
		return false;
	}

	/** Two blocks in front of their face for half a second, with a sting. */
	public static boolean spawnJumpscare(ServerPlayer player) {
		if (isActive(player)) {
			return false;
		}
		Vec3 look = player.getViewVector(1.0F);
		Vec3 flat = new Vec3(look.x, 0, look.z);
		if (flat.lengthSqr() < 1.0E-4) {
			return false;
		}
		Vec3 at = player.position().add(flat.normalize().scale(2.0));
		BlockPos feet = SpawnFinder.findStandable(player.level(), Mth.floor(at.x), Mth.floor(at.z), player.getBlockY(), 2, false);
		if (feet == null || !spawn(player, feet, Mode.JUMPSCARE, 0, JUMPSCARE_TICKS, null)) {
			return false;
		}
		PrivateSounds.play(player, ModSounds.JUMPSCARE, SoundSource.HOSTILE, player.getEyePosition(), 1.0F, 1.0F);
		return true;
	}

	/** Spawns a scripted apparition (e.g. ghost replay) that the controller ticks and cleans up. */
	public static @Nullable HerobrineEntity spawnScripted(ServerPlayer player, Vec3 at, float yaw, int maxAge, Script script) {
		if (isActive(player)) {
			return null;
		}
		HerobrineEntity entity = create(player, Mode.GHOST, at, yaw);
		if (entity == null) {
			return null;
		}
		ACTIVE.put(player.getUUID(), new Active(entity, Mode.GHOST, 0, maxAge, script));
		return entity;
	}

	private static @Nullable BlockPos columnAt(ServerPlayer player, double yawOffset, double distance, int range, boolean surface) {
		double angle = Math.toRadians(player.getYRot() + yawOffset);
		double x = player.getX() - Math.sin(angle) * distance;
		double z = player.getZ() + Math.cos(angle) * distance;
		return SpawnFinder.findStandable(player.level(), Mth.floor(x), Mth.floor(z), player.getBlockY(), range, surface);
	}

	private static boolean spawn(ServerPlayer player, BlockPos feet, Mode mode, int stareTicks, int maxAge, @Nullable Script script) {
		Vec3 at = Vec3.atBottomCenterOf(feet);
		float yaw = yawToward(at, player.position());
		HerobrineEntity entity = create(player, mode, at, yaw);
		if (entity == null) {
			return false;
		}
		ACTIVE.put(player.getUUID(), new Active(entity, mode, stareTicks, maxAge, script));
		ModAttachments.haunt(player).recordSighting();
		return true;
	}

	private static @Nullable HerobrineEntity create(ServerPlayer player, Mode mode, Vec3 at, float yaw) {
		ServerLevel level = player.level();
		HerobrineEntity entity = ModEntities.HEROBRINE.create(level, EntitySpawnReason.EVENT);
		if (entity == null) {
			return null;
		}
		entity.setMode(mode);
		entity.setWatchedPlayer(player);
		entity.snapTo(at.x, at.y, at.z, yaw, 0.0F);
		entity.setYHeadRot(yaw);
		entity.setYBodyRot(yaw);
		if (!level.addFreshEntity(entity)) {
			return null;
		}
		return entity;
	}

	public static float yawToward(Vec3 from, Vec3 to) {
		return (float) (Mth.atan2(to.z - from.z, to.x - from.x) * Mth.RAD_TO_DEG) - 90.0F;
	}

	// --- Ticking -----------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (ACTIVE.isEmpty()) {
			return;
		}
		Iterator<Map.Entry<UUID, Active>> it = ACTIVE.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Active> entry = it.next();
			Active active = entry.getValue();
			HerobrineEntity entity = active.entity;
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (entity.isRemoved() || player == null || player.level() != entity.level() || !player.isAlive()
					|| entity.ageInMode() > active.maxAge || !shouldStay(active, entity, player)) {
				entity.vanish();
				it.remove();
			}
		}
	}

	private static boolean shouldStay(Active active, HerobrineEntity entity, ServerPlayer player) {
		return switch (active.mode) {
			case SIGHTING -> tickDistant(active, entity, player);
			case STALKING -> entity.ageInMode() < 2 || (entity.distanceToSqr(player) < 16 * 16
					&& LookDetector.viewDot(player, entity.getEyePosition()) < STALK_VANISH_DOT);
			case JUMPSCARE -> entity.ageInMode() < JUMPSCARE_TICKS;
			case GHOST -> active.script == null || active.script.tick(entity, player);
			default -> false;
		};
	}

	private static boolean tickDistant(Active active, HerobrineEntity entity, ServerPlayer player) {
		if (entity.distanceToSqr(player) < DISTANT_VANISH_RANGE * DISTANT_VANISH_RANGE) {
			return false;
		}
		if (LookDetector.isStaringAt(player, entity)) {
			if (!active.rewarded) {
				active.rewarded = true;
				HauntLevel.gain(player, LOOK_REWARD);
			}
			return ++active.stareTicks < active.stareTicksNeeded;
		}
		active.stareTicks = 0;
		return true;
	}
}

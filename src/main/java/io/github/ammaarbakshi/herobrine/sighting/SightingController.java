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
import io.github.ammaarbakshi.herobrine.perf.Perf;
import io.github.ammaarbakshi.herobrine.registry.ModAttachments;
import io.github.ammaarbakshi.herobrine.registry.ModEntities;
import io.github.ammaarbakshi.herobrine.registry.ModSounds;

/**
 * Owns every non-combat appearance. At most one per player; only that player's view and
 * position drive the vanish rules. Ticks only the active apparitions, never the world.
 *
 * <p>It reports each apparition's life to a {@link Listener} (the appearance controller): when he
 * appears, the moment the player first notices him, and how it ended. That is how the memory
 * learns whether a player stares him down, walks up to him, looks away or never sees him at all.
 */
public final class SightingController {
	/** Per-tick behaviour for scripted appearances (ghost replay). Returns false when finished. */
	@FunctionalInterface
	public interface Script {
		boolean tick(HerobrineEntity herobrine, ServerPlayer player);
	}

	/** How an appearance ended. */
	public enum Outcome {
		/** Looked at until he left. */
		STARED_DOWN,
		/** The player walked up to him. */
		APPROACHED,
		/** Seen, then the player looked away and he was gone. */
		LOOKED_AWAY,
		/** The player turned around to face him (he was behind them). */
		TURNED_AROUND,
		/** He waited and left; {@code seen} says whether they ever noticed. */
		TIMED_OUT,
		/** Anything else: a relog, a death, a fight, a light placed next to his eyes. */
		OTHER
	}

	/** Told about each apparition's life. Calls happen on the server thread. */
	public interface Listener {
		void appeared(ServerPlayer player, HerobrineEntity herobrine);

		void noticed(ServerPlayer player, HerobrineEntity herobrine);

		void ended(ServerPlayer player, Mode mode, boolean seen, Outcome outcome);
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

	/** Observer: unnoticed, he waits this long; noticed, he lets himself be watched this long. */
	private static final int OBSERVER_MAX_AGE = 45 * 20;
	private static final int OBSERVER_WATCHED_TICKS = 12 * 20;
	/** Ticks out of view, once seen, before he is gone. */
	private static final int OBSERVER_LOOK_AWAY_TICKS = 10;
	private static final double OBSERVER_VANISH_RANGE = 10.0;
	/** cos(~32 degrees): roughly where the player is looking, close enough to notice a figure. */
	private static final double NOTICE_DOT = 0.85;
	private static final int NOTICE_TICKS = 8;

	private static final int EYES_MAX_AGE = 30 * 20;
	private static final double EYES_VANISH_RANGE = 7.0;
	/** Light at his head above this and the eyes go out: a torch scares him off. */
	private static final int EYES_MAX_LIGHT = 7;

	private static final Map<UUID, Active> ACTIVE = new HashMap<>();
	private static Listener listener = new Listener() {
		@Override
		public void appeared(ServerPlayer player, HerobrineEntity herobrine) {
		}

		@Override
		public void noticed(ServerPlayer player, HerobrineEntity herobrine) {
		}

		@Override
		public void ended(ServerPlayer player, Mode mode, boolean seen, Outcome outcome) {
		}
	};

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
		/** Ticks the player has had him in the middle of their view. */
		int noticeTicks;
		boolean seen;
		int seenAtAge = -1;
		int outOfViewTicks;
		/** Age until which he stays put because he is speaking. */
		int holdUntil = -1;
		Outcome outcome = Outcome.OTHER;

		Active(HerobrineEntity entity, Mode mode, int stareTicksNeeded, int maxAge, @Nullable Script script) {
			this.entity = entity;
			this.mode = mode;
			this.stareTicksNeeded = stareTicksNeeded;
			this.maxAge = maxAge;
			this.script = script;
		}

		boolean held() {
			return entity.ageInMode() < holdUntil;
		}
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(Perf.timed(Perf.WORLD, SightingController::tick));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> clear());
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> end(handler.player.getUUID()));
	}

	public static void setListener(Listener newListener) {
		listener = newListener;
	}

	public static boolean isActive(ServerPlayer player) {
		return ACTIVE.containsKey(player.getUUID());
	}

	public static @Nullable HerobrineEntity activeEntity(ServerPlayer player) {
		Active active = ACTIVE.get(player.getUUID());
		return active == null ? null : active.entity;
	}

	/** Ends the player's apparition, if any, without telling anyone how (a fight is starting). */
	public static void end(UUID playerId) {
		Active active = ACTIVE.remove(playerId);
		if (active != null && !active.entity.isRemoved()) {
			active.entity.vanish();
		}
	}

	/** Keeps the player's apparition where it is for {@code ticks} more: he is talking. */
	public static void holdFor(ServerPlayer player, int ticks) {
		Active active = ACTIVE.get(player.getUUID());
		if (active != null && (active.mode == Mode.OBSERVER || active.mode == Mode.EYES)) {
			active.holdUntil = Math.max(active.holdUntil, active.entity.ageInMode() + ticks);
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
		return spawn(player, best, Mode.SIGHTING, stare, DISTANT_MAX_AGE, null, false);
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
			if (spawn(player, feet, Mode.STALKING, 0, STALK_MAX_AGE, null, false)) {
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
		if (feet == null || !spawn(player, feet, Mode.JUMPSCARE, 0, JUMPSCARE_TICKS, null, false)) {
			return false;
		}
		PrivateSounds.play(player, ModSounds.JUMPSCARE, SoundSource.HOSTILE, player.getEyePosition(), 1.0F, 1.0F);
		return true;
	}

	/**
	 * The Living Observer at {@code feet}: he stands and watches. {@code fadeIn} makes him
	 * materialise (for spots at the edge of the player's view) instead of simply being there.
	 */
	public static boolean spawnObserver(ServerPlayer player, BlockPos feet, boolean fadeIn) {
		return !isActive(player) && spawn(player, feet, Mode.OBSERVER, 0, OBSERVER_MAX_AGE, null, fadeIn);
	}

	/** Only his eyes, at {@code feet}, which must be dark. */
	public static boolean spawnEyes(ServerPlayer player, BlockPos feet) {
		if (isActive(player)) {
			return false;
		}
		int stare = 40 + player.getRandom().nextInt(31); // 2 - 3.5 s
		return spawn(player, feet, Mode.EYES, stare, EYES_MAX_AGE, null, false);
	}

	/** True if a spot is dark enough for the eyes: no torch, no daylight. */
	public static boolean isDarkForEyes(ServerLevel level, BlockPos feet) {
		return level.getMaxLocalRawBrightness(feet.above()) <= EYES_MAX_LIGHT - 3;
	}

	/** Spawns a scripted apparition (e.g. ghost replay) that the controller ticks and cleans up. */
	public static @Nullable HerobrineEntity spawnScripted(ServerPlayer player, Vec3 at, float yaw, int maxAge, Script script) {
		if (isActive(player)) {
			return null;
		}
		HerobrineEntity entity = create(player, Mode.GHOST, at, yaw, false);
		if (entity == null) {
			return null;
		}
		ACTIVE.put(player.getUUID(), new Active(entity, Mode.GHOST, 0, maxAge, script));
		listener.appeared(player, entity);
		return entity;
	}

	private static @Nullable BlockPos columnAt(ServerPlayer player, double yawOffset, double distance, int range, boolean surface) {
		double angle = Math.toRadians(player.getYRot() + yawOffset);
		double x = player.getX() - Math.sin(angle) * distance;
		double z = player.getZ() + Math.cos(angle) * distance;
		return SpawnFinder.findStandable(player.level(), Mth.floor(x), Mth.floor(z), player.getBlockY(), range, surface);
	}

	private static boolean spawn(ServerPlayer player, BlockPos feet, Mode mode, int stareTicks, int maxAge, @Nullable Script script,
			boolean fadeIn) {
		Vec3 at = Vec3.atBottomCenterOf(feet);
		float yaw = yawToward(at, player.position());
		HerobrineEntity entity = create(player, mode, at, yaw, fadeIn);
		if (entity == null) {
			return false;
		}
		Active active = new Active(entity, mode, stareTicks, maxAge, script);
		ACTIVE.put(player.getUUID(), active);
		ModAttachments.haunt(player).recordSighting();
		listener.appeared(player, entity);
		if (mode == Mode.JUMPSCARE) {
			// Two blocks from their face: there is no not noticing.
			markSeen(active, player);
		}
		return true;
	}

	private static @Nullable HerobrineEntity create(ServerPlayer player, Mode mode, Vec3 at, float yaw, boolean fadeIn) {
		ServerLevel level = player.level();
		HerobrineEntity entity = ModEntities.HEROBRINE.create(level, EntitySpawnReason.EVENT);
		if (entity == null) {
			return null;
		}
		entity.setMode(mode);
		entity.setWatchedPlayer(player);
		if (fadeIn) {
			entity.fadeIn();
		}
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
			if (entity.isRemoved() && entity.isLeaving()) {
				// He finished fading out on his own.
				it.remove();
				if (player != null) {
					listener.ended(player, active.mode, active.seen, active.outcome);
				}
				continue;
			}
			if (entity.isLeaving()) {
				continue;
			}
			boolean stay;
			if (entity.isRemoved() || player == null || player.level() != entity.level() || !player.isAlive()) {
				active.outcome = Outcome.OTHER;
				stay = false;
			} else if (entity.ageInMode() > active.maxAge && !active.held()) {
				active.outcome = active.seen && active.mode == Mode.OBSERVER ? Outcome.STARED_DOWN : Outcome.TIMED_OUT;
				stay = false;
			} else {
				stay = shouldStay(active, entity, player);
			}
			if (!stay) {
				it.remove();
				boolean inView = player != null && player.level() == entity.level() && LookDetector.canSeeEntity(player, entity);
				if (inView && !entity.isRemoved() && active.mode == Mode.OBSERVER) {
					// He leaves in plain view: let it be seen, rather than a pop.
					entity.fadeAway();
				} else {
					entity.vanish();
				}
				if (player != null) {
					listener.ended(player, active.mode, active.seen, active.outcome);
				}
			}
		}
	}

	private static boolean shouldStay(Active active, HerobrineEntity entity, ServerPlayer player) {
		return switch (active.mode) {
			case SIGHTING -> tickDistant(active, entity, player);
			case STALKING -> tickStalker(active, entity, player);
			case JUMPSCARE -> entity.ageInMode() < JUMPSCARE_TICKS;
			case GHOST -> tickGhost(active, entity, player);
			case OBSERVER -> tickObserver(active, entity, player);
			case EYES -> tickEyes(active, entity, player);
			default -> false;
		};
	}

	private static boolean tickDistant(Active active, HerobrineEntity entity, ServerPlayer player) {
		if (entity.distanceToSqr(player) < DISTANT_VANISH_RANGE * DISTANT_VANISH_RANGE) {
			active.outcome = Outcome.APPROACHED;
			return false;
		}
		if (LookDetector.isStaringAt(player, entity)) {
			if (!active.rewarded) {
				active.rewarded = true;
				HauntLevel.gain(player, LOOK_REWARD);
				markSeen(active, player);
			}
			if (++active.stareTicks >= active.stareTicksNeeded) {
				active.outcome = Outcome.STARED_DOWN;
				return false;
			}
			return true;
		}
		active.stareTicks = 0;
		return true;
	}

	private static boolean tickStalker(Active active, HerobrineEntity entity, ServerPlayer player) {
		if (entity.ageInMode() < 2) {
			return true;
		}
		if (entity.distanceToSqr(player) >= 16 * 16) {
			active.outcome = Outcome.OTHER;
			return false;
		}
		if (LookDetector.viewDot(player, entity.getEyePosition()) >= STALK_VANISH_DOT) {
			markSeen(active, player);
			active.outcome = Outcome.TURNED_AROUND;
			return false;
		}
		return true;
	}

	private static boolean tickGhost(Active active, HerobrineEntity entity, ServerPlayer player) {
		if (!active.seen && entity.ageInMode() % 4 == 0 && LookDetector.canSeeEntity(player, entity)) {
			markSeen(active, player);
		}
		return active.script == null || active.script.tick(entity, player);
	}

	/**
	 * Stands still and watches. Unnoticed, he waits; noticed, he lets himself be watched for a
	 * while (longer if he is speaking), and the moment they look away he is gone. Walking up to him
	 * makes him fade.
	 */
	private static boolean tickObserver(Active active, HerobrineEntity entity, ServerPlayer player) {
		if (entity.distanceToSqr(player) < OBSERVER_VANISH_RANGE * OBSERVER_VANISH_RANGE) {
			active.outcome = Outcome.APPROACHED;
			return false;
		}
		boolean onScreen = LookDetector.canSeeEntity(player, entity);
		if (!active.seen) {
			if (onScreen && LookDetector.viewDot(player, entity.getEyePosition()) > NOTICE_DOT) {
				if (++active.noticeTicks >= NOTICE_TICKS) {
					markSeen(active, player);
				}
			}
			return true;
		}
		if (onScreen) {
			active.outOfViewTicks = 0;
			if (entity.ageInMode() - active.seenAtAge > OBSERVER_WATCHED_TICKS && !active.held()) {
				active.outcome = Outcome.STARED_DOWN;
				return false;
			}
			return true;
		}
		if (++active.outOfViewTicks >= OBSERVER_LOOK_AWAY_TICKS) {
			active.outcome = Outcome.LOOKED_AWAY;
			return false;
		}
		return true;
	}

	/** Two points of light in the dark. Approach, stare too long, or light the place up: gone. */
	private static boolean tickEyes(Active active, HerobrineEntity entity, ServerPlayer player) {
		if (entity.distanceToSqr(player) < EYES_VANISH_RANGE * EYES_VANISH_RANGE) {
			active.outcome = Outcome.APPROACHED;
			return false;
		}
		if (entity.ageInMode() % 10 == 0
				&& entity.level().getMaxLocalRawBrightness(entity.blockPosition().above()) > EYES_MAX_LIGHT) {
			active.outcome = Outcome.OTHER;
			return false;
		}
		if (LookDetector.isStaringAt(player, entity)) {
			if (!active.seen && ++active.noticeTicks >= NOTICE_TICKS / 2) {
				markSeen(active, player);
			}
			if (active.seen && ++active.stareTicks >= active.stareTicksNeeded && !active.held()) {
				active.outcome = Outcome.STARED_DOWN;
				return false;
			}
		}
		return true;
	}

	private static void markSeen(Active active, ServerPlayer player) {
		if (active.seen) {
			return;
		}
		active.seen = true;
		active.seenAtAge = active.entity.ageInMode();
		listener.noticed(player, active.entity);
	}
}

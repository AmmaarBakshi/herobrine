package io.github.ammaarbakshi.herobrine.encounter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.random.RandomGenerator;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import io.github.ammaarbakshi.herobrine.HerobrineMod;
import io.github.ammaarbakshi.herobrine.config.HerobrineConfig;
import io.github.ammaarbakshi.herobrine.entity.HerobrineEntity.Mode;
import io.github.ammaarbakshi.herobrine.entity.HerobrineEntity;
import io.github.ammaarbakshi.herobrine.entity.ai.MirrorBrain;
import io.github.ammaarbakshi.herobrine.haunt.HauntLevel;
import io.github.ammaarbakshi.herobrine.haunt.HauntState;
import io.github.ammaarbakshi.herobrine.haunt.PrivateSounds;
import io.github.ammaarbakshi.herobrine.mirror.CounterPlan;
import io.github.ammaarbakshi.herobrine.mirror.Habit;
import io.github.ammaarbakshi.herobrine.mirror.ProfileAnalyzer;
import io.github.ammaarbakshi.herobrine.mirror.Taunts;
import io.github.ammaarbakshi.herobrine.net.ConfrontationCuePayload;
import io.github.ammaarbakshi.herobrine.net.ModNetworking;
import io.github.ammaarbakshi.herobrine.perf.Perf;
import io.github.ammaarbakshi.herobrine.registry.ModAttachments;
import io.github.ammaarbakshi.herobrine.registry.ModEntities;
import io.github.ammaarbakshi.herobrine.registry.ModItems;
import io.github.ammaarbakshi.herobrine.registry.ModSounds;
import io.github.ammaarbakshi.herobrine.sighting.SightingController;
import io.github.ammaarbakshi.herobrine.sighting.SpawnFinder;

/**
 * Runs Mirror fights, at most one per player: the arrival (silence, darkness, lightning), the
 * fight itself (following a player who runs), and the endings. A win drops the Mirror Shard
 * and grants "Know Thyself"; a loss sends him to stand over the respawn point. Either way the
 * fight goes into the player's {@link EncounterHistory} and he will come back.
 *
 * <p>Fights that end any other way (logout, dimension change, Peaceful, a removed entity) are
 * abandoned: no record, no reward, nothing changes.
 */
public final class ConfrontationManager {
	public enum Trigger {
		NATURAL,
		SHRINE,
		COMMAND
	}

	public enum StartResult {
		STARTED,
		ALREADY_FIGHTING,
		PEACEFUL,
		DISABLED,
		NO_ROOM
	}

	public static final double ARRIVAL_DISTANCE = 10.0;
	public static final double FLEE_DISTANCE = 64.0;
	public static final double LOSS_HAUNT_LEVEL = 70.0;
	public static final double WIN_HAUNT_LEVEL = 40.0;
	/** Per-second chance once eligible, times {@code hauntSpeedMultiplier}: about 20 s at 1x. */
	public static final double NATURAL_CHANCE_PER_SECOND = 0.05;
	public static final Identifier KNOW_THYSELF = HerobrineMod.id("know_thyself");
	public static final String KNOW_THYSELF_CRITERION = "beat_the_mirror";

	private static final int LIGHTNING_TICK = 30;
	private static final int ARRIVAL_TICK = 34;
	private static final int DARKNESS_TICKS = 100;
	private static final int FLEE_CHECK_INTERVAL = 10;
	private static final int AFTERMATH_DELAY = 10;
	private static final int AFTERMATH_SPEAK_TICK = 20;
	private static final int AFTERMATH_TICKS = 80;
	private static final int AFTERMATH_LINES = 5;

	private static final class Fight {
		final UUID playerId;
		final Trigger trigger;
		final CounterPlan plan;
		@Nullable BlockPos arrival;
		@Nullable HerobrineEntity boss;
		boolean arrived;
		int ticks;
		int fightTicks;
		int nextTaunt = Taunts.FIRST_DELAY;
		String lastTaunt = "";
		int taunts;

		Fight(UUID playerId, Trigger trigger, CounterPlan plan) {
			this.playerId = playerId;
			this.trigger = trigger;
			this.plan = plan;
		}
	}

	private static final class Aftermath {
		@Nullable HerobrineEntity entity;
		int ticks;
	}

	private static final Map<UUID, Fight> FIGHTS = new HashMap<>();
	private static final Map<UUID, Aftermath> AFTERMATHS = new HashMap<>();
	/** Boss entities that reloaded after their fight moved on; discarded on the next tick. */
	private static final List<HerobrineEntity> STALE = new ArrayList<>();

	private ConfrontationManager() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(Perf.timed(Perf.WORLD, ConfrontationManager::tick));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				onPlayerDeath(player);
			} else if (entity instanceof HerobrineEntity herobrine && herobrine.mode() == Mode.BOSS) {
				onBossDeath(herobrine);
			}
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			Aftermath aftermath = AFTERMATHS.get(newPlayer.getUUID());
			if (aftermath != null) {
				aftermath.ticks = 0;
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID id = handler.getPlayer().getUUID();
			Fight fight = FIGHTS.get(id);
			if (fight != null) {
				abandon(fight);
			}
			Aftermath aftermath = AFTERMATHS.remove(id);
			if (aftermath != null && aftermath.entity != null) {
				aftermath.entity.vanish();
			}
		});
		ServerEntityEvents.ENTITY_LOAD.register(ConfrontationManager::onEntityLoad);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			FIGHTS.clear();
			AFTERMATHS.clear();
			STALE.clear();
		});
	}

	// --- Queries ------------------------------------------------------------------------------

	public static boolean isFighting(ServerPlayer player) {
		return FIGHTS.containsKey(player.getUUID());
	}

	/** Lines he has said in the current fight (0 when there is none). */
	public static int tauntsSent(ServerPlayer player) {
		Fight fight = FIGHTS.get(player.getUUID());
		return fight == null ? 0 : fight.taunts;
	}

	/** The counters he would bring if a fight started now. */
	public static CounterPlan nextPlan(ServerPlayer player) {
		return planFor(player, HerobrineConfig.get());
	}

	/** The player's live boss, if a fight is on and he has arrived. */
	public static @Nullable HerobrineEntity activeBoss(ServerPlayer player) {
		Fight fight = FIGHTS.get(player.getUUID());
		return fight == null || fight.boss == null || fight.boss.isRemoved() ? null : fight.boss;
	}

	// --- Starting -----------------------------------------------------------------------------

	/** Starts a confrontation with the full arrival sequence. */
	public static StartResult begin(ServerPlayer player, Trigger trigger) {
		HerobrineConfig config = HerobrineConfig.get();
		if (!config.enabled) {
			return StartResult.DISABLED;
		}
		if (player.level().getDifficulty() == Difficulty.PEACEFUL) {
			return StartResult.PEACEFUL;
		}
		if (FIGHTS.containsKey(player.getUUID())) {
			return StartResult.ALREADY_FIGHTING;
		}
		BlockPos arrival = findSpot(player, 0.0, ARRIVAL_DISTANCE);
		if (arrival == null) {
			return StartResult.NO_ROOM;
		}
		SightingController.end(player.getUUID());
		Fight fight = new Fight(player.getUUID(), trigger, planFor(player, config));
		fight.arrival = arrival;
		FIGHTS.put(player.getUUID(), fight);

		ModNetworking.sendCue(player, ConfrontationCuePayload.Cue.SILENCE);
		player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, DARKNESS_TICKS, 0, false, false));
		PrivateSounds.play(player, ModSounds.DRONE, SoundSource.HOSTILE, player.position(), 1.0F, 0.7F);
		// A 1.5 s swell that cuts off dead on the lightning (tick 30).
		PrivateSounds.play(player, ModSounds.ARRIVAL, SoundSource.HOSTILE, Vec3.atBottomCenterOf(arrival), 1.0F, 1.0F);
		HerobrineMod.LOGGER.info("Confrontation with {} ({})", player.getName().getString(), trigger);
		return StartResult.STARTED;
	}

	/**
	 * Spawns the boss in front of {@code player} immediately, skipping the arrival sequence.
	 * Returns null if there's nowhere to stand. Used by {@link #begin} and by tests.
	 */
	public static @Nullable HerobrineEntity summon(ServerPlayer player, CounterPlan plan) {
		Fight existing = FIGHTS.get(player.getUUID());
		if (existing != null) {
			abandon(existing);
		}
		BlockPos feet = findSpot(player, 0.0, ARRIVAL_DISTANCE);
		if (feet == null) {
			return null;
		}
		SightingController.end(player.getUUID());
		Fight fight = new Fight(player.getUUID(), Trigger.COMMAND, plan);
		HerobrineEntity boss = spawnBoss(player, feet, plan);
		if (boss == null) {
			return null;
		}
		fight.boss = boss;
		fight.arrived = true;
		FIGHTS.put(player.getUUID(), fight);
		return boss;
	}

	/** Natural trigger, checked once a second per player by the haunt director. */
	public static void tryNatural(ServerPlayer player, HauntState haunt, HerobrineConfig config) {
		if (haunt.level() < config.confrontationThreshold || FIGHTS.containsKey(player.getUUID())
				|| player.isCreative() || player.isSpectator() || !player.isAlive()
				|| player.level().getDifficulty() == Difficulty.PEACEFUL || !player.level().isDarkOutside()) {
			return;
		}
		if (!ModAttachments.encounters(player).naturalAllowed(day(player.level().getServer()))
				|| ProfileAnalyzer.confidence(ModAttachments.profile(player), config.minTraitSamples) < config.requiredConfidence
				|| !HauntLevel.isAlone(player)) {
			return;
		}
		double chance = Math.min(1.0, NATURAL_CHANCE_PER_SECOND * config.hauntSpeedMultiplier);
		if (player.getRandom().nextDouble() < chance) {
			begin(player, Trigger.NATURAL);
		}
	}

	private static CounterPlan planFor(ServerPlayer player, HerobrineConfig config) {
		EncounterHistory history = ModAttachments.encounters(player);
		return CounterPlan.create(history.lastFocus(), history.total(), config.adaptiveCounters);
	}

	private static @Nullable HerobrineEntity spawnBoss(ServerPlayer player, BlockPos feet, CounterPlan plan) {
		ServerLevel level = player.level();
		HerobrineEntity boss = ModEntities.HEROBRINE.create(level, EntitySpawnReason.EVENT);
		if (boss == null) {
			return null;
		}
		Vec3 at = Vec3.atBottomCenterOf(feet);
		float yaw = SightingController.yawToward(at, player.position());
		boss.snapTo(at.x, at.y, at.z, yaw, 0.0F);
		boss.setYHeadRot(yaw);
		boss.setYBodyRot(yaw);
		boss.startBoss(player, plan);
		return level.addFreshEntity(boss) ? boss : null;
	}

	// --- Ticking ------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (!STALE.isEmpty()) {
			STALE.forEach(HerobrineEntity::vanish);
			STALE.clear();
		}
		if (!FIGHTS.isEmpty()) {
			for (Fight fight : List.copyOf(FIGHTS.values())) {
				tickFight(server, fight);
			}
		}
		if (!AFTERMATHS.isEmpty()) {
			tickAftermaths(server);
		}
	}

	private static void tickFight(MinecraftServer server, Fight fight) {
		ServerPlayer player = server.getPlayerList().getPlayer(fight.playerId);
		if (player == null || !HerobrineConfig.get().enabled || player.level().getDifficulty() == Difficulty.PEACEFUL) {
			abandon(fight);
			return;
		}
		fight.ticks++;
		if (!fight.arrived) {
			tickArrival(player, fight);
			return;
		}
		fight.fightTicks++;
		HerobrineEntity boss = fight.boss;
		if (boss == null || boss.isRemoved()) {
			if (boss != null && boss.getRemovalReason() != null && !boss.getRemovalReason().shouldDestroy() && player.isAlive()) {
				// His chunk unloaded under him (the player teleported away): he follows.
				replaceBehind(player, fight, boss);
			} else if (boss == null || !boss.isDeadOrDying()) {
				abandon(fight);
			}
			return;
		}
		if (boss.isDeadOrDying() || !player.isAlive()) {
			return;
		}
		if (fight.fightTicks >= fight.nextTaunt) {
			taunt(player, fight);
		}
		if (fight.ticks % FLEE_CHECK_INTERVAL != 0) {
			return;
		}
		if (boss.level() != player.level()) {
			abandon(fight);
		} else if (boss.distanceTo(player) > FLEE_DISTANCE) {
			BlockPos behind = findSpot(player, 180.0, 5.0);
			if (behind != null) {
				Vec3 at = Vec3.atBottomCenterOf(behind);
				boss.teleportTo(at.x, at.y, at.z);
				boss.resetFallDistance();
				boss.getNavigation().stop();
			}
		}
	}

	private static void tickArrival(ServerPlayer player, Fight fight) {
		ServerLevel level = player.level();
		if (!player.isAlive()) {
			abandon(fight);
			return;
		}
		if (fight.arrival == null || !SpawnFinder.isStandable(level, fight.arrival)) {
			fight.arrival = findSpot(player, 0.0, ARRIVAL_DISTANCE);
		}
		if (fight.ticks == LIGHTNING_TICK && fight.arrival != null) {
			LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.EVENT);
			if (bolt != null) {
				bolt.snapTo(Vec3.atBottomCenterOf(fight.arrival));
				bolt.setVisualOnly(true);
				level.addFreshEntity(bolt);
			}
		}
		if (fight.ticks >= ARRIVAL_TICK) {
			HerobrineEntity boss = fight.arrival == null ? null : spawnBoss(player, fight.arrival, fight.plan);
			if (boss == null) {
				abandon(fight);
				return;
			}
			fight.boss = boss;
			fight.arrived = true;
		}
	}

	/** One line on the action bar, built from what he knows about them. */
	private static void taunt(ServerPlayer player, Fight fight) {
		RandomGenerator random = player.getRandom()::nextLong;
		Taunts.Taunt taunt = Taunts.pick(ModAttachments.profile(player), HerobrineConfig.get().minTraitSamples, fight.lastTaunt, random);
		Component line = taunt.argument().isEmpty()
				? Component.translatable(taunt.translationKey())
				: Component.translatable(taunt.translationKey(), taunt.argument());
		player.sendOverlayMessage(line.copy().withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
		fight.lastTaunt = taunt.id();
		fight.taunts++;
		fight.nextTaunt = fight.fightTicks + Taunts.nextInterval(random);
	}

	/** Swaps in a fresh boss behind the player, carrying over his health and heal budget. */
	private static void replaceBehind(ServerPlayer player, Fight fight, HerobrineEntity old) {
		BlockPos behind = findSpot(player, 180.0, 5.0);
		if (behind == null) {
			return; // try again next tick
		}
		HerobrineEntity fresh = spawnBoss(player, behind, fight.plan);
		if (fresh == null) {
			abandon(fight);
			return;
		}
		fresh.setHealth(Math.max(1.0F, old.getHealth()));
		MirrorBrain oldBrain = old.brain();
		MirrorBrain freshBrain = fresh.brain();
		if (oldBrain != null && freshBrain != null) {
			freshBrain.setHealsUsed(oldBrain.healsUsed());
		}
		fight.boss = fresh;
	}

	private static void tickAftermaths(MinecraftServer server) {
		Iterator<Map.Entry<UUID, Aftermath>> it = AFTERMATHS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Aftermath> entry = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			Aftermath aftermath = entry.getValue();
			if (player == null) {
				it.remove();
				continue;
			}
			if (!player.isAlive()) {
				continue; // still on the death screen
			}
			aftermath.ticks++;
			if (aftermath.ticks == AFTERMATH_DELAY) {
				aftermath.entity = spawnAftermath(player);
			}
			if (aftermath.ticks == AFTERMATH_DELAY + AFTERMATH_SPEAK_TICK && aftermath.entity != null) {
				int line = 1 + player.getRandom().nextInt(AFTERMATH_LINES);
				player.sendSystemMessage(Component.translatable("chat.type.text",
						Component.translatable("entity.herobrine.herobrine"), Component.translatable("herobrine.aftermath." + line)));
			}
			if (aftermath.ticks >= AFTERMATH_DELAY + AFTERMATH_TICKS) {
				if (aftermath.entity != null) {
					aftermath.entity.vanish();
				}
				it.remove();
			}
		}
	}

	private static @Nullable HerobrineEntity spawnAftermath(ServerPlayer player) {
		BlockPos feet = findSpot(player, 0.0, 2.5);
		if (feet == null) {
			return null;
		}
		ServerLevel level = player.level();
		HerobrineEntity entity = ModEntities.HEROBRINE.create(level, EntitySpawnReason.EVENT);
		if (entity == null) {
			return null;
		}
		Vec3 at = Vec3.atBottomCenterOf(feet);
		float yaw = SightingController.yawToward(at, player.position());
		entity.setMode(Mode.AFTERMATH);
		entity.setWatchedPlayer(player);
		entity.snapTo(at.x, at.y, at.z, yaw, 0.0F);
		entity.setYHeadRot(yaw);
		entity.setYBodyRot(yaw);
		return level.addFreshEntity(entity) ? entity : null;
	}

	// --- Endings ------------------------------------------------------------------------------

	private static void onBossDeath(HerobrineEntity boss) {
		UUID owner = boss.watchedPlayerId();
		Fight fight = owner == null ? null : FIGHTS.get(owner);
		if (fight == null || fight.boss != boss || !(boss.level() instanceof ServerLevel level)) {
			return;
		}
		FIGHTS.remove(owner);
		ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
		if (player == null) {
			return;
		}
		level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), ModSounds.SHATTER, SoundSource.HOSTILE, 1.0F, 1.0F);
		ItemEntity shard = boss.spawnAtLocation(level, new ItemStack(ModItems.MIRROR_SHARD));
		if (shard != null) {
			shard.setUnlimitedLifetime();
			shard.setGlowingTag(true);
		}
		AdvancementHolder advancement = level.getServer().getAdvancements().get(KNOW_THYSELF);
		if (advancement != null) {
			player.getAdvancements().award(advancement, KNOW_THYSELF_CRITERION);
		}
		player.sendOverlayMessage(Component.translatable("herobrine.confrontation.won"));
		record(player, fight, true);
	}

	private static void onPlayerDeath(ServerPlayer player) {
		Fight fight = FIGHTS.get(player.getUUID());
		if (fight == null) {
			return;
		}
		if (!fight.arrived) {
			abandon(fight);
			return;
		}
		FIGHTS.remove(player.getUUID());
		record(player, fight, false);
		if (fight.boss != null) {
			fight.boss.vanish();
		}
		// He waits for them at the respawn point.
		AFTERMATHS.put(player.getUUID(), new Aftermath());
	}

	private static void record(ServerPlayer player, Fight fight, boolean won) {
		HerobrineConfig config = HerobrineConfig.get();
		List<Habit> focus = ProfileAnalyzer.topHabits(ModAttachments.profile(player), config.minTraitSamples,
				CounterPlan.MAX_COUNTERS, CounterPlan.MIN_RELIANCE);
		MirrorBrain brain = fight.boss == null ? null : fight.boss.brain();
		int heals = brain == null ? 0 : brain.healsUsed();
		ModAttachments.encounters(player).add(new EncounterRecord(day(player.level().getServer()), won, fight.fightTicks, heals, focus));
		HauntLevel.set(player, won ? WIN_HAUNT_LEVEL : LOSS_HAUNT_LEVEL);
		HerobrineMod.LOGGER.info("Confrontation with {} ended: {}", player.getName().getString(), won ? "player won" : "player lost");
	}

	/** Ends a fight with no result: he simply leaves. */
	private static void abandon(Fight fight) {
		FIGHTS.remove(fight.playerId);
		if (fight.boss != null && !fight.boss.isRemoved()) {
			fight.boss.vanish();
		}
	}

	/** Ends every fight; for {@code /herobrine reload} and tests. */
	public static void abandonAll() {
		for (Fight fight : List.copyOf(FIGHTS.values())) {
			abandon(fight);
		}
	}

	// --- Reloads ------------------------------------------------------------------------------

	private static void onEntityLoad(Entity entity, ServerLevel level) {
		if (!(entity instanceof HerobrineEntity herobrine) || herobrine.mode() != Mode.BOSS) {
			return;
		}
		UUID owner = herobrine.watchedPlayerId();
		Fight fight = owner == null ? null : FIGHTS.get(owner);
		if (fight != null && (fight.boss == null || fight.boss.getUUID().equals(herobrine.getUUID()))) {
			fight.boss = herobrine; // back with its chunk: same fight
			return;
		}
		ServerPlayer player = owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);
		if (fight == null && player != null && player.level() == level && player.isAlive()) {
			// Saved mid-fight before a restart: pick up where it left off.
			Fight resumed = new Fight(owner, Trigger.COMMAND, CounterPlan.NONE);
			resumed.boss = herobrine;
			resumed.arrived = true;
			FIGHTS.put(owner, resumed);
			return;
		}
		STALE.add(herobrine);
	}

	// --- Helpers ------------------------------------------------------------------------------

	/** Standing room at {@code distance} from the player, {@code yawOffset} degrees from where they look. */
	private static @Nullable BlockPos findSpot(ServerPlayer player, double yawOffset, double distance) {
		int[] fan = {0, 30, -30, 60, -60, 90, -90, 135, -135, 180};
		for (double d : new double[] {distance, distance * 0.6}) {
			for (int offset : fan) {
				double angle = Math.toRadians(player.getYRot() + yawOffset + offset);
				int x = Mth.floor(player.getX() - Math.sin(angle) * d);
				int z = Mth.floor(player.getZ() + Math.cos(angle) * d);
				BlockPos feet = SpawnFinder.findStandable(player.level(), x, z, player.getBlockY(), 4, false);
				if (feet != null) {
					return feet;
				}
			}
		}
		return null;
	}

	static long day(MinecraftServer server) {
		return server.overworld().getOverworldClockTime() / 24000L;
	}
}

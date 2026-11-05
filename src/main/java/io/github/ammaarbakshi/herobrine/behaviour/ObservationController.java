package io.github.ammaarbakshi.herobrine.behaviour;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.end.EnderDragonFight;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.BlockHitResult;

import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import io.github.ammaarbakshi.herobrine.HerobrineMod;
import io.github.ammaarbakshi.herobrine.config.HerobrineConfig;
import io.github.ammaarbakshi.herobrine.entity.HerobrineEntity;
import io.github.ammaarbakshi.herobrine.memory.BossKind;
import io.github.ammaarbakshi.herobrine.memory.Counters;
import io.github.ammaarbakshi.herobrine.memory.GearTier;
import io.github.ammaarbakshi.herobrine.memory.Milestone;
import io.github.ammaarbakshi.herobrine.memory.ObserverMemory;
import io.github.ammaarbakshi.herobrine.memory.Place;
import io.github.ammaarbakshi.herobrine.memory.WorldEvent;
import io.github.ammaarbakshi.herobrine.perf.Perf;
import io.github.ammaarbakshi.herobrine.registry.ModAttachments;
import io.github.ammaarbakshi.herobrine.sighting.SightingController;

/**
 * What he notices about a player's journey, turned into memory and into moments he may comment
 * on ({@link DialogueController#trigger}).
 *
 * <p>Event-driven where Fabric offers an event: equipment changes (progression), damage and
 * deaths (bosses, the player's own deaths), entity loads (a Wither being built, a Warden
 * emerging), dimension changes (the Nether, the End), sleeping (home), block placing
 * (building). Everything else is one staggered look-around per player every 5 seconds
 * (weather, caves, nights) and a slower one every 20 seconds (villages, the memorial,
 * advancements). No world scans: each check is a handful of lookups.
 */
public final class ObservationController {
	public static final ResourceKey<Structure> BERSERK = ResourceKey.create(Registries.STRUCTURE, HerobrineMod.id("berserk"));

	private static final int SCAN_INTERVAL = 5 * 20;
	private static final int SLOW_SCAN_EVERY = 4;
	private static final int VILLAGE_RANGE = 48;
	private static final double HOME_RANGE = 48.0;
	/** Below sea level by this much, with no sky: underground. */
	private static final int UNDERGROUND_DEPTH = 8;
	private static final long CAVE_COMMENT_AFTER = 60 * 20;
	private static final int MINING_MILESTONE_MINUTES = 5;
	private static final double WARDEN_RANGE = 32.0;
	private static final double BOSS_ARRIVAL_RANGE = 48.0;
	/** A boss fight counts as going on until this long after the last contact. */
	private static final long BOSS_FIGHT_TICKS = 2 * 60 * 20;
	/** No contact with that boss kind for this long and the next contact is a new encounter. */
	private static final long BOSS_NEW_ENCOUNTER = 10 * 60 * 20;
	private static final int BUILD_RADIUS = 16;
	private static final int BUILD_COUNT = 40;
	private static final long BUILD_WINDOW = 4 * 60 * 20;
	/** Night lasts from 13000 to 23000 of the day clock; a player must exist for all of it. */
	private static final long NIGHT_TICKS = 11000;
	/** Play time after which a newly observed player is clearly not new: early milestones are assumed. */
	private static final int EXPERIENCED_PLAY_TICKS = 2 * 24000;

	/** Death tags, held from the death until the respawn. */
	private static final Map<UUID, Set<String>> DEATHS = new HashMap<>();
	private static final Map<Item, GearTier> TIERS = new HashMap<>();
	private static long tick;

	private ObservationController() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(Perf.timed(Perf.WORLD, ObservationController::tick));
		ServerPlayerEvents.JOIN.register(player -> ModAttachments.observer(player).since = Observer.now(player));
		ServerPlayerEvents.AFTER_RESPAWN.register(ObservationController::onRespawn);
		ServerLivingEntityEvents.AFTER_DEATH.register(ObservationController::onDeath);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(ObservationController::onDamage);
		ServerEntityEvents.ENTITY_LOAD.register(ObservationController::onEntityLoad);
		ServerEntityEvents.EQUIPMENT_CHANGE.register(ObservationController::onEquipmentChange);
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register(ObservationController::onChangeLevel);
		EntitySleepEvents.START_SLEEPING.register(ObservationController::onSleep);
		UseBlockCallback.EVENT.register(ObservationController::onUseBlock);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> DEATHS.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> DEATHS.clear());
	}

	// --- The look-around ----------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		tick++;
		if (!Observer.active()) {
			return;
		}
		List<ServerPlayer> players = server.getPlayerList().getPlayers();
		for (int i = 0; i < players.size(); i++) {
			if ((tick + i * 11L) % SCAN_INTERVAL != 0) {
				continue;
			}
			ServerPlayer player = players.get(i);
			if (player.isSpectator() || !player.isAlive()) {
				continue;
			}
			scan(player, (tick + i * 11L) / SCAN_INTERVAL % SLOW_SCAN_EVERY == 0);
		}
	}

	private static void scan(ServerPlayer player, boolean slow) {
		ObserverMemory memory = ModAttachments.memory(player);
		ObserverState state = ModAttachments.observer(player);
		long now = Observer.now(player);
		if (state.since < 0) {
			state.since = now;
		}
		if (!memory.baselined()) {
			baseline(player, memory, now);
		}
		ServerLevel level = player.level();
		boolean overworld = level.dimension() == Level.OVERWORLD;
		if (overworld) {
			weather(player, state);
			underground(player, state, memory, now);
			nights(player, state, memory, now);
			if (player.getY() < 0) {
				warden(player);
			}
		}
		Place home = memory.home();
		state.nearHome = home != null && home.distanceSqr(level.dimension().identifier().toString(),
				player.getX(), player.getY(), player.getZ()) < HOME_RANGE * HOME_RANGE;
		if (slow) {
			advancements(player, memory);
			if (overworld) {
				village(player, state);
				memorial(player, state);
			}
			DialogueController.trigger(player, "idle");
		}
	}

	/**
	 * The first look at a player: note what they already have without a word, so he only ever
	 * comments on what happens from now on.
	 */
	static void baseline(ServerPlayer player, ObserverMemory memory, long now) {
		memory.set(Counters.GEAR_TIER, Math.max(memory.counter(Counters.GEAR_TIER), bestTier(player).ordinal()));
		MinecraftServer server = player.level().getServer();
		for (Milestone milestone : Milestone.values()) {
			milestone.advancement().ifPresent(path -> {
				var holder = server.getAdvancements().get(Identifier.withDefaultNamespace(path));
				if (holder != null && player.getAdvancements().getOrStartProgress(holder).isDone()) {
					memory.addMilestone(milestone);
				}
			});
		}
		if (player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)) >= EXPERIENCED_PLAY_TICKS) {
			memory.addMilestone(Milestone.FIRST_NIGHT);
			memory.addMilestone(Milestone.FIRST_MINING);
			memory.addMilestone(Milestone.FIRST_HOUSE);
			memory.addMilestone(Milestone.FIRST_VILLAGE);
		}
		memory.markBaselined(now);
	}

	private static void weather(ServerPlayer player, ObserverState state) {
		ServerLevel level = player.level();
		String weather = level.isThundering() ? "thunder" : level.isRaining() ? "rain" : "clear";
		if (weather.equals("clear")) {
			state.weather = weather;
			return;
		}
		// Once per spell of weather, the first time he sees them out in it.
		if (!weather.equals(state.weather) && level.isRainingAt(player.blockPosition().above())) {
			state.weather = weather;
			DialogueController.trigger(player, "weather", "weather:" + weather);
		}
	}

	private static void underground(ServerPlayer player, ObserverState state, ObserverMemory memory, long now) {
		ServerLevel level = player.level();
		BlockPos pos = player.blockPosition();
		boolean underground = pos.getY() < level.getSeaLevel() - UNDERGROUND_DEPTH && !level.canSeeSky(pos.above());
		if (!underground) {
			state.undergroundSince = -1;
			state.caveNoticed = false;
			return;
		}
		if (state.undergroundSince < 0) {
			state.undergroundSince = now;
		}
		state.undergroundTicks += SCAN_INTERVAL;
		if (state.undergroundTicks >= Observer.TICKS_PER_MINUTE) {
			state.undergroundTicks -= (int) Observer.TICKS_PER_MINUTE;
			memory.increment(Counters.UNDERGROUND_MINUTES);
			if (memory.counter(Counters.UNDERGROUND_MINUTES) >= MINING_MILESTONE_MINUTES) {
				recognise(player, Milestone.FIRST_MINING);
			}
		}
		if (!state.caveNoticed && now - state.undergroundSince >= CAVE_COMMENT_AFTER) {
			state.caveNoticed = true;
			DialogueController.trigger(player, "cave", pos.getY() < 0 ? "depth:deep" : "depth:shallow");
		}
	}

	private static void nights(ServerPlayer player, ObserverState state, ObserverMemory memory, long now) {
		long day = player.level().getServer().overworld().getOverworldClockTime() / Observer.TICKS_PER_DAY;
		if (state.dayChecked < 0) {
			state.dayChecked = day;
			return;
		}
		if (day <= state.dayChecked) {
			return;
		}
		state.dayChecked = day;
		// A new day began: this player entity was here (alive) for the whole night before it.
		if (now - state.since >= NIGHT_TICKS) {
			memory.increment(Counters.NIGHTS);
			recognise(player, Milestone.FIRST_NIGHT);
		}
	}

	private static void warden(ServerPlayer player) {
		List<Warden> wardens = player.level().getEntities(EntityTypes.WARDEN, player.getBoundingBox().inflate(WARDEN_RANGE), Entity::isAlive);
		if (!wardens.isEmpty()) {
			noticeBoss(player, BossKind.WARDEN, "arrive");
		}
	}

	private static void advancements(ServerPlayer player, ObserverMemory memory) {
		MinecraftServer server = player.level().getServer();
		for (Milestone milestone : Milestone.values()) {
			if (milestone.advancement().isEmpty() || memory.hasMilestone(milestone)) {
				continue;
			}
			var holder = server.getAdvancements().get(Identifier.withDefaultNamespace(milestone.advancement().get()));
			if (holder != null && player.getAdvancements().getOrStartProgress(holder).isDone()) {
				recognise(player, milestone);
			}
		}
	}

	private static void village(ServerPlayer player, ObserverState state) {
		state.nearVillage = player.level().getPoiManager()
				.findClosest(type -> type.is(PoiTypes.MEETING), player.blockPosition(), VILLAGE_RANGE, PoiManager.Occupancy.ANY)
				.isPresent();
		if (state.nearVillage) {
			recognise(player, Milestone.FIRST_VILLAGE);
		}
	}

	private static void memorial(ServerPlayer player, ObserverState state) {
		ServerLevel level = player.level();
		Structure berserk = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(BERSERK);
		boolean near = berserk != null && level.structureManager().getAllStructuresAt(player.blockPosition()).containsKey(berserk);
		state.nearBerserk = near;
		if (near) {
			recognise(player, Milestone.FOUND_BERSERK);
		}
	}

	// --- Events -------------------------------------------------------------------------------

	private static void onEquipmentChange(LivingEntity entity, EquipmentSlot slot, ItemStack previous, ItemStack current) {
		if (!(entity instanceof ServerPlayer player) || current.isEmpty() || !Observer.active()) {
			return;
		}
		GearTier tier = tierOf(current.getItem());
		if (tier == GearTier.NONE) {
			return;
		}
		ObserverMemory memory = ModAttachments.memory(player);
		if (!memory.baselined()) {
			// Logging in counts as "changing" into everything they wear: that's not news.
			baseline(player, memory, Observer.now(player));
			return;
		}
		if (tier.isAbove(GearTier.byOrdinal(memory.counter(Counters.GEAR_TIER)))) {
			memory.set(Counters.GEAR_TIER, tier.ordinal());
			memory.recordEvent(new WorldEvent("tier_" + tier.id(), Observer.place(player)));
			DialogueController.trigger(player, "progression", "tier:" + tier.id());
		}
	}

	private static void onDamage(LivingEntity entity, DamageSource source, float baseDamage, float damageTaken, boolean blocked) {
		if (!Observer.active()) {
			return;
		}
		BossKind attacked = bossKind(entity);
		if (attacked != null && source.getEntity() instanceof ServerPlayer player) {
			noticeBoss(player, attacked, "fight");
		} else if (entity instanceof ServerPlayer player) {
			BossKind attacker = bossKind(source.getEntity());
			if (attacker != null) {
				noticeBoss(player, attacker, "fight");
			}
		}
	}

	private static void onEntityLoad(Entity entity, ServerLevel level) {
		if (!Observer.active()) {
			return;
		}
		// Freshly built (still charging up) or freshly emerged: a boss is arriving, not reloading.
		boolean arriving = entity instanceof WitherBoss wither && wither.getInvulnerableTicks() > 0
				|| entity instanceof Warden warden && warden.tickCount < 5 && warden.hasPose(Pose.EMERGING);
		BossKind kind = bossKind(entity);
		if (!arriving || kind == null) {
			return;
		}
		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator() && player.distanceToSqr(entity) < BOSS_ARRIVAL_RANGE * BOSS_ARRIVAL_RANGE) {
				noticeBoss(player, kind, "arrive");
			}
		}
	}

	private static void onChangeLevel(ServerPlayer player, ServerLevel origin, ServerLevel destination) {
		if (!Observer.active()) {
			return;
		}
		if (destination.dimension() == Level.NETHER) {
			recognise(player, Milestone.FIRST_NETHER_PORTAL);
		} else if (destination.dimension() == Level.END) {
			EnderDragonFight fight = destination.getDragonFight();
			if (fight != null && !fight.hasPreviouslyKilledDragon()) {
				noticeBoss(player, BossKind.ENDER_DRAGON, "arrive");
			}
		}
	}

	private static void onDeath(LivingEntity entity, DamageSource source) {
		if (!Observer.active()) {
			return;
		}
		if (entity instanceof ServerPlayer player) {
			ModAttachments.memory(player).increment(Counters.DEATHS);
			if (source.getEntity() instanceof HerobrineEntity) {
				return; // the Mirror's aftermath speaks for him
			}
			Set<String> tags = new HashSet<>();
			source.typeHolder().unwrapKey().ifPresent(key -> tags.add("cause:" + key.identifier().getPath()));
			Entity killer = source.getEntity();
			if (killer != null && killer != player) {
				tags.add("killer:" + BuiltInRegistries.ENTITY_TYPE.getKey(killer.getType()).getPath());
			}
			if (ModAttachments.observer(player).inBossFight(Observer.now(player))) {
				tags.add("in_boss_fight");
			}
			DEATHS.put(player.getUUID(), tags);
			return;
		}
		BossKind kind = bossKind(entity);
		if (kind == null || !(entity.level() instanceof ServerLevel level)) {
			return;
		}
		for (ServerPlayer player : level.players()) {
			// The dragon credits everyone nearby; the others whoever dealt the blow, or stood close.
			boolean credited = source.getEntity() == player
					|| player.distanceToSqr(entity) < (kind == BossKind.ENDER_DRAGON ? 192 * 192 : 32 * 32);
			if (credited && !player.isSpectator()) {
				ObserverMemory memory = ModAttachments.memory(player);
				memory.increment(Counters.kill(kind));
				memory.recordEvent(new WorldEvent("killed_" + kind.id(), Observer.place(player)));
				ModAttachments.observer(player).bossFightUntil = -1;
				DialogueController.trigger(player, "boss", "boss:" + kind.id(), "phase:killed");
				if (kind == BossKind.ENDER_DRAGON) {
					recognise(player, Milestone.FIRST_DRAGON);
				}
			}
		}
	}

	private static void onRespawn(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive) {
		ObserverState state = ModAttachments.observer(newPlayer);
		state.since = Observer.now(newPlayer);
		Set<String> tags = DEATHS.remove(newPlayer.getUUID());
		if (!alive && tags != null) {
			DialogueController.trigger(newPlayer, "death", tags);
		}
	}

	private static void onSleep(LivingEntity entity, BlockPos bed) {
		if (!(entity instanceof ServerPlayer player) || !Observer.active()) {
			return;
		}
		ObserverMemory memory = ModAttachments.memory(player);
		memory.setHome(Observer.place(player.level(), bed, Observer.now(player)));
		if (!memory.hasMilestone(Milestone.FIRST_HOUSE) && isEnclosed(player.level(), bed)) {
			recognise(player, Milestone.FIRST_HOUSE);
		}
	}

	private static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer) || player.isSpectator()
				|| !(player.getItemInHand(hand).getItem() instanceof BlockItem) || !Observer.active()) {
			return InteractionResult.PASS;
		}
		ObserverState state = ModAttachments.observer(serverPlayer);
		long now = Observer.now(serverPlayer);
		BlockPos at = hit.getBlockPos();
		if (state.buildCenter == null || now - state.buildStart > BUILD_WINDOW
				|| state.buildCenter.distSqr(at) > BUILD_RADIUS * BUILD_RADIUS) {
			state.buildCenter = at.immutable();
			state.buildStart = now;
			state.buildCount = 0;
		}
		if (++state.buildCount == BUILD_COUNT) {
			DialogueController.trigger(serverPlayer, "building", state.nearHome ? Set.of("near:home") : Set.of());
		}
		return InteractionResult.PASS;
	}

	// --- Helpers ------------------------------------------------------------------------------

	/** Records a milestone the first time, with a diary entry, and gives him the chance to say so. */
	public static void recognise(ServerPlayer player, Milestone milestone) {
		ObserverMemory memory = ModAttachments.memory(player);
		if (!memory.baselined() || !memory.addMilestone(milestone)) {
			return;
		}
		memory.recordEvent(new WorldEvent(milestone.id(), Observer.place(player)));
		DialogueController.trigger(player, "milestone", "milestone:" + milestone.id());
	}

	/**
	 * Contact with a boss. Keeps the fight "on" (so he keeps his distance), counts a new encounter
	 * after a long gap, and offers each phase (arrive, fight) to the voice once per encounter.
	 */
	static void noticeBoss(ServerPlayer player, BossKind kind, String phase) {
		ObserverState state = ModAttachments.observer(player);
		long now = Observer.now(player);
		state.bossFightUntil = now + BOSS_FIGHT_TICKS;
		Long last = state.bossNoticed.put(kind, now);
		if (last == null || now - last >= BOSS_NEW_ENCOUNTER) {
			state.bossPhases.removeIf(p -> p.startsWith(kind.id() + ":"));
			ObserverMemory memory = ModAttachments.memory(player);
			memory.increment(Counters.BOSS_ENCOUNTERS);
			memory.increment(Counters.boss(kind));
			memory.recordEvent(new WorldEvent("boss_" + kind.id(), Observer.place(player)));
			if (!HerobrineConfig.get().appearDuringBossFights) {
				// He never interferes: whatever apparition was out leaves the stage.
				SightingController.end(player.getUUID());
			}
		}
		if (state.bossPhases.add(kind.id() + ":" + phase)) {
			DialogueController.trigger(player, "boss", "boss:" + kind.id(), "phase:" + phase);
		}
	}

	private static @Nullable BossKind bossKind(@Nullable Entity entity) {
		if (entity instanceof WitherBoss) {
			return BossKind.WITHER;
		}
		if (entity instanceof EnderDragon) {
			return BossKind.ENDER_DRAGON;
		}
		if (entity instanceof Warden) {
			return BossKind.WARDEN;
		}
		return null;
	}

	static GearTier tierOf(Item item) {
		return TIERS.computeIfAbsent(item, i -> GearTier.ofItemPath(BuiltInRegistries.ITEM.getKey(i).getPath()));
	}

	/** Best tool, weapon or armour tier anywhere in their inventory. */
	static GearTier bestTier(ServerPlayer player) {
		GearTier best = GearTier.NONE;
		var inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			GearTier tier = tierOf(inventory.getItem(slot).getItem());
			if (tier.isAbove(best)) {
				best = tier;
			}
		}
		return best;
	}

	/**
	 * A bed under a roof with walls on at least three sides: a house, however small. Bounded: at
	 * most 6 blocks up and 8 out in four directions.
	 */
	static boolean isEnclosed(ServerLevel level, BlockPos bed) {
		boolean roof = false;
		for (int dy = 2; dy <= 6 && !roof; dy++) {
			roof = isSolid(level, bed.above(dy));
		}
		if (!roof) {
			return false;
		}
		int walls = 0;
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			for (int d = 1; d <= 8; d++) {
				if (isSolid(level, bed.above().relative(direction, d))) {
					walls++;
					break;
				}
			}
		}
		return walls >= 3;
	}

	private static boolean isSolid(ServerLevel level, BlockPos pos) {
		return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
	}
}

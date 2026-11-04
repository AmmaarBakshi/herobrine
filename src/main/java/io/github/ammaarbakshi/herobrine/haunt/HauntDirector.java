package io.github.ammaarbakshi.herobrine.haunt;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;

import io.github.ammaarbakshi.herobrine.config.HerobrineConfig;
import io.github.ammaarbakshi.herobrine.haunt.events.EventContext;
import io.github.ammaarbakshi.herobrine.haunt.events.HauntEvent;
import io.github.ammaarbakshi.herobrine.haunt.events.HauntEvents;
import io.github.ammaarbakshi.herobrine.registry.ModAttachments;
import io.github.ammaarbakshi.herobrine.registry.ModTags;

/**
 * Drives the haunting. Per player and staggered so the work spreads across ticks:
 * <ul>
 *   <li>every 2 ticks: one movement sample into the trail (for the ghost replay);</li>
 *   <li>every 5 s: remember where they are (their path, caves they're in);</li>
 *   <li>every second: haunt gains, and maybe one event once the cooldown is over.</li>
 * </ul>
 * Nothing here scans the world; events do their own bounded searches when they fire.
 */
public final class HauntDirector {
	public enum ForceResult {
		FIRED,
		/** The event ran but found nowhere suitable (or safe) to happen. */
		NOTHING_FOUND,
		/** Blocked by the config: mod disabled, griefing level too low, toggle off. */
		DISABLED
	}

	private static final int PROCESS_INTERVAL = 20;
	private static final int PLACE_INTERVAL = 100;
	/** "Every few in-game minutes": 2-5 real minutes between chances, at multiplier 1. */
	private static final int MIN_COOLDOWN = 2 * 60 * 20;
	private static final int MAX_COOLDOWN = 5 * 60 * 20;
	private static final int PICK_ATTEMPTS = 3;
	private static final double DAY_GAIN = 3.0;
	/** +1 per real minute, applied once a second. */
	private static final double UNDERGROUND_GAIN_PER_SECOND = 1.0 / 60.0;
	private static final int UNDERGROUND_Y = 40;

	private static long tick;

	private HauntDirector() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(HauntDirector::tick);
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (player instanceof ServerPlayer serverPlayer && state.is(ModTags.TUNNEL_CARVABLE)) {
				PlayerTrail trail = ModAttachments.trail(serverPlayer);
				trail.track(serverPlayer);
				trail.rememberMined(pos);
			}
		});
	}

	private static void tick(MinecraftServer server) {
		tick++;
		HerobrineConfig config = HerobrineConfig.get();
		if (!config.enabled) {
			return;
		}
		List<ServerPlayer> players = server.getPlayerList().getPlayers();
		for (int i = 0; i < players.size(); i++) {
			ServerPlayer player = players.get(i);
			if (player.isSpectator() || !player.isAlive()) {
				continue;
			}
			PlayerTrail trail = ModAttachments.trail(player);
			if (tick % PlayerTrail.SAMPLE_INTERVAL == 0) {
				trail.track(player);
				trail.recordMovement(player);
			}
			// Stagger by player index so a full server doesn't do everyone's work on the same tick.
			if ((tick + i * 7L) % PLACE_INTERVAL == 0) {
				rememberPlace(player, trail);
			}
			if ((tick + i) % PROCESS_INTERVAL == 0) {
				process(server, player, trail, config);
			}
		}
	}

	private static void rememberPlace(ServerPlayer player, PlayerTrail trail) {
		BlockPos pos = player.blockPosition();
		trail.rememberPath(pos);
		ServerLevel level = player.level();
		if (!level.canSeeSky(pos.above()) && pos.getY() < level.getSeaLevel()) {
			trail.rememberCave(pos);
		}
	}

	private static void process(MinecraftServer server, ServerPlayer player, PlayerTrail trail, HerobrineConfig config) {
		HauntState haunt = ModAttachments.haunt(player);
		applyGains(server, player, haunt);

		if (trail.nextEventTick < 0) {
			trail.nextEventTick = tick + cooldown(player.getRandom(), config);
			return;
		}
		if (tick < trail.nextEventTick) {
			return;
		}
		trail.nextEventTick = tick + cooldown(player.getRandom(), config);
		RandomSource random = player.getRandom();
		double chance = 0.35 + 0.65 * haunt.level() / HauntPhase.MAX_LEVEL;
		if (random.nextDouble() >= chance) {
			return;
		}
		EventContext ctx = context(player, haunt, trail, config, false);
		pickAndFire(ctx);
	}

	private static void applyGains(MinecraftServer server, ServerPlayer player, HauntState haunt) {
		long day = server.overworld().getOverworldClockTime() / 24000L;
		if (haunt.lastDayCounted() < 0) {
			haunt.setLastDayCounted(day);
		} else if (day > haunt.lastDayCounted()) {
			// One day's worth at most: days that passed while they were offline weren't survived.
			haunt.setLastDayCounted(day);
			HauntLevel.gain(player, DAY_GAIN);
		}
		if (player.getY() < UNDERGROUND_Y && server.overworld().isDarkOutside()) {
			HauntLevel.gain(player, UNDERGROUND_GAIN_PER_SECOND);
		}
	}

	/** Weighted pick among events possible right now; up to three tries if one finds nothing. */
	private static boolean pickAndFire(EventContext ctx) {
		List<HauntEvent> candidates = new ArrayList<>();
		List<Integer> weights = new ArrayList<>();
		int total = 0;
		for (HauntEvent event : HauntEvents.all()) {
			if (event.minPhase().ordinal() > ctx.phase().ordinal() || !WorldGuard.allows(event.requiredGriefing())) {
				continue;
			}
			int weight = event.weight(ctx);
			if (weight > 0) {
				candidates.add(event);
				weights.add(weight);
				total += weight;
			}
		}
		for (int attempt = 0; attempt < PICK_ATTEMPTS && total > 0; attempt++) {
			int roll = ctx.random().nextInt(total);
			int index = 0;
			while (roll >= weights.get(index)) {
				roll -= weights.get(index);
				index++;
			}
			if (candidates.get(index).fire(ctx)) {
				return true;
			}
			total -= weights.get(index);
			candidates.remove(index);
			weights.remove(index);
		}
		return false;
	}

	/** Fires one event now for {@code /herobrine sign}, skipping chance, cooldown and phase. */
	public static ForceResult force(ServerPlayer player, HauntEvent event) {
		HerobrineConfig config = HerobrineConfig.get();
		if (!WorldGuard.allows(event.requiredGriefing())) {
			return ForceResult.DISABLED;
		}
		PlayerTrail trail = ModAttachments.trail(player);
		trail.track(player);
		EventContext ctx = context(player, ModAttachments.haunt(player), trail, config, true);
		return event.fire(ctx) ? ForceResult.FIRED : ForceResult.NOTHING_FOUND;
	}

	private static EventContext context(ServerPlayer player, HauntState haunt, PlayerTrail trail, HerobrineConfig config, boolean forced) {
		ServerLevel level = player.level();
		return new EventContext(player, level, level.getRandom(), haunt, HauntLevel.phase(player), trail,
				WorldEditLog.get(level.getServer()), config, forced);
	}

	private static int cooldown(RandomSource random, HerobrineConfig config) {
		int base = Mth.randomBetweenInclusive(random, MIN_COOLDOWN, MAX_COOLDOWN);
		double scaled = base / Math.max(config.hauntSpeedMultiplier, 0.01);
		return (int) Mth.clamp(scaled, 100, 24000);
	}
}

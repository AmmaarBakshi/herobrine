package io.github.ammaarbakshi.herobrine.behaviour;

import java.util.random.RandomGenerator;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import io.github.ammaarbakshi.herobrine.config.HerobrineConfig;
import io.github.ammaarbakshi.herobrine.memory.Place;
import io.github.ammaarbakshi.herobrine.perf.Perf;

/**
 * Small shared helpers for the Living Observer: the clock everything is measured in, the master
 * switch, and conversions into the plain types the memory stores.
 */
public final class Observer {
	public static final long TICKS_PER_DAY = 24000L;
	public static final long TICKS_PER_MINUTE = 1200L;

	private Observer() {
	}

	/**
	 * Starts the three controllers, kept apart from each other, and gives them one shared tick,
	 * measured on its own meter ({@code /herobrine perf}): the voice first, so a line due this tick
	 * goes out before anything new is noticed.
	 */
	public static void init() {
		DialogueController.init();
		AppearanceController.init();
		ObservationController.init();
		ServerTickEvents.END_SERVER_TICK.register(Perf.timed(Perf.OBSERVER, server -> {
			DialogueController.tick(server);
			AppearanceController.tick(server);
			ObservationController.tick(server);
		}));
	}

	/**
	 * Overworld game time: saved with the world, never runs backwards, unaffected by {@code /time
	 * set}. Every time in the memory uses it, so cooldowns survive restarts exactly.
	 */
	public static long now(MinecraftServer server) {
		return server.overworld().getGameTime();
	}

	public static long now(ServerPlayer player) {
		return now(player.level().getServer());
	}

	/** Master switch plus the observer switch. */
	public static boolean active() {
		HerobrineConfig config = HerobrineConfig.get();
		return config.enabled && config.observer;
	}

	public static Place place(ServerLevel level, BlockPos pos, long time) {
		return new Place(level.dimension().identifier().toString(), pos.getX(), pos.getY(), pos.getZ(), time);
	}

	public static Place place(ServerPlayer player) {
		return place(player.level(), player.blockPosition(), now(player));
	}

	/** The player's own random source, as the {@link RandomGenerator} the pure logic takes. */
	public static RandomGenerator random(ServerPlayer player) {
		return player.getRandom()::nextLong;
	}
}

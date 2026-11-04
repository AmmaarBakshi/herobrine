package io.github.ammaarbakshi.herobrine.haunt.events;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

import io.github.ammaarbakshi.herobrine.config.HerobrineConfig;
import io.github.ammaarbakshi.herobrine.haunt.HauntPhase;
import io.github.ammaarbakshi.herobrine.haunt.HauntState;
import io.github.ammaarbakshi.herobrine.haunt.PlayerTrail;
import io.github.ammaarbakshi.herobrine.haunt.WorldEditLog;

/**
 * Everything an event needs about its target. {@code forced} is set by commands: it skips the
 * random roll and phase gate, but never the safety rules.
 */
public record EventContext(
		ServerPlayer player,
		ServerLevel level,
		RandomSource random,
		HauntState haunt,
		HauntPhase phase,
		PlayerTrail trail,
		WorldEditLog log,
		HerobrineConfig config,
		boolean forced) {

	/** In-game day number, from the overworld clock so it means the same in every dimension. */
	public long day() {
		return level.getServer().overworld().getOverworldClockTime() / 24000L;
	}
}

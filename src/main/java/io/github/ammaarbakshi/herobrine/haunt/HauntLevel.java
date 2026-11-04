package io.github.ammaarbakshi.herobrine.haunt;

import net.minecraft.server.level.ServerPlayer;

import io.github.ammaarbakshi.herobrine.config.HerobrineConfig;
import io.github.ammaarbakshi.herobrine.registry.ModAttachments;

/** Reads and changes a player's haunt level, applying the configured speed multiplier. */
public final class HauntLevel {
	private HauntLevel() {
	}

	public static double get(ServerPlayer player) {
		return ModAttachments.haunt(player).level();
	}

	public static void set(ServerPlayer player, double level) {
		ModAttachments.haunt(player).setLevel(level);
	}

	/** No other player within this many blocks counts as alone. */
	public static final double ALONE_RANGE = 64.0;
	public static final double ALONE_MULTIPLIER = 1.5;

	/**
	 * Organic gain: scaled by {@code hauntSpeedMultiplier}, and by 1.5 while the player is
	 * alone. Use {@link #set} for exact changes.
	 */
	public static void gain(ServerPlayer player, double amount) {
		double multiplier = HerobrineConfig.get().hauntSpeedMultiplier * (isAlone(player) ? ALONE_MULTIPLIER : 1.0);
		ModAttachments.haunt(player).add(amount * multiplier);
	}

	/** True if no other (non-spectator) player is within {@link #ALONE_RANGE} in the same level. */
	public static boolean isAlone(ServerPlayer player) {
		for (ServerPlayer other : player.level().players()) {
			if (other != player && !other.isSpectator() && other.distanceToSqr(player) < ALONE_RANGE * ALONE_RANGE) {
				return false;
			}
		}
		return true;
	}

	public static HauntPhase phase(ServerPlayer player) {
		HerobrineConfig config = HerobrineConfig.get();
		return HauntPhase.of(ModAttachments.haunt(player).levelInt(),
				config.sightingsThreshold, config.stalkingThreshold, config.confrontationThreshold);
	}
}

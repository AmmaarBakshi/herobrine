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

	/** Organic gain: scaled by {@code hauntSpeedMultiplier}. Use {@link #set} for exact changes. */
	public static void gain(ServerPlayer player, double amount) {
		ModAttachments.haunt(player).add(amount * HerobrineConfig.get().hauntSpeedMultiplier);
	}

	public static HauntPhase phase(ServerPlayer player) {
		HerobrineConfig config = HerobrineConfig.get();
		return HauntPhase.of(ModAttachments.haunt(player).levelInt(),
				config.sightingsThreshold, config.stalkingThreshold, config.confrontationThreshold);
	}
}

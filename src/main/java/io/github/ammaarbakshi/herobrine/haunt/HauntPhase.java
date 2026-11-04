package io.github.ammaarbakshi.herobrine.haunt;

import java.util.Locale;

/**
 * What Herobrine is allowed to do to a player, derived from their haunt level.
 * Each phase includes everything the earlier phases do.
 */
public enum HauntPhase {
	SIGNS,
	SIGHTINGS,
	STALKING,
	CONFRONTATION;

	public static final int MAX_LEVEL = 100;

	/** Thresholds are passed in (not read from config) to keep this pure and testable. */
	public static HauntPhase of(int level, int sightingsAt, int stalkingAt, int confrontationAt) {
		if (level >= confrontationAt) {
			return CONFRONTATION;
		}
		if (level >= stalkingAt) {
			return STALKING;
		}
		if (level >= sightingsAt) {
			return SIGHTINGS;
		}
		return SIGNS;
	}

	public String translationKey() {
		return "herobrine.phase." + name().toLowerCase(Locale.ROOT);
	}
}

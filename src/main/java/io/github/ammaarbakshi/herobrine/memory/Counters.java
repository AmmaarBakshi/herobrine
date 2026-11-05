package io.github.ammaarbakshi.herobrine.memory;

import java.util.Locale;

/**
 * Names of the numbers Herobrine keeps about a player. They are stable: they are saved under these
 * names, and dialogue.json refers to them in {@code "memory"} conditions (for example
 * {@code "deaths": "5.."}). Counters that are worked out when needed rather than stored (such as
 * {@link #DAYS_KNOWN}) are listed here too so authors find every name in one place.
 */
public final class Counters {
	/** Times the player died. */
	public static final String DEATHS = "deaths";
	/** Times he appeared, noticed or not. */
	public static final String APPEARANCES = "appearances";
	/** Times the player actually saw him (or turned around to face him). */
	public static final String ENCOUNTERS = "encounters";
	/** Appearances the player never noticed. */
	public static final String IGNORED = "ignored";
	/** Unnoticed appearances in a row; back to 0 the next time they see him. */
	public static final String IGNORED_STREAK = "ignored_streak";
	/** Times the player walked up to him and he left. */
	public static final String APPROACHED = "approached";
	/** Times the player looked straight at him until he left. */
	public static final String STARED = "stared";
	/** Times he left because the player looked away. */
	public static final String LOOKED_AWAY = "looked_away";
	/** Voice lines he has spoken to this player. */
	public static final String LINES = "lines";
	/** Boss fights he noticed (Wither, Ender Dragon, Warden), all kinds together. */
	public static final String BOSS_ENCOUNTERS = "boss_encounters";
	/** Highest equipment tier he has commented on, as {@link GearTier#ordinal()}. */
	public static final String GEAR_TIER = "gear_tier";
	/** Nights the player was online for, start to finish, and survived. */
	public static final String NIGHTS = "nights";
	/** Minutes spent underground. */
	public static final String UNDERGROUND_MINUTES = "underground_minutes";

	// --- Derived: computed when a line is chosen, never stored. -------------------------------

	/** In-game days since they first saw him (0 before that). */
	public static final String DAYS_KNOWN = "days_known";
	/** Real minutes (at 20 TPS) since they last saw him; very large if never. */
	public static final String MINUTES_SINCE_ENCOUNTER = "minutes_since_encounter";
	/** Milestones he has recognised. */
	public static final String MILESTONES = "milestones";
	/** Mirror fights, from the encounter history. */
	public static final String CONFRONTATIONS = "confrontations";
	public static final String MIRROR_WINS = "mirror_wins";
	public static final String MIRROR_LOSSES = "mirror_losses";
	/** The player's haunt level (0-100). */
	public static final String HAUNT = "haunt";

	private Counters() {
	}

	/** Encounters with one boss kind, e.g. {@code boss.wither}. */
	public static String boss(BossKind kind) {
		return "boss." + kind.id();
	}

	/** Kills of one boss kind, e.g. {@code kill.ender_dragon}. */
	public static String kill(BossKind kind) {
		return "kill." + kind.id();
	}

	static String normalize(String key) {
		return key.toLowerCase(Locale.ROOT);
	}
}

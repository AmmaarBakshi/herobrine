package io.github.ammaarbakshi.herobrine.mirror;

import java.util.Locale;

/**
 * Every scalar habit the recorder learns. {@code core} traits are sampled often enough during
 * normal fighting to define profile confidence; the rest (heals, escapes) are rare events.
 */
public enum TraitId {
	// Melee
	AVG_ATTACK_CHARGE(Group.MELEE, 0.08, true, 0.9),
	CRIT_RATE(Group.MELEE, 0.08, true, 0.2),
	SPRINT_HIT_RATE(Group.MELEE, 0.08, true, 0.3),
	// Ranged
	RANGED_RATIO(Group.RANGED, 0.08, true, 0.1),
	BOW_DRAW_TICKS(Group.RANGED, 0.15, false, 20.0),
	// Movement
	STRAFE_BIAS(Group.MOVEMENT, 0.06, true, 0.0),
	STRAFE_INTENSITY(Group.MOVEMENT, 0.06, true, 0.3),
	PREFERRED_RANGE(Group.MOVEMENT, 0.06, true, 2.5),
	JUMP_FREQUENCY(Group.MOVEMENT, 0.06, true, 0.3),
	// Defense
	SHIELD_USE_RATE(Group.DEFENSE, 0.1, true, 0.0),
	SHIELD_REACTION_TICKS(Group.DEFENSE, 0.15, false, 10.0),
	HEAL_THRESHOLD(Group.DEFENSE, 0.15, false, 0.35),
	/** Mean absolute deviation of heal HP fraction: low = heals at a predictable point. */
	HEAL_THRESHOLD_SPREAD(Group.DEFENSE, 0.15, false, 0.25),
	RETREAT_THRESHOLD(Group.DEFENSE, 0.15, false, 0.3),
	// Escape
	PILLAR_RATE(Group.ESCAPE, 0.15, false, 0.0),
	PEARL_ESCAPE_RATE(Group.ESCAPE, 0.15, false, 0.0),
	WATER_CLUTCH_RATE(Group.ESCAPE, 0.15, false, 0.0);

	public enum Group {
		MELEE, RANGED, MOVEMENT, DEFENSE, ESCAPE
	}

	private final Group group;
	private final double alpha;
	private final boolean core;
	private final double fallback;

	TraitId(Group group, double alpha, boolean core, double fallback) {
		this.group = group;
		this.alpha = alpha;
		this.core = core;
		this.fallback = fallback;
	}

	public Group group() {
		return group;
	}

	/** EMA weight: smaller = longer memory. Rare events use a larger weight so they adapt. */
	public double alpha() {
		return alpha;
	}

	public boolean core() {
		return core;
	}

	/** What Herobrine assumes while this habit is still unknown. */
	public double fallback() {
		return fallback;
	}

	/** Stable lower-camel-case key used in saved data and commands, e.g. {@code strafeBias}. */
	public String key() {
		String[] parts = name().toLowerCase(Locale.ROOT).split("_");
		StringBuilder sb = new StringBuilder(parts[0]);
		for (int i = 1; i < parts.length; i++) {
			sb.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i], 1, parts[i].length());
		}
		return sb.toString();
	}

	public static TraitId byKey(String key) {
		for (TraitId id : values()) {
			if (id.key().equals(key)) {
				return id;
			}
		}
		return null;
	}
}

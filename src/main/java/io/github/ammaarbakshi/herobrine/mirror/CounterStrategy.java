package io.github.ammaarbakshi.herobrine.mirror;

import java.util.Locale;

/**
 * How Herobrine answers each habit once he has seen it. The entity AI asks the active
 * {@link CounterPlan} which of these apply and acts on them; this enum holds the pure decisions.
 */
public enum CounterStrategy {
	/** Shields: bring an axe and swing it at a raised shield to disable it (vanilla mechanic). */
	SHIELD_BREAKER(Habit.SHIELD),
	/** Strafing: lead attacks and approach toward the side the player always strafes to. */
	STRAFE_LEAD(Habit.STRAFE),
	/** Pillaring: knock the pillar out from below and carry a bow for players out of reach. */
	ANTI_PILLAR(Habit.PILLAR),
	/** Bow kiting: close distance fast, zig-zagging to spoil aim. */
	GAP_CLOSER(Habit.BOW_KITE),
	/** Healing at a fixed HP: burst hardest just above that threshold. */
	HEAL_BURST(Habit.FIXED_HEAL),
	/** Pearling away: follow to where the pearl lands. */
	PEARL_FOLLOW(Habit.PEARL);

	private final Habit habit;

	CounterStrategy(Habit habit) {
		this.habit = habit;
	}

	public Habit habit() {
		return habit;
	}

	public static CounterStrategy forHabit(Habit habit) {
		for (CounterStrategy s : values()) {
			if (s.habit == habit) {
				return s;
			}
		}
		throw new IllegalArgumentException("No counter for " + habit);
	}

	public String translationKey() {
		return "herobrine.counter." + name().toLowerCase(Locale.ROOT);
	}

	/**
	 * Strafe lead: which side to offset toward, -1 left / +1 right relative to the line between
	 * them, or 0 when the player has no clear bias.
	 */
	public static int strafeLeadSide(double strafeBias) {
		if (Math.abs(strafeBias) < 0.2) {
			return 0;
		}
		return strafeBias > 0 ? 1 : -1;
	}

	/**
	 * Heal burst window: true while the target is just above the HP fraction at which they
	 * usually heal, i.e. the last chance to land damage before they top up.
	 */
	public static boolean inHealBurstWindow(double targetHealthFraction, double healThreshold) {
		return targetHealthFraction > healThreshold && targetHealthFraction <= healThreshold + 0.2;
	}
}

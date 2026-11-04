package io.github.ammaarbakshi.herobrine.mirror;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Reads a {@link PlayerProfile}: how well Herobrine knows the player, and what they lean on. */
public final class ProfileAnalyzer {
	private ProfileAnalyzer() {
	}

	/** Rare events (heals, pearls, pillars) can't be expected to reach the full sample count. */
	public static int rareMinSamples(int minSamples) {
		return Math.max(3, minSamples / 4);
	}

	/**
	 * Fraction of core habits that are known. The weapon preference counts as one core habit,
	 * since mirroring the right weapon matters as much as any single number.
	 */
	public static double confidence(PlayerProfile profile, int minSamples) {
		int total = 1;
		int known = profile.weaponPref().known(minSamples) ? 1 : 0;
		for (TraitId id : TraitId.values()) {
			if (id.core()) {
				total++;
				if (profile.trait(id).known(minSamples)) {
					known++;
				}
			}
		}
		return (double) known / total;
	}

	/** How strongly the player relies on each habit, 0..1; 0 while the evidence is too thin. */
	public static Map<Habit, Double> reliance(PlayerProfile profile, int minSamples) {
		int rare = rareMinSamples(minSamples);
		EnumMap<Habit, Double> out = new EnumMap<>(Habit.class);

		out.put(Habit.SHIELD, known(profile, TraitId.SHIELD_USE_RATE, minSamples)
				? clamp01(profile.trait(TraitId.SHIELD_USE_RATE).value()) : 0.0);

		out.put(Habit.STRAFE, known(profile, TraitId.STRAFE_BIAS, minSamples) && known(profile, TraitId.STRAFE_INTENSITY, minSamples)
				? clamp01(Math.abs(profile.trait(TraitId.STRAFE_BIAS).value())
						* Math.min(1.0, profile.trait(TraitId.STRAFE_INTENSITY).value() * 1.5))
				: 0.0);

		out.put(Habit.PILLAR, known(profile, TraitId.PILLAR_RATE, rare)
				? clamp01(profile.trait(TraitId.PILLAR_RATE).value()) : 0.0);

		out.put(Habit.BOW_KITE, known(profile, TraitId.RANGED_RATIO, minSamples)
				? clamp01(profile.trait(TraitId.RANGED_RATIO).value()
						* clamp((profile.value(TraitId.PREFERRED_RANGE, minSamples) - 3.0) / 9.0, 0.3, 1.0))
				: 0.0);

		// A fixed heal point is only exploitable if it is consistent: low spread = high reliance.
		out.put(Habit.FIXED_HEAL, known(profile, TraitId.HEAL_THRESHOLD, rare) && known(profile, TraitId.HEAL_THRESHOLD_SPREAD, rare)
				? clamp01(1.0 - profile.trait(TraitId.HEAL_THRESHOLD_SPREAD).value() / 0.2) * 0.9
				: 0.0);

		out.put(Habit.PEARL, known(profile, TraitId.PEARL_ESCAPE_RATE, rare)
				? clamp01(profile.trait(TraitId.PEARL_ESCAPE_RATE).value()) : 0.0);
		return out;
	}

	/** The {@code n} habits relied on most, strongest first, ignoring anything below {@code minReliance}. */
	public static List<Habit> topHabits(PlayerProfile profile, int minSamples, int n, double minReliance) {
		Map<Habit, Double> reliance = reliance(profile, minSamples);
		List<Habit> habits = new ArrayList<>();
		reliance.entrySet().stream()
				.filter(e -> e.getValue() >= minReliance)
				.sorted(Map.Entry.<Habit, Double>comparingByValue(Comparator.reverseOrder())
						.thenComparing(e -> e.getKey().ordinal()))
				.limit(n)
				.forEach(e -> habits.add(e.getKey()));
		return habits;
	}

	private static boolean known(PlayerProfile profile, TraitId id, int minSamples) {
		return profile.trait(id).known(minSamples);
	}

	private static double clamp01(double v) {
		return clamp(v, 0.0, 1.0);
	}

	private static double clamp(double v, double min, double max) {
		return Math.max(min, Math.min(max, v));
	}
}

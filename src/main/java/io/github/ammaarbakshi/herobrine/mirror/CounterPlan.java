package io.github.ammaarbakshi.herobrine.mirror;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The adaptive half of a fight: which counters are active and how strongly Herobrine blends
 * countering into mirroring. Built once at the start of each confrontation.
 *
 * @param counterWeight probability (0 to MAX_WEIGHT) that a tactical decision counters instead of mirrors
 * @param active        counters in effect, strongest habit first
 */
public record CounterPlan(double counterWeight, List<CounterStrategy> active) {
	public static final double WEIGHT_PER_ENCOUNTER = 0.15;
	public static final double MAX_WEIGHT = 0.6;
	/** Habits weaker than this aren't worth countering. */
	public static final double MIN_RELIANCE = 0.25;
	public static final int MAX_COUNTERS = 2;

	public static final CounterPlan NONE = new CounterPlan(0.0, List.of());

	public CounterPlan {
		active = List.copyOf(active);
	}

	public static double weightFor(int previousEncounters) {
		return Math.min(MAX_WEIGHT, WEIGHT_PER_ENCOUNTER * Math.max(0, previousEncounters));
	}

	/** @param focus habits to counter, normally the top habits recorded after the last fight */
	public static CounterPlan create(List<Habit> focus, int previousEncounters, boolean adaptiveEnabled) {
		double weight = adaptiveEnabled ? weightFor(previousEncounters) : 0.0;
		if (weight <= 0.0 || focus.isEmpty()) {
			return new CounterPlan(weight, List.of());
		}
		return new CounterPlan(weight, focus.stream().limit(MAX_COUNTERS).map(CounterStrategy::forHabit).toList());
	}

	/** Derives the focus habits straight from a profile. */
	public static CounterPlan fromProfile(PlayerProfile profile, int minSamples, int previousEncounters, boolean adaptiveEnabled) {
		return create(ProfileAnalyzer.topHabits(profile, minSamples, MAX_COUNTERS, MIN_RELIANCE), previousEncounters, adaptiveEnabled);
	}

	public boolean has(CounterStrategy strategy) {
		return active.contains(strategy);
	}

	public Set<CounterStrategy> activeSet() {
		return active.isEmpty() ? EnumSet.noneOf(CounterStrategy.class) : EnumSet.copyOf(active);
	}
}

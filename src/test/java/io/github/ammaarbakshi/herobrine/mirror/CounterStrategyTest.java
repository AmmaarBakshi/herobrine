package io.github.ammaarbakshi.herobrine.mirror;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class CounterStrategyTest {
	@ParameterizedTest
	@CsvSource({"0, 0.0", "1, 0.15", "2, 0.30", "3, 0.45", "4, 0.60", "5, 0.60", "40, 0.60"})
	void counterWeightGrowsAndCaps(int encounters, double expected) {
		assertEquals(expected, CounterPlan.weightFor(encounters), 1e-9);
	}

	@Test
	void firstFightIsPureMirror() {
		CounterPlan plan = CounterPlan.fromProfile(ProfilePresets.SHIELD_TURTLE.build(), 20, 0, true);
		assertEquals(0.0, plan.counterWeight());
		assertTrue(plan.active().isEmpty());
	}

	@Test
	void secondFightAgainstShieldUserBringsTheAxe() {
		CounterPlan plan = CounterPlan.fromProfile(ProfilePresets.SHIELD_TURTLE.build(), 20, 1, true);
		assertEquals(0.15, plan.counterWeight(), 1e-9);
		assertTrue(plan.has(CounterStrategy.SHIELD_BREAKER));
	}

	@Test
	void disablingAdaptiveCountersTurnsThemOff() {
		CounterPlan plan = CounterPlan.fromProfile(ProfilePresets.SHIELD_TURTLE.build(), 20, 3, false);
		assertEquals(0.0, plan.counterWeight());
		assertTrue(plan.active().isEmpty());
	}

	@Test
	void atMostTwoCounters() {
		CounterPlan plan = CounterPlan.create(List.of(Habit.PEARL, Habit.SHIELD, Habit.STRAFE), 2, true);
		assertEquals(List.of(CounterStrategy.PEARL_FOLLOW, CounterStrategy.SHIELD_BREAKER), plan.active());
	}

	@ParameterizedTest
	@EnumSource(Habit.class)
	void everyHabitHasACounter(Habit habit) {
		assertEquals(habit, CounterStrategy.forHabit(habit).habit());
	}

	@ParameterizedTest
	@CsvSource({"0.6, 1", "-0.45, -1", "0.1, 0", "-0.19, 0"})
	void strafeLeadFollowsBias(double bias, int side) {
		assertEquals(side, CounterStrategy.strafeLeadSide(bias));
	}

	@Test
	void healBurstWindowSitsJustAboveThreshold() {
		assertTrue(CounterStrategy.inHealBurstWindow(0.45, 0.3));
		assertFalse(CounterStrategy.inHealBurstWindow(0.25, 0.3));
		assertFalse(CounterStrategy.inHealBurstWindow(0.9, 0.3));
	}
}

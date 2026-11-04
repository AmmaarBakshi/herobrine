package io.github.ammaarbakshi.herobrine.mirror;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class ProfileAnalyzerTest {
	private static final int MIN = 20;

	@Test
	void emptyProfileHasNoConfidence() {
		assertEquals(0.0, ProfileAnalyzer.confidence(new PlayerProfile(), MIN));
	}

	@Test
	void presetsAreFullyKnown() {
		for (ProfilePresets preset : ProfilePresets.values()) {
			assertEquals(1.0, ProfileAnalyzer.confidence(preset.build(), MIN), preset.id());
		}
	}

	@Test
	void confidenceCountsOnlyCoreTraitsWithEnoughSamples() {
		PlayerProfile p = new PlayerProfile();
		long core = java.util.Arrays.stream(TraitId.values()).filter(TraitId::core).count();
		double total = core + 1; // + weapon preference

		p.setTrait(TraitId.STRAFE_BIAS, new Trait(0.5, MIN));
		p.setTrait(TraitId.CRIT_RATE, new Trait(0.5, MIN - 1)); // not yet known
		p.setTrait(TraitId.PILLAR_RATE, new Trait(1.0, 500));   // rare trait, not core
		assertEquals(1 / total, ProfileAnalyzer.confidence(p, MIN), 1e-9);

		for (int i = 0; i < MIN; i++) {
			p.observeWeapon(WeaponType.SWORD);
		}
		assertEquals(2 / total, ProfileAnalyzer.confidence(p, MIN), 1e-9);
	}

	@Test
	void shieldTurtleReliesOnShieldFirst() {
		List<Habit> top = ProfileAnalyzer.topHabits(ProfilePresets.SHIELD_TURTLE.build(), MIN, 2, CounterPlan.MIN_RELIANCE);
		assertEquals(Habit.SHIELD, top.getFirst());
	}

	@Test
	void bowKiterReliesOnBowAndStrafe() {
		List<Habit> top = ProfileAnalyzer.topHabits(ProfilePresets.BOW_KITER.build(), MIN, 2, CounterPlan.MIN_RELIANCE);
		assertEquals(List.of(Habit.BOW_KITE, Habit.STRAFE), top);
	}

	@Test
	void pillarCowardReliesOnPillaring() {
		List<Habit> top = ProfileAnalyzer.topHabits(ProfilePresets.PILLAR_COWARD.build(), MIN, 2, CounterPlan.MIN_RELIANCE);
		assertEquals(Habit.PILLAR, top.getFirst());
		assertTrue(top.contains(Habit.PEARL) || top.contains(Habit.STRAFE));
	}

	@Test
	void thinEvidenceYieldsNoHabits() {
		PlayerProfile p = new PlayerProfile();
		p.setTrait(TraitId.SHIELD_USE_RATE, new Trait(1.0, 3));
		assertEquals(0.0, ProfileAnalyzer.reliance(p, MIN).get(Habit.SHIELD));
		assertTrue(ProfileAnalyzer.topHabits(p, MIN, 2, CounterPlan.MIN_RELIANCE).isEmpty());
	}

	@Test
	void consistentHealerIsExploitable() {
		PlayerProfile p = new PlayerProfile();
		p.setTrait(TraitId.HEAL_THRESHOLD, new Trait(0.3, 10));
		p.setTrait(TraitId.HEAL_THRESHOLD_SPREAD, new Trait(0.02, 10));
		assertTrue(ProfileAnalyzer.reliance(p, MIN).get(Habit.FIXED_HEAL) > 0.7);

		p.setTrait(TraitId.HEAL_THRESHOLD_SPREAD, new Trait(0.3, 10));
		assertEquals(0.0, ProfileAnalyzer.reliance(p, MIN).get(Habit.FIXED_HEAL));
	}
}

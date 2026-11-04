package io.github.ammaarbakshi.herobrine.mirror;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

class TauntsTest {
	private static final int MIN = 20;

	private static List<String> ids(PlayerProfile profile) {
		return Taunts.candidates(profile, MIN).stream().map(Taunts.Taunt::id).toList();
	}

	@Test
	void emptyProfileOnlyGetsGenericLines() {
		PlayerProfile empty = new PlayerProfile();
		assertTrue(ids(empty).isEmpty());
		Taunts.Taunt taunt = Taunts.pick(empty, MIN, "", new Random(1));
		assertTrue(Taunts.GENERIC.contains(taunt.id()));
	}

	@Test
	void linesMatchTheHabitsTheyQuote() {
		List<String> kiter = ids(ProfilePresets.BOW_KITER.build());
		assertTrue(kiter.contains("strafe_right"), kiter.toString());
		assertTrue(kiter.contains("kiting"), kiter.toString());
		assertTrue(kiter.contains("heal_at"), kiter.toString());
		assertFalse(kiter.contains("strafe_left"));
		assertFalse(kiter.contains("spam"));

		List<String> spammer = ids(ProfilePresets.SWORD_SPAMMER.build());
		assertTrue(spammer.contains("spam"), spammer.toString());
		assertTrue(spammer.contains("close"), spammer.toString());
		assertFalse(spammer.contains("kiting"));

		List<String> coward = ids(ProfilePresets.PILLAR_COWARD.build());
		assertTrue(coward.contains("strafe_left"), coward.toString());
		assertTrue(coward.contains("pillar"), coward.toString());
		assertTrue(coward.contains("pearl"), coward.toString());
	}

	@Test
	void healLineQuotesTheirRealThreshold() {
		Taunts.Taunt heal = Taunts.candidates(ProfilePresets.BOW_KITER.build(), MIN).stream()
				.filter(t -> t.id().equals("heal_at")).findFirst().orElseThrow();
		// Bow kiter heals at half health: 5 hearts.
		assertEquals("5", heal.argument());
		assertEquals("herobrine.taunt.heal_at", heal.translationKey());
	}

	@Test
	void unpredictableHealersDontGetTheHealLine() {
		PlayerProfile profile = ProfilePresets.BOW_KITER.build();
		profile.setTrait(TraitId.HEAL_THRESHOLD_SPREAD, new Trait(0.3, 1000));
		assertFalse(ids(profile).contains("heal_at"));
	}

	@Test
	void neverRepeatsTheLastLine() {
		PlayerProfile profile = ProfilePresets.SWORD_SPAMMER.build();
		Random random = new Random(7);
		String previous = "";
		for (int i = 0; i < 50; i++) {
			Taunts.Taunt next = Taunts.pick(profile, MIN, previous, random);
			assertNotEquals(previous, next.id());
			previous = next.id();
		}
	}

	@Test
	void heartsReadLikeAPlayerWouldSayThem() {
		assertEquals("3.5", Taunts.hearts(0.35));
		assertEquals("3", Taunts.hearts(0.3));
		assertEquals("10", Taunts.hearts(1.0));
		assertEquals("0", Taunts.hearts(-0.2));
	}

	@Test
	void intervalsStayInRange() {
		Random random = new Random(3);
		for (int i = 0; i < 100; i++) {
			int interval = Taunts.nextInterval(random);
			assertTrue(interval >= Taunts.MIN_INTERVAL && interval <= Taunts.MAX_INTERVAL);
		}
	}
}

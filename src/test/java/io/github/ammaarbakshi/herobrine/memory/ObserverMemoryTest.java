package io.github.ammaarbakshi.herobrine.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import org.junit.jupiter.api.Test;

class ObserverMemoryTest {
	@Test
	void roundTripsEverything() {
		ObserverMemory memory = new ObserverMemory();
		memory.increment(Counters.DEATHS);
		memory.add(Counters.boss(BossKind.WITHER), 2);
		memory.set(Counters.GEAR_TIER, GearTier.DIAMOND.ordinal());
		memory.addMilestone(Milestone.FIRST_HOUSE);
		memory.recordAppearance(new Place("minecraft:overworld", 10, 64, -20, 1200));
		memory.recordEncounter(1300);
		memory.setHome(new Place("minecraft:overworld", 0, 70, 0, 900));
		memory.recordEvent(new WorldEvent("killed_wither", new Place("minecraft:the_nether", 1, 2, 3, 5000)));
		memory.recordTrigger("encounter", 1300);
		memory.recordLine("random.hi", "random", 1310);
		memory.markBaselined(700);

		JsonElement json = ObserverMemory.CODEC.encodeStart(JsonOps.INSTANCE, memory).getOrThrow();
		ObserverMemory decoded = ObserverMemory.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();

		assertEquals(memory.counters(), decoded.counters());
		assertEquals(memory.milestones(), decoded.milestones());
		assertEquals(memory.lastAppearance(), decoded.lastAppearance());
		assertEquals(memory.home(), decoded.home());
		assertEquals(memory.events(), decoded.events());
		assertEquals(memory.encounterTimes(), decoded.encounterTimes());
		assertEquals(1310, decoded.lastSaid("random.hi"));
		assertEquals(1310, decoded.categoryLastSaid("random"));
		assertEquals(1300, decoded.triggerLastFired("encounter"));
		assertTrue(decoded.recentlySaid("random.hi"));
		assertEquals(1300, decoded.firstMet());
		assertEquals(1310, decoded.lastSpoke());
		assertTrue(decoded.baselined());
		assertEquals(700, decoded.observedSince());
	}

	@Test
	void anEmptyOrDamagedSaveLoadsAsBlankMemory() {
		ObserverMemory empty = ObserverMemory.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{}")).getOrThrow();
		assertEquals(0, empty.counter(Counters.DEATHS));
		assertNull(empty.lastAppearance());
		assertFalse(empty.baselined());

		ObserverMemory damaged = ObserverMemory.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
				{"counters": {"deaths": 4}, "lastAppearance": "not a place", "events": 7, "baselined": true}""")).getOrThrow();
		assertEquals(4, damaged.counter(Counters.DEATHS));
		assertNull(damaged.lastAppearance());
		assertTrue(damaged.events().isEmpty());
		assertTrue(damaged.baselined());
	}

	@Test
	void encountersResetTheIgnoredStreakAndKeepRecentTimes() {
		ObserverMemory memory = new ObserverMemory();
		memory.recordIgnored();
		memory.recordIgnored();
		assertEquals(2, memory.counter(Counters.IGNORED_STREAK));
		for (int i = 1; i <= ObserverMemory.MAX_ENCOUNTER_TIMES + 3; i++) {
			memory.recordEncounter(i * 100L);
		}
		assertEquals(0, memory.counter(Counters.IGNORED_STREAK));
		assertEquals(2, memory.counter(Counters.IGNORED));
		assertEquals(ObserverMemory.MAX_ENCOUNTER_TIMES + 3, memory.counter(Counters.ENCOUNTERS));
		assertEquals(100L, memory.firstMet(), "the first meeting is never forgotten");
		List<Long> times = memory.encounterTimes();
		assertEquals(ObserverMemory.MAX_ENCOUNTER_TIMES, times.size());
		assertEquals((ObserverMemory.MAX_ENCOUNTER_TIMES + 3) * 100L, memory.lastEncounter());
	}

	@Test
	void recentLinesAreABoundedRing() {
		ObserverMemory memory = new ObserverMemory();
		for (int i = 0; i < ObserverMemory.RECENT_LINES + 2; i++) {
			memory.recordLine("random.l" + i, "random", i);
		}
		assertFalse(memory.recentlySaid("random.l0"));
		assertFalse(memory.recentlySaid("random.l1"));
		assertTrue(memory.recentlySaid("random.l2"));
		assertEquals(ObserverMemory.RECENT_LINES, memory.recentLines().size());
		assertEquals(0, memory.lastSaid("random.l0"), "older lines are still remembered for cooldowns");
		assertEquals(ObserverMemory.RECENT_LINES + 2, memory.counter(Counters.LINES));
	}

	@Test
	void milestonesAreRecognisedOnce() {
		ObserverMemory memory = new ObserverMemory();
		assertTrue(memory.addMilestone(Milestone.FIRST_DIAMONDS));
		assertFalse(memory.addMilestone(Milestone.FIRST_DIAMONDS));
		assertTrue(memory.hasMilestone(Milestone.FIRST_DIAMONDS));
	}

	@Test
	void gearTiersComeFromItemIds() {
		assertEquals(GearTier.WOODEN, GearTier.ofItemPath("wooden_pickaxe"));
		assertEquals(GearTier.STONE, GearTier.ofItemPath("stone_sword"));
		assertEquals(GearTier.IRON, GearTier.ofItemPath("iron_chestplate"));
		assertEquals(GearTier.DIAMOND, GearTier.ofItemPath("diamond_boots"));
		assertEquals(GearTier.NETHERITE, GearTier.ofItemPath("netherite_axe"));
		assertEquals(GearTier.COPPER, GearTier.ofItemPath("copper_spear"));
		assertEquals(GearTier.NONE, GearTier.ofItemPath("golden_sword"));
		assertEquals(GearTier.NONE, GearTier.ofItemPath("iron_ingot"));
		assertEquals(GearTier.NONE, GearTier.ofItemPath("diamond"));
		assertEquals(GearTier.NONE, GearTier.ofItemPath("pickaxe"));
		assertTrue(GearTier.DIAMOND.isAbove(GearTier.IRON));
	}
}

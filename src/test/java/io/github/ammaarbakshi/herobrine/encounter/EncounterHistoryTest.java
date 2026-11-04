package io.github.ammaarbakshi.herobrine.encounter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import org.junit.jupiter.api.Test;

import io.github.ammaarbakshi.herobrine.mirror.Habit;

class EncounterHistoryTest {
	@Test
	void countsWinsAndLosses() {
		EncounterHistory history = new EncounterHistory();
		history.add(new EncounterRecord(3, false, 600, 2, List.of(Habit.STRAFE)));
		history.add(new EncounterRecord(5, true, 900, 3, List.of(Habit.SHIELD, Habit.BOW_KITE)));
		assertEquals(2, history.total());
		assertEquals(1, history.wins());
		assertEquals(1, history.losses());
		assertEquals(List.of(Habit.SHIELD, Habit.BOW_KITE), history.lastFocus());
	}

	@Test
	void keepsOnlyRecentRecordsButCountsAll() {
		EncounterHistory history = new EncounterHistory();
		for (int i = 0; i < EncounterHistory.MAX_RECORDS + 5; i++) {
			history.add(new EncounterRecord(i, i % 2 == 0, 100, 0, List.of()));
		}
		assertEquals(EncounterHistory.MAX_RECORDS + 5, history.total());
		assertEquals(EncounterHistory.MAX_RECORDS, history.recent().size());
		assertEquals(5, history.recent().getFirst().day());
	}

	@Test
	void naturalConfrontationAtMostOncePerDay() {
		EncounterHistory history = new EncounterHistory();
		assertTrue(history.naturalAllowed(0));
		history.add(new EncounterRecord(7, false, 100, 0, List.of()));
		assertFalse(history.naturalAllowed(7));
		assertTrue(history.naturalAllowed(8));
	}

	@Test
	void roundTripsAndToleratesUnknownHabits() {
		EncounterHistory history = new EncounterHistory();
		history.add(new EncounterRecord(2, true, 1200, 1, List.of(Habit.PILLAR, Habit.PEARL)));
		JsonElement json = EncounterHistory.CODEC.encodeStart(JsonOps.INSTANCE, history).getOrThrow();
		EncounterHistory decoded = EncounterHistory.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		assertEquals(history.total(), decoded.total());
		assertEquals(history.wins(), decoded.wins());
		assertEquals(history.recent(), decoded.recent());

		// A habit removed in a later version must not wipe the player's history.
		JsonElement future = JsonParser.parseString(
				"{\"total\":1,\"wins\":0,\"recent\":[{\"day\":1,\"won\":false,\"focus\":[\"strafe\",\"telepathy\"]}]}");
		EncounterHistory tolerant = EncounterHistory.CODEC.parse(JsonOps.INSTANCE, future).getOrThrow();
		assertEquals(1, tolerant.total());
		assertEquals(1, tolerant.recent().size());
	}
}

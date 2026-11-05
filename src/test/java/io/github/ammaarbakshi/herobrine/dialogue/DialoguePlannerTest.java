package io.github.ammaarbakshi.herobrine.dialogue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.ammaarbakshi.herobrine.memory.ObserverMemory;

class DialoguePlannerTest {
	private static final long NOW = 100_000_000L;

	private static DialogueLine line(String id, String category) {
		return new DialogueLine(id, category, "", id, "", 0, 10, 1.0, 0, false, Set.of(), Set.of(), Map.of());
	}

	private static final TriggerRule ENCOUNTER = new TriggerRule("encounter", List.of("fourth_wall", "random"),
			1.0, 0, 0, 0, 10, 0, false);

	@Test
	void rareCategoriesComeFirstButKeepTheirOwnChanceAndCooldown() {
		DialogueCatalog catalog = new DialogueCatalog(List.of(line("fourth_wall.voice", "fourth_wall"), line("random.hi", "random")),
				Map.of("encounter", ENCOUNTER), Map.of("fourth_wall", new CategoryRule("fourth_wall", 0.1, 3600)));
		int fourthWall = 0;
		Random random = new Random(7);
		for (int i = 0; i < 1000; i++) {
			DialoguePlanner.Plan plan = DialoguePlanner.choose(catalog, ENCOUNTER, Set.of(), name -> 0, DialogueHistory.NONE,
					NOW, random, false);
			if (plan.line() != null && plan.line().category().equals("fourth_wall")) {
				fourthWall++;
			}
		}
		assertTrue(fourthWall > 60 && fourthWall < 140, "expected ~10% fourth-wall lines, got " + fourthWall);

		ObserverMemory memory = new ObserverMemory();
		memory.recordLine("fourth_wall.voice", "fourth_wall", NOW - 100 * 20);
		for (int i = 0; i < 200; i++) {
			DialoguePlanner.Plan plan = DialoguePlanner.choose(catalog, ENCOUNTER, Set.of(), name -> 0, memory, NOW, random, false);
			assertEquals("random.hi", plan.line().id(), "the fourth-wall category is on its hour-long cooldown");
		}
	}

	@Test
	void triggerChanceAndCooldownAreCheckedWhenItHappens() {
		TriggerRule weather = new TriggerRule("weather", List.of("weather"), 0.5, 600, 1, 2, 30, 0, false);
		int accepted = 0;
		Random random = new Random(7);
		for (int i = 0; i < 1000; i++) {
			if (DialoguePlanner.accepts(weather, DialogueHistory.NONE, NOW, random, false)) {
				accepted++;
			}
		}
		assertTrue(accepted > 420 && accepted < 580, "expected ~50%, got " + accepted);

		ObserverMemory memory = new ObserverMemory();
		memory.recordTrigger("weather", NOW - 300 * 20);
		TriggerRule always = new TriggerRule("weather", List.of("weather"), 1.0, 600, 1, 2, 30, 0, false);
		assertFalse(DialoguePlanner.accepts(always, memory, NOW, new Random(1), false));
		assertTrue(DialoguePlanner.accepts(always, memory, NOW, new Random(1), true), "forced ignores the cooldown");
		memory.recordTrigger("weather", NOW - 601 * 20);
		assertTrue(DialoguePlanner.accepts(always, memory, NOW, new Random(1), false));
	}

	@Test
	void forcedStillNeedsALineThatFits() {
		DialogueLine iron = new DialogueLine("progression.iron", "progression", "", "Iron.", "", 0, 10, 1.0, 0, false,
				Set.of("tier:iron"), Set.of(), Map.of());
		TriggerRule rule = new TriggerRule("progression", List.of("progression"), 1.0, 0, 0, 0, 10, 0, false);
		DialogueCatalog catalog = new DialogueCatalog(List.of(iron), Map.of("progression", rule), Map.of());
		assertNull(DialoguePlanner.choose(catalog, rule, Set.of("tier:wooden"), n -> 0, DialogueHistory.NONE, NOW, new Random(1), true).line());
		assertEquals(iron, DialoguePlanner.choose(catalog, rule, Set.of("tier:iron"), n -> 0, DialogueHistory.NONE, NOW, new Random(1), true).line());
	}
}

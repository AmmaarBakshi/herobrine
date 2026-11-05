package io.github.ammaarbakshi.herobrine.dialogue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.ammaarbakshi.herobrine.memory.ObserverMemory;

class DialogueSelectorTest {
	private static final long NOW = 100_000_000L;

	static DialogueLine line(String id, int priority, int weight) {
		return new DialogueLine(id, "random", DialogueLine.defaultAudio(id, "random"), id, "", priority, weight, 1.0, 0, false,
				Set.of(), Set.of(), Map.of());
	}

	static DialogueLine withMemory(String id, String counter, String range) {
		return new DialogueLine(id, "random", "", id, "", 0, 10, 1.0, 0, false, Set.of(), Set.of(),
				Map.of(counter, IntRange.parse(range)));
	}

	private static Optional<DialogueLine> pick(List<DialogueLine> lines, Set<String> tags, Map<String, Integer> counters,
			DialogueHistory history, long seed) {
		return pick(lines, tags, counters, history, new Random(seed));
	}

	/** One generator for a whole loop: java.util.Random's first draw barely varies across nearby seeds. */
	private static Optional<DialogueLine> pick(List<DialogueLine> lines, Set<String> tags, Map<String, Integer> counters,
			DialogueHistory history, Random random) {
		return DialogueSelector.pick(lines, tags, name -> counters.getOrDefault(name, 0), history, NOW, random);
	}

	@Test
	void onlyTheHighestPriorityLinesThatFitAreConsidered() {
		List<DialogueLine> lines = List.of(line("random.a", 0, 10), line("random.b", 5, 10), line("random.c", 5, 10));
		Set<String> seen = new HashSet<>();
		Random random = new Random(7);
		for (int i = 0; i < 200; i++) {
			seen.add(pick(lines, Set.of(), Map.of(), DialogueHistory.NONE, random).orElseThrow().id());
		}
		assertEquals(Set.of("random.b", "random.c"), seen);
	}

	@Test
	void weightsSkewTheDraw() {
		List<DialogueLine> lines = List.of(line("random.common", 0, 90), line("random.rare", 0, 10));
		int common = 0;
		Random random = new Random(7);
		for (int i = 0; i < 2000; i++) {
			if (pick(lines, Set.of(), Map.of(), DialogueHistory.NONE, random).orElseThrow().id().equals("random.common")) {
				common++;
			}
		}
		assertTrue(common > 1650 && common < 1950, "expected ~90% common, got " + common);
	}

	@Test
	void memoryRangesSelectTheRightEncounterGreeting() {
		List<DialogueLine> lines = List.of(
				withMemory("random.first", "encounters", "1"),
				withMemory("random.fifth", "encounters", "5..9"),
				withMemory("random.tenth", "encounters", "10.."));
		assertEquals("random.first", pick(lines, Set.of(), Map.of("encounters", 1), DialogueHistory.NONE, 1).orElseThrow().id());
		assertEquals("random.fifth", pick(lines, Set.of(), Map.of("encounters", 7), DialogueHistory.NONE, 1).orElseThrow().id());
		assertEquals("random.tenth", pick(lines, Set.of(), Map.of("encounters", 40), DialogueHistory.NONE, 1).orElseThrow().id());
		assertTrue(pick(lines, Set.of(), Map.of("encounters", 3), DialogueHistory.NONE, 1).isEmpty());
	}

	@Test
	void tagsMustAllBePresentAndNotTagsAbsent() {
		DialogueLine iron = new DialogueLine("progression.iron", "progression", "", "Iron.", "", 0, 10, 1.0, 0, false,
				Set.of("tier:iron"), Set.of("dimension:the_nether"), Map.of());
		List<DialogueLine> lines = List.of(iron);
		assertTrue(pick(lines, Set.of("tier:diamond"), Map.of(), DialogueHistory.NONE, 1).isEmpty());
		assertTrue(pick(lines, Set.of("tier:iron", "dimension:the_nether"), Map.of(), DialogueHistory.NONE, 1).isEmpty());
		assertEquals(iron, pick(lines, Set.of("tier:iron", "dimension:overworld"), Map.of(), DialogueHistory.NONE, 1).orElseThrow());
	}

	@Test
	void onceLinesAreNeverRepeatedAndCooldownsHold() {
		DialogueLine once = new DialogueLine("random.once", "random", "", "x", "", 0, 10, 1.0, 0, true, Set.of(), Set.of(), Map.of());
		DialogueLine cooled = new DialogueLine("random.cooled", "random", "", "y", "", 0, 10, 1.0, 600, false, Set.of(), Set.of(), Map.of());
		ObserverMemory memory = new ObserverMemory();
		memory.recordLine("random.once", "random", NOW - 10_000_000L);
		// Said 20 s ago with a 600 s cooldown.
		memory.recordLine("random.cooled", "random", NOW - 20 * 20);
		for (int i = 0; i < ObserverMemory.RECENT_LINES; i++) {
			memory.recordLine("random.filler" + i, "random", NOW - 30 * 20);
		}
		assertTrue(pick(List.of(once, cooled), Set.of(), Map.of(), memory, 1).isEmpty());

		ObserverMemory later = new ObserverMemory();
		later.recordLine("random.cooled", "random", NOW - 601 * 20);
		for (int i = 0; i < ObserverMemory.RECENT_LINES; i++) {
			later.recordLine("random.filler" + i, "random", NOW - 30 * 20);
		}
		assertEquals(cooled, pick(List.of(cooled), Set.of(), Map.of(), later, 1).orElseThrow());
	}

	@Test
	void recentLinesAreSkippedEvenWithoutACooldown() {
		DialogueLine a = line("random.a", 0, 10);
		DialogueLine b = line("random.b", 0, 10);
		ObserverMemory memory = new ObserverMemory();
		memory.recordLine("random.a", "random", NOW - 100);
		Random random = new Random(7);
		for (int i = 0; i < 50; i++) {
			assertEquals(b, pick(List.of(a, b), Set.of(), Map.of(), memory, random).orElseThrow());
		}
		memory.recordLine("random.b", "random", NOW - 50);
		assertTrue(pick(List.of(a, b), Set.of(), Map.of(), memory, 1).isEmpty(), "rather silent than repeating himself");
	}

	@Test
	void zeroWeightDisablesALine() {
		assertTrue(pick(List.of(line("random.off", 0, 0)), Set.of(), Map.of(), DialogueHistory.NONE, 1).isEmpty());
	}

	@Test
	void lineChanceIsRolled() {
		DialogueLine sometimes = new DialogueLine("random.sometimes", "random", "", "z", "", 0, 10, 0.25, 0, false, Set.of(), Set.of(), Map.of());
		Map<Boolean, Integer> counts = new HashMap<>();
		Random random = new Random(7);
		for (int i = 0; i < 2000; i++) {
			counts.merge(pick(List.of(sometimes), Set.of(), Map.of(), DialogueHistory.NONE, random).isPresent(), 1, Integer::sum);
		}
		int hits = counts.getOrDefault(true, 0);
		assertTrue(hits > 400 && hits < 600, "expected ~25%, got " + hits);
	}
}

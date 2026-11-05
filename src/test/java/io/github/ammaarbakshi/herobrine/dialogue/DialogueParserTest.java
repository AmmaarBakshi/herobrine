package io.github.ammaarbakshi.herobrine.dialogue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

class DialogueParserTest {
	@Test
	void readsAFullLineAndFillsDefaults() {
		List<String> warnings = new ArrayList<>();
		DialogueCatalog catalog = new DialogueParser.Builder(warnings::add)
				.addLines("test", JsonParser.parseString("""
						{"lines": [
						  {"id": "progression.iron", "text": "Iron.", "note": "Amused.", "priority": 2, "weight": 5,
						   "chance": 0.5, "cooldown": 600, "once": true,
						   "when": {"tags": ["tier:iron"], "not_tags": ["dimension:the_end"], "memory": {"deaths": "..3", "encounters": 2}}},
						  {"id": "random.hi", "text": "Hi.", "audio": "voice/custom/hello"}
						]}"""))
				.build();
		DialogueLine iron = catalog.line("progression.iron").orElseThrow();
		assertEquals("progression", iron.category(), "category defaults to the id prefix");
		assertEquals("herobrine:voice/progression/iron", iron.audio(), "audio defaults to voice/<category>/<name>");
		assertEquals("assets/herobrine/sounds/voice/progression/iron.ogg", iron.audioFile());
		assertEquals(2, iron.priority());
		assertEquals(5, iron.weight());
		assertEquals(0.5, iron.chance());
		assertEquals(600, iron.cooldownSeconds());
		assertTrue(iron.once());
		assertTrue(iron.tags().contains("tier:iron"));
		assertTrue(iron.notTags().contains("dimension:the_end"));
		assertEquals(IntRange.atMost(3), iron.memory().get("deaths"));
		assertEquals(IntRange.exactly(2), iron.memory().get("encounters"));
		assertEquals("herobrine:voice/custom/hello", catalog.line("random.hi").orElseThrow().audio());
		assertTrue(warnings.isEmpty(), warnings.toString());
	}

	@Test
	void brokenLinesAreSkippedWithAWarningAndTheRestLoads() {
		List<String> warnings = new ArrayList<>();
		DialogueCatalog catalog = new DialogueParser.Builder(warnings::add)
				.addLines("pack/dialogue.json", JsonParser.parseString("""
						{"lines": [
						  {"id": "Random.Bad Id", "text": "x"},
						  {"id": "random.notext"},
						  {"id": "random.badrange", "text": "x", "when": {"memory": {"deaths": "lots"}}},
						  {"id": "random.badaudio", "text": "x", "audio": "voice/Random/hello.ogg "},
						  {"id": "random.good", "text": "Fine."}
						]}"""))
				.build();
		assertEquals(1, catalog.size());
		assertTrue(catalog.line("random.good").isPresent());
		assertEquals(4, warnings.size(), warnings.toString());
		assertTrue(warnings.getFirst().startsWith("pack/dialogue.json: line 1"), warnings.getFirst());
	}

	@Test
	void packsStackAndReplaceLikeTags() {
		DialogueParser.Builder builder = new DialogueParser.Builder(w -> { });
		builder.addLines("base", JsonParser.parseString("""
				{"lines": [{"id": "random.a", "text": "A"}, {"id": "random.b", "text": "B"}]}"""));
		builder.addLines("addon", JsonParser.parseString("""
				{"lines": [{"id": "random.b", "text": "B2"}, {"id": "random.c", "text": "C"}]}"""));
		DialogueCatalog stacked = builder.build();
		assertEquals(3, stacked.size());
		assertEquals("B2", stacked.line("random.b").orElseThrow().text());

		builder.addLines("override", JsonParser.parseString("""
				{"replace": true, "lines": [{"id": "random.only", "text": "Only"}]}"""));
		DialogueCatalog replaced = builder.build();
		assertEquals(1, replaced.size());
		assertFalse(replaced.line("random.a").isPresent());
	}

	@Test
	void triggersAndCategoriesParse() {
		List<String> warnings = new ArrayList<>();
		DialogueCatalog catalog = new DialogueParser.Builder(warnings::add)
				.addLines("lines", JsonParser.parseString("""
						{"lines": [{"id": "random.hi", "text": "Hi."}, {"id": "fourth_wall.dev", "text": "Developer."}]}"""))
				.addTriggers("triggers", JsonParser.parseString("""
						{"triggers": {
						   "encounter": {"categories": ["fourth_wall", "random"], "chance": 0.6, "cooldown": 30,
						                 "delay": [0.5, 2], "patience": 12, "appear": 0.25, "in_combat": true},
						   "weather": {"categories": ["weather"], "delay": 4}
						 },
						 "categories": {"fourth_wall": {"chance": 0.04, "cooldown": 14400}}}"""))
				.build();
		TriggerRule encounter = catalog.trigger("encounter");
		assertEquals(List.of("fourth_wall", "random"), encounter.categories());
		assertEquals(0.6, encounter.chance());
		assertEquals(30, encounter.cooldownSeconds());
		assertEquals(10, encounter.minDelayTicks());
		assertEquals(40, encounter.maxDelayTicks());
		assertEquals(12, encounter.patienceSeconds());
		assertEquals(0.25, encounter.appearChance());
		assertTrue(encounter.inCombat());
		assertEquals(80, catalog.trigger("weather").minDelayTicks());
		assertEquals(0.04, catalog.categoryRule("fourth_wall").chance());
		assertEquals(14400, catalog.categoryRule("fourth_wall").cooldownSeconds());
		assertEquals(1.0, catalog.categoryRule("random").chance(), "unlisted categories are unrestricted");
		assertTrue(warnings.stream().anyMatch(w -> w.contains("\"weather\"") && w.contains("no lines")), warnings.toString());
	}
}

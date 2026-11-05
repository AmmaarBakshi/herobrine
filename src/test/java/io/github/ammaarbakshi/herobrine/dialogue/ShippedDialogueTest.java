package io.github.ammaarbakshi.herobrine.dialogue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.github.ammaarbakshi.herobrine.entity.HerobrineEntity;
import io.github.ammaarbakshi.herobrine.memory.BossKind;
import io.github.ammaarbakshi.herobrine.memory.Counters;
import io.github.ammaarbakshi.herobrine.memory.GearTier;
import io.github.ammaarbakshi.herobrine.memory.Milestone;

/**
 * Checks the dialogue the mod ships with, so a typo in a tag or a counter name fails the build
 * instead of silently making a line unreachable.
 */
class ShippedDialogueTest {
	/** Tags whose values come from game data (damage types, entity ids): only the prefix is checked. */
	private static final Set<String> OPEN_PREFIXES = Set.of("cause:", "killer:");
	/** Every other tag the code can produce (see ObserverContext and the controllers). */
	private static final Set<String> KNOWN_TAGS = Set.of(
			"outdoors", "underground", "indoors", "alone", "health:low", "creative", "visible", "ignored",
			"first_meeting", "long_absence", "in_boss_fight",
			"dimension:overworld", "dimension:the_nether", "dimension:the_end", "time:day", "time:night",
			"weather:clear", "weather:rain", "weather:thunder",
			"haunt:signs", "haunt:sightings", "haunt:stalking", "haunt:confrontation",
			"near:home", "near:village", "near:berserk",
			"place:tree", "place:river", "place:ridge", "place:home", "place:cave", "place:open", "place:dark",
			"place:distant", "place:behind", "place:ruin", "place:village",
			"outcome:approached", "outcome:stared", "outcome:looked_away", "outcome:ignored",
			"phase:arrive", "phase:fight", "phase:killed", "depth:shallow", "depth:deep");

	private static DialogueCatalog catalog;
	private static final List<String> WARNINGS = new ArrayList<>();

	@BeforeAll
	static void load() throws IOException {
		DialogueParser.Builder builder = new DialogueParser.Builder(WARNINGS::add);
		builder.addLines("dialogue.json", read("/data/herobrine/dialogue/dialogue.json"));
		builder.addTriggers("triggers.json", read("/data/herobrine/dialogue/triggers.json"));
		catalog = builder.build();
	}

	private static JsonElement read(String path) throws IOException {
		try (InputStream in = ShippedDialogueTest.class.getResourceAsStream(path)) {
			if (in == null) {
				throw new IOException("Missing resource " + path);
			}
			try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
				return JsonParser.parseReader(reader);
			}
		}
	}

	@Test
	void loadsWithoutWarnings() {
		assertTrue(WARNINGS.isEmpty(), String.join("\n", WARNINGS));
		assertTrue(catalog.size() >= 100, "expected the full script, got " + catalog.size());
	}

	@Test
	void everyCodeTriggerHasARule() {
		for (String trigger : List.of("encounter", "reaction", "idle", "progression", "boss", "weather", "cave", "building",
				"death", "milestone")) {
			assertTrue(catalog.hasTrigger(trigger), "triggers.json has no rule for " + trigger);
		}
	}

	@Test
	void everyTagIsOneTheCodeProduces() {
		Set<String> tierTags = new HashSet<>();
		for (GearTier tier : GearTier.values()) {
			tierTags.add("tier:" + tier.id());
		}
		Set<String> bossTags = new HashSet<>();
		for (BossKind kind : BossKind.values()) {
			bossTags.add("boss:" + kind.id());
		}
		Set<String> milestoneTags = new HashSet<>();
		for (Milestone milestone : Milestone.values()) {
			milestoneTags.add("milestone:" + milestone.id());
		}
		Set<String> modeTags = new HashSet<>();
		for (HerobrineEntity.Mode mode : HerobrineEntity.Mode.values()) {
			modeTags.add("mode:" + mode.name().toLowerCase(Locale.ROOT));
		}
		for (DialogueLine line : catalog.lines()) {
			Set<String> tags = new HashSet<>(line.tags());
			tags.addAll(line.notTags());
			for (String tag : tags) {
				boolean known = KNOWN_TAGS.contains(tag) || tierTags.contains(tag) || bossTags.contains(tag)
						|| milestoneTags.contains(tag) || modeTags.contains(tag) || OPEN_PREFIXES.stream().anyMatch(tag::startsWith);
				assertTrue(known, line.id() + " uses unknown tag " + tag);
			}
		}
	}

	@Test
	void everyMemoryConditionNamesARealCounter() throws IllegalAccessException {
		Set<String> counters = new HashSet<>();
		for (Field field : Counters.class.getFields()) {
			if (Modifier.isStatic(field.getModifiers()) && field.getType() == String.class) {
				counters.add((String) field.get(null));
			}
		}
		for (BossKind kind : BossKind.values()) {
			counters.add(Counters.boss(kind));
			counters.add(Counters.kill(kind));
		}
		for (DialogueLine line : catalog.lines()) {
			for (String counter : line.memory().keySet()) {
				assertTrue(counters.contains(counter), line.id() + " checks unknown counter " + counter);
			}
		}
	}

	@Test
	void audioFollowsTheFolderLayout() {
		for (DialogueLine line : catalog.lines()) {
			assertEquals("herobrine:voice/" + line.category() + "/" + line.id().substring(line.category().length() + 1),
					line.audio(), line.id());
			assertTrue(!line.note().isBlank(), line.id() + " has no delivery note for the recording");
		}
	}

	@Test
	void theSpecifiedEncounterGreetingsFollowTheCount() {
		assertEquals("Hey, what's up?", catalog.line("random.first").orElseThrow().text());
		assertTrue(catalog.line("random.back_so_soon").orElseThrow().memory().get("encounters").test(5));
		assertTrue(catalog.line("random.knew_youd_return").orElseThrow().memory().get("encounters").test(10));
		assertTrue(catalog.line("random.so_many_times").orElseThrow().memory().get("encounters").test(20));
	}

	@Test
	void theFirstMeetingAlwaysOpensWithTheGreeting() {
		TriggerRule encounter = catalog.trigger("encounter");
		java.util.Random random = new java.util.Random(11);
		for (int i = 0; i < 500; i++) {
			DialoguePlanner.Plan plan = DialoguePlanner.choose(catalog, encounter,
					Set.of("mode:observer", "place:tree", "first_meeting", "time:day", "outdoors", "alone"),
					name -> name.equals("encounters") ? 1 : 0, DialogueHistory.NONE, 100_000_000L, random, false);
			assertEquals("random.first", plan.line() == null ? null : plan.line().id(),
					"the first time he is seen, he must say hello, not " + plan.line());
		}
	}

	@Test
	void fourthWallLinesAreGuardedByARareCategory() {
		CategoryRule rule = catalog.categoryRule("fourth_wall");
		assertTrue(rule.chance() <= 0.05, "fourth-wall lines must stay extremely rare");
		assertTrue(rule.cooldownSeconds() >= 6 * 3600, "fourth-wall lines need a cooldown of hours");
		for (DialogueLine line : catalog.category("fourth_wall")) {
			assertTrue(line.once(), line.id() + " should only ever be said once");
		}
	}
}

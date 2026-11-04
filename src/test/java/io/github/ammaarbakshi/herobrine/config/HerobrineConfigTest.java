package io.github.ammaarbakshi.herobrine.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HerobrineConfigTest {
	@TempDir
	Path dir;

	@Test
	void missingFileWritesDefaults() throws IOException {
		Path file = dir.resolve("herobrine.json");
		HerobrineConfig config = HerobrineConfig.load(file);

		assertTrue(Files.exists(file));
		assertEquals(16, config.protectedRadius);
		assertEquals(HerobrineConfig.Griefing.NATURAL_ONLY, config.griefing);
		assertTrue(Files.readString(file).contains("\"hauntSpeedMultiplier\""));
	}

	@Test
	void partialFileKeepsDefaultsForMissingKeys() throws IOException {
		Path file = dir.resolve("herobrine.json");
		Files.writeString(file, "{\"bossHealth\": 80, \"griefing\": \"NONE\"}", StandardCharsets.UTF_8);

		HerobrineConfig config = HerobrineConfig.load(file);

		assertEquals(80.0, config.bossHealth);
		assertEquals(HerobrineConfig.Griefing.NONE, config.griefing);
		assertEquals(20, config.minTraitSamples);
	}

	@Test
	void outOfRangeValuesAreClamped() throws IOException {
		Path file = dir.resolve("herobrine.json");
		Files.writeString(file, """
				{"requiredConfidence": 4.0, "protectedRadius": -3,
				 "sightingsThreshold": 70, "stalkingThreshold": 10, "confrontationThreshold": 5}
				""", StandardCharsets.UTF_8);

		HerobrineConfig config = HerobrineConfig.load(file);

		assertEquals(1.0, config.requiredConfidence);
		assertEquals(0, config.protectedRadius);
		// Thresholds are forced to be non-decreasing so phases never overlap.
		assertEquals(70, config.sightingsThreshold);
		assertEquals(70, config.stalkingThreshold);
		assertEquals(70, config.confrontationThreshold);
	}

	@Test
	void malformedFileKeepsPreviousConfigAndFile() throws IOException {
		HerobrineConfig before = HerobrineConfig.load(dir.resolve("good.json"));
		Path bad = dir.resolve("bad.json");
		Files.writeString(bad, "{ not json", StandardCharsets.UTF_8);

		HerobrineConfig after = HerobrineConfig.load(bad);

		assertSame(before, after);
		assertEquals("{ not json", Files.readString(bad));
	}
}

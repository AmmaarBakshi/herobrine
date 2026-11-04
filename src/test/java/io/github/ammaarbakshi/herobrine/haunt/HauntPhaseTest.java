package io.github.ammaarbakshi.herobrine.haunt;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class HauntPhaseTest {
	@ParameterizedTest
	@CsvSource({
			"0, SIGNS",
			"24, SIGNS",
			"25, SIGHTINGS",
			"59, SIGHTINGS",
			"60, STALKING",
			"89, STALKING",
			"90, CONFRONTATION",
			"100, CONFRONTATION"
	})
	void defaultThresholdsMatchSpec(int level, HauntPhase expected) {
		assertEquals(expected, HauntPhase.of(level, 25, 60, 90));
	}

	@ParameterizedTest
	@CsvSource({"49, SIGNS", "50, CONFRONTATION"})
	void equalThresholdsSkipIntermediatePhases(int level, HauntPhase expected) {
		assertEquals(expected, HauntPhase.of(level, 50, 50, 50));
	}
}

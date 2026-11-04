package io.github.ammaarbakshi.herobrine.encounter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.ammaarbakshi.herobrine.encounter.ShrinePattern.Part;

class ShrinePatternTest {
	private static Map<String, Part> shrine() {
		Map<String, Part> blocks = new HashMap<>();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				blocks.put(key(dx, -1, dz), Part.GOLD);
			}
		}
		blocks.put(key(0, 0, 0), Part.NETHERRACK);
		blocks.put(key(1, 0, 0), Part.REDSTONE_TORCH);
		blocks.put(key(-1, 0, 0), Part.REDSTONE_TORCH);
		blocks.put(key(0, 0, 1), Part.REDSTONE_TORCH);
		blocks.put(key(0, 0, -1), Part.REDSTONE_TORCH);
		blocks.put(key(0, 1, 0), Part.FIRE);
		return blocks;
	}

	private static String key(int dx, int dy, int dz) {
		return dx + "," + dy + "," + dz;
	}

	private static boolean matches(Map<String, Part> blocks) {
		return ShrinePattern.matches((dx, dy, dz) -> blocks.getOrDefault(key(dx, dy, dz), Part.OTHER));
	}

	@Test
	void completeLitShrineMatches() {
		assertTrue(matches(shrine()));
	}

	@Test
	void unlitShrineDoesNotMatch() {
		Map<String, Part> blocks = shrine();
		blocks.remove(key(0, 1, 0));
		assertFalse(matches(blocks));
	}

	@Test
	void missingTorchDoesNotMatch() {
		Map<String, Part> blocks = shrine();
		blocks.remove(key(0, 0, -1));
		assertFalse(matches(blocks));
	}

	@Test
	void floorCornersMustBeGold() {
		Map<String, Part> blocks = shrine();
		blocks.put(key(1, -1, 1), Part.OTHER);
		assertFalse(matches(blocks));
	}

	@Test
	void centreMustBeNetherrack() {
		Map<String, Part> blocks = shrine();
		blocks.put(key(0, 0, 0), Part.GOLD);
		assertFalse(matches(blocks));
	}
}

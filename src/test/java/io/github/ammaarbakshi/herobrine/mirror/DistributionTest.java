package io.github.ammaarbakshi.herobrine.mirror;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class DistributionTest {
	@Test
	void emptyReturnsFallback() {
		assertEquals(2, Distribution.empty(5).top(2));
	}

	@Test
	void mostObservedCategoryWins() {
		Distribution d = Distribution.empty(5);
		for (int i = 0; i < 30; i++) {
			d = d.observe(WeaponType.AXE.ordinal(), 0.08);
		}
		for (int i = 0; i < 5; i++) {
			d = d.observe(WeaponType.SWORD.ordinal(), 0.08);
		}
		assertEquals(WeaponType.AXE.ordinal(), d.top(0));
		assertEquals(35, d.samples());
	}

	@Test
	void weightsStayNormalised() {
		Distribution d = Distribution.empty(3);
		int[] seq = {0, 1, 1, 2, 0, 0, 1, 2, 2, 2};
		for (int c : seq) {
			d = d.observe(c, 0.1);
		}
		assertEquals(1.0, Arrays.stream(d.weights()).sum(), 1e-9);
	}

	@Test
	void outOfRangeCategoryIsIgnored() {
		Distribution d = Distribution.empty(3);
		assertEquals(d, d.observe(7, 0.1));
	}

	@Test
	void resizeKeepsKnownWeights() {
		Distribution d = Distribution.of(new double[] {0.2, 0.8}, 10).resized(4);
		assertEquals(4, d.size());
		assertEquals(0.8, d.weight(1), 1e-9);
		assertEquals(0.0, d.weight(3), 1e-9);
	}
}

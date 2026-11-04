package io.github.ammaarbakshi.herobrine.mirror;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TraitTest {
	private static final double EPS = 1e-9;

	@Test
	void firstSampleSetsValueExactly() {
		Trait t = Trait.EMPTY.update(0.8, 0.1);
		assertEquals(0.8, t.value(), EPS);
		assertEquals(1, t.samples());
	}

	@Test
	void warmUpIsRunningMean() {
		// While 1/(n+1) > alpha the trait is a plain average of what it has seen.
		Trait t = Trait.EMPTY.update(1.0, 0.1).update(0.0, 0.1).update(0.5, 0.1);
		assertEquals(0.5, t.value(), EPS);
		assertEquals(3, t.samples());
	}

	@Test
	void afterWarmUpUsesAlpha() {
		Trait t = new Trait(0.0, 100);
		Trait next = t.update(1.0, 0.1);
		assertEquals(0.1, next.value(), EPS);
	}

	@Test
	void convergesTowardRepeatedValue() {
		Trait t = Trait.EMPTY;
		for (int i = 0; i < 200; i++) {
			t = t.update(i % 2 == 0 ? 0.6 : 0.6, 0.08);
		}
		assertEquals(0.6, t.value(), 1e-6);
	}

	@Test
	void emaFollowsAChangeOfHabit() {
		Trait t = Trait.EMPTY;
		for (int i = 0; i < 100; i++) {
			t = t.update(-1.0, 0.08);
		}
		for (int i = 0; i < 60; i++) {
			t = t.update(1.0, 0.08);
		}
		assertTrue(t.value() > 0.9, "should have mostly adapted to the new habit: " + t.value());
	}

	@Test
	void nonFiniteSamplesAreIgnored() {
		Trait t = new Trait(0.4, 5);
		assertSame(t, t.update(Double.NaN, 0.1));
		assertSame(t, t.update(Double.POSITIVE_INFINITY, 0.1));
	}

	@Test
	void knownAndFallback() {
		Trait t = new Trait(0.7, 19);
		assertFalse(t.known(20));
		assertEquals(0.3, t.valueOr(20, 0.3), EPS);
		Trait more = t.update(0.7, 0.1);
		assertTrue(more.known(20));
		assertEquals(0.7, more.valueOr(20, 0.3), EPS);
	}
}

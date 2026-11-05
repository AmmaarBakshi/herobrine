package io.github.ammaarbakshi.herobrine.dialogue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IntRangeTest {
	@Test
	void parsesVanillaStyleRanges() {
		assertEquals(IntRange.exactly(5), IntRange.parse("5"));
		assertEquals(new IntRange(5, 9), IntRange.parse("5..9"));
		assertEquals(IntRange.atLeast(5), IntRange.parse("5.."));
		assertEquals(IntRange.atMost(9), IntRange.parse("..9"));
		assertEquals(IntRange.atLeast(-3), IntRange.parse(" -3 .. "));
	}

	@Test
	void testsInclusively() {
		IntRange range = IntRange.parse("5..9");
		assertFalse(range.test(4));
		assertTrue(range.test(5));
		assertTrue(range.test(9));
		assertFalse(range.test(10));
	}

	@Test
	void rejectsNonsense() {
		assertThrows(IllegalArgumentException.class, () -> IntRange.parse(".."));
		assertThrows(IllegalArgumentException.class, () -> IntRange.parse("five"));
		assertThrows(IllegalArgumentException.class, () -> IntRange.parse("9..5"));
	}

	@Test
	void printsBackTheSameSyntax() {
		for (String text : new String[] {"5", "5..9", "5..", "..9"}) {
			assertEquals(text, IntRange.parse(text).toString());
		}
	}
}

package io.github.ammaarbakshi.herobrine.mirror;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CombatMotionTest {
	private static final double EPS = 1.0E-9;

	@Test
	void facingSouthRightHandIsWest() {
		// Target to the south (+Z); stepping west (-X) is a step to the right.
		CombatMotion.Motion m = CombatMotion.of(0, 0, -1, 0, -1, 5);
		assertEquals(1.0, m.lateral(), EPS);
		assertEquals(1.0, m.strafeDirection(), EPS);
		assertEquals(5.0, m.distance(), EPS);
	}

	@Test
	void strafingLeftIsNegative() {
		CombatMotion.Motion m = CombatMotion.of(0, 0, 1, 0, 1, 5);
		assertEquals(-1.0, m.strafeDirection(), EPS);
	}

	@Test
	void directionIsRelativeToTheTargetNotTheWorld() {
		// Target to the east (+X): the right hand points south (+Z).
		CombatMotion.Motion m = CombatMotion.of(0, 0, 0, 1, 4, 1);
		assertTrue(m.lateral() > 0.99);
		assertEquals(0.0, m.radial(), EPS);
	}

	@Test
	void walkingStraightAtTheTargetIsNotStrafing() {
		CombatMotion.Motion m = CombatMotion.of(0, 0, 0, 1, 0, 4);
		assertFalse(m.strafing());
		assertEquals(0.0, m.strafeIntensity(), EPS);
		assertTrue(m.radialSpeed() > 0);
		assertFalse(m.backingOff());
	}

	@Test
	void backingOffIsDetected() {
		assertTrue(CombatMotion.of(0, 0, 0, -1, 0, 4).backingOff());
	}

	@Test
	void intensityScalesWithSidewaysSpeedAndCaps() {
		double perSample = CombatMotion.STRAFE_FULL_SPEED * CombatMotion.SAMPLE_TICKS;
		// Direction is measured from where they ended up, so keep the target straight ahead of that.
		assertEquals(0.5, CombatMotion.of(0, 0, -perSample / 2, 0, -perSample / 2, 5).strafeIntensity(), 1.0E-9);
		assertEquals(1.0, CombatMotion.of(0, 0, -perSample * 3, 0, 0, 5).strafeIntensity(), EPS);
	}

	@Test
	void jitterIsNotStrafing() {
		double tiny = CombatMotion.STRAFE_MIN_SPEED * CombatMotion.SAMPLE_TICKS / 2;
		assertEquals(0.0, CombatMotion.of(0, 0, -tiny, 0, 0, 5).strafeDirection(), EPS);
	}

	@Test
	void standingOnTheTargetIsHarmless() {
		CombatMotion.Motion m = CombatMotion.of(0, 0, 1, 1, 1, 1);
		assertEquals(0.0, m.distance(), EPS);
		assertFalse(m.strafing());
	}
}

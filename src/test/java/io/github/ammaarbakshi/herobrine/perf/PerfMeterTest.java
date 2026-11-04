package io.github.ammaarbakshi.herobrine.perf;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PerfMeterTest {
	@Test
	void emptyMeterReportsZero() {
		PerfMeter.Snapshot snapshot = new PerfMeter().snapshot();
		assertEquals(0.0, snapshot.averageMicros());
		assertEquals(0.0, snapshot.worstMicros());
		assertEquals(0, snapshot.units());
	}

	@Test
	void averagesAndWorstArePerUnit() {
		PerfMeter meter = new PerfMeter();
		meter.sample(10_000);
		meter.sample(30_000);
		meter.sample(20_000);
		PerfMeter.Snapshot snapshot = meter.snapshot();
		assertEquals(20.0, snapshot.averageMicros(), 1e-9);
		assertEquals(30.0, snapshot.worstMicros(), 1e-9);
		assertEquals(3, snapshot.units());
	}

	@Test
	void chargesBeforeTheUnitEndsCountTowardsIt() {
		PerfMeter meter = new PerfMeter();
		// Two event handlers, then the tick itself: one unit of 6 µs.
		meter.charge(1_000);
		meter.charge(2_000);
		meter.sample(3_000);
		assertEquals(6.0, meter.snapshot().worstMicros(), 1e-9);
		assertEquals(1, meter.snapshot().units());
	}

	@Test
	void idleUnitsLowerTheAverage() {
		PerfMeter meter = new PerfMeter();
		meter.charge(8_000);
		meter.endUnit();
		meter.endUnit();
		meter.endUnit();
		meter.endUnit();
		assertEquals(2.0, meter.snapshot().averageMicros(), 1e-9);
		assertEquals(8.0, meter.snapshot().worstMicros(), 1e-9);
	}

	@Test
	void resetForgetsEverythingIncludingTheUnitInProgress() {
		PerfMeter meter = new PerfMeter();
		meter.sample(5_000);
		meter.charge(7_000);
		meter.reset();
		meter.endUnit();
		assertEquals(0.0, meter.snapshot().worstMicros());
		assertEquals(1, meter.snapshot().units());
	}
}

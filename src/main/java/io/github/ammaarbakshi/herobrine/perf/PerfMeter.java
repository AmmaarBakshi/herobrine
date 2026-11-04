package io.github.ammaarbakshi.herobrine.perf;

/**
 * How long one part of the mod takes, per unit of work (a tick, a player-tick), so
 * {@code /herobrine perf} can show an average and a worst case. Time can be charged to the
 * unit in progress from several places (event handlers, tick handlers) before the unit ends.
 *
 * <p>Not thread-safe: every meter belongs to the server thread.
 */
public final class PerfMeter {
	private long totalNanos;
	private long units;
	private long worstNanos;
	private long currentNanos;

	/** Adds time to the unit in progress. */
	public void charge(long nanos) {
		currentNanos += nanos;
	}

	/** Ends the unit in progress (which may have cost nothing). */
	public void endUnit() {
		totalNanos += currentNanos;
		worstNanos = Math.max(worstNanos, currentNanos);
		currentNanos = 0;
		units++;
	}

	/** A whole unit at once. */
	public void sample(long nanos) {
		charge(nanos);
		endUnit();
	}

	public Snapshot snapshot() {
		return new Snapshot(units == 0 ? 0.0 : totalNanos / 1000.0 / units, worstNanos / 1000.0, units);
	}

	public void reset() {
		totalNanos = 0;
		units = 0;
		worstNanos = 0;
		currentNanos = 0;
	}

	/** Averages and worst case so far, in microseconds per unit. */
	public record Snapshot(double averageMicros, double worstMicros, long units) {
	}
}

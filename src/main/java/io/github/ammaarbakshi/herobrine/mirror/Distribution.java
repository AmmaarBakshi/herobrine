package io.github.ammaarbakshi.herobrine.mirror;

import java.util.Arrays;

/**
 * Exponentially weighted preference over a fixed set of categories (enum ordinals), e.g. which
 * weapon a player reaches for. Immutable; every update returns a new instance.
 */
public final class Distribution {
	private final double[] weights;
	private final int samples;

	private Distribution(double[] weights, int samples) {
		this.weights = weights;
		this.samples = samples;
	}

	public static Distribution empty(int categories) {
		return new Distribution(new double[categories], 0);
	}

	public static Distribution of(double[] weights, int samples) {
		return new Distribution(weights.clone(), Math.max(0, samples));
	}

	public Distribution observe(int category, double alpha) {
		if (category < 0 || category >= weights.length) {
			return this;
		}
		double weight = Math.max(alpha, 1.0 / (samples + 1.0));
		double[] next = new double[weights.length];
		for (int i = 0; i < next.length; i++) {
			double target = i == category ? 1.0 : 0.0;
			next[i] = weights[i] + (target - weights[i]) * weight;
		}
		return new Distribution(next, samples == Integer.MAX_VALUE ? samples : samples + 1);
	}

	/** Most likely category, or {@code fallback} when nothing has been observed. */
	public int top(int fallback) {
		if (samples == 0) {
			return fallback;
		}
		int best = fallback;
		double bestWeight = -1;
		for (int i = 0; i < weights.length; i++) {
			if (weights[i] > bestWeight) {
				best = i;
				bestWeight = weights[i];
			}
		}
		return best;
	}

	public double weight(int category) {
		return category >= 0 && category < weights.length ? weights[category] : 0.0;
	}

	public int samples() {
		return samples;
	}

	public int size() {
		return weights.length;
	}

	public double[] weights() {
		return weights.clone();
	}

	public boolean known(int minSamples) {
		return samples >= minSamples;
	}

	/** Pads or truncates to {@code categories}, so saved data survives enum changes. */
	public Distribution resized(int categories) {
		return weights.length == categories ? this : new Distribution(Arrays.copyOf(weights, categories), samples);
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof Distribution other && samples == other.samples && Arrays.equals(weights, other.weights);
	}

	@Override
	public int hashCode() {
		return 31 * Arrays.hashCode(weights) + samples;
	}

	@Override
	public String toString() {
		return "Distribution" + Arrays.toString(weights) + "x" + samples;
	}
}

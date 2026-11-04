package io.github.ammaarbakshi.herobrine.mirror;

/**
 * One learned habit: an exponential moving average plus how many samples fed it.
 *
 * <p>Early samples are weighted as a plain running mean ({@code 1/(n+1)}) until that drops
 * below {@code alpha}. Without this warm-up the first few samples would be dragged toward the
 * arbitrary starting value instead of reflecting what the player actually did.
 */
public record Trait(double value, int samples) {
	public static final Trait EMPTY = new Trait(0.0, 0);

	public Trait {
		if (Double.isNaN(value) || Double.isInfinite(value)) {
			value = 0.0;
		}
		samples = Math.max(0, samples);
	}

	public Trait update(double x, double alpha) {
		if (Double.isNaN(x) || Double.isInfinite(x)) {
			return this;
		}
		double weight = Math.max(alpha, 1.0 / (samples + 1.0));
		int next = samples == Integer.MAX_VALUE ? samples : samples + 1;
		return new Trait(value + (x - value) * weight, next);
	}

	public boolean known(int minSamples) {
		return samples >= minSamples;
	}

	/** The learned value if known, otherwise {@code fallback}. */
	public double valueOr(int minSamples, double fallback) {
		return known(minSamples) ? value : fallback;
	}
}

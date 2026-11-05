package io.github.ammaarbakshi.herobrine.dialogue;

/**
 * An inclusive whole-number range in the syntax datapack authors already know from vanilla:
 * {@code "5"} (exactly 5), {@code "5..9"}, {@code "5.."} (5 or more), {@code "..9"} (9 or less).
 */
public record IntRange(int min, int max) {
	public static final IntRange ANY = new IntRange(Integer.MIN_VALUE, Integer.MAX_VALUE);

	public IntRange {
		if (min > max) {
			throw new IllegalArgumentException("min " + min + " is above max " + max);
		}
	}

	public static IntRange exactly(int value) {
		return new IntRange(value, value);
	}

	public static IntRange atLeast(int value) {
		return new IntRange(value, Integer.MAX_VALUE);
	}

	public static IntRange atMost(int value) {
		return new IntRange(Integer.MIN_VALUE, value);
	}

	public boolean test(int value) {
		return value >= min && value <= max;
	}

	/** @throws IllegalArgumentException if {@code text} isn't a range */
	public static IntRange parse(String text) {
		String s = text.strip();
		int dots = s.indexOf("..");
		try {
			if (dots < 0) {
				return exactly(Integer.parseInt(s));
			}
			String low = s.substring(0, dots).strip();
			String high = s.substring(dots + 2).strip();
			if (low.isEmpty() && high.isEmpty()) {
				throw new IllegalArgumentException("Empty range: " + text);
			}
			int min = low.isEmpty() ? Integer.MIN_VALUE : Integer.parseInt(low);
			int max = high.isEmpty() ? Integer.MAX_VALUE : Integer.parseInt(high);
			return new IntRange(min, max);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Not a range: " + text, e);
		}
	}

	@Override
	public String toString() {
		if (min == max) {
			return Integer.toString(min);
		}
		return (min == Integer.MIN_VALUE ? "" : Integer.toString(min)) + ".." + (max == Integer.MAX_VALUE ? "" : Integer.toString(max));
	}
}

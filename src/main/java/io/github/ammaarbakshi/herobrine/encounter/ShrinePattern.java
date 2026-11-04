package io.github.ammaarbakshi.herobrine.encounter;

/**
 * The shrine, as a pure pattern: a 3x3 floor of gold blocks, netherrack on its centre, a
 * redstone torch on each of the netherrack's four sides (standing on the gold, or on the
 * netherrack's side), and fire on the netherrack. Coordinates are relative to the netherrack.
 */
public final class ShrinePattern {
	/** What a block is, as far as the shrine is concerned. */
	public enum Part {
		GOLD,
		NETHERRACK,
		REDSTONE_TORCH,
		FIRE,
		OTHER
	}

	@FunctionalInterface
	public interface Lookup {
		Part at(int dx, int dy, int dz);
	}

	private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

	private ShrinePattern() {
	}

	/** @param lookup block parts around the netherrack, which is at (0, 0, 0) */
	public static boolean matches(Lookup lookup) {
		if (lookup.at(0, 0, 0) != Part.NETHERRACK || lookup.at(0, 1, 0) != Part.FIRE) {
			return false;
		}
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (lookup.at(dx, -1, dz) != Part.GOLD) {
					return false;
				}
			}
		}
		for (int[] side : SIDES) {
			if (lookup.at(side[0], 0, side[1]) != Part.REDSTONE_TORCH) {
				return false;
			}
		}
		return true;
	}
}

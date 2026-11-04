package io.github.ammaarbakshi.herobrine.mirror;

import java.util.random.RandomGenerator;

/**
 * The utility half of the Mirror AI: given the player's habits and the state of the fight,
 * what should Herobrine be doing? Pure, so the decisions can be tested without a world.
 *
 * <p>Priority: heal when hurt to the player's own heal point (if he has heals left), back off
 * at the player's retreat point, otherwise fight the way they do, ranged as often as they
 * shoot. A choice is kept for a while so he doesn't flicker between bow and sword.
 */
public final class TacticPlanner {
	public enum Tactic {
		MELEE,
		RANGED,
		RETREAT,
		HEAL
	}

	/** How the player fights, already resolved to values (known trait or fallback). */
	public record Habits(
			double rangedRatio,
			double preferredRange,
			double healThreshold,
			double retreatThreshold,
			double pillarRate,
			double pearlEscapeRate) {
	}

	/** The fight right now. */
	public record Situation(
			double healthFraction,
			double distance,
			int healsLeft,
			boolean healOnCooldown,
			boolean retreatOnCooldown,
			boolean hasBow,
			Tactic current,
			int ticksInCurrent) {
	}

	/** Melee/ranged choices last at least this long. */
	public static final int MIN_COMMIT_TICKS = 60;
	/** Past this distance a melee fighter still walks in; a ranged one starts shooting. */
	public static final double BOW_MIN_DISTANCE = 5.0;

	private TacticPlanner() {
	}

	public static Tactic decide(Habits habits, Situation now, RandomGenerator random) {
		if (now.current() == Tactic.HEAL || now.current() == Tactic.RETREAT) {
			// Finishing an action is the action goal's job; it hands back by switching to MELEE.
			return now.current();
		}
		if (now.healsLeft() > 0 && !now.healOnCooldown() && now.healthFraction() <= habits.healThreshold()) {
			return Tactic.HEAL;
		}
		if (!now.retreatOnCooldown() && now.healthFraction() <= habits.retreatThreshold() && now.healthFraction() < 1.0) {
			return Tactic.RETREAT;
		}
		if (now.ticksInCurrent() < MIN_COMMIT_TICKS) {
			return now.current();
		}
		boolean ranged = now.hasBow() && random.nextDouble() < habits.rangedRatio();
		// Up close a bow is useless unless that's genuinely how they play (kiters back off and shoot).
		if (ranged && now.distance() < BOW_MIN_DISTANCE && habits.rangedRatio() < 0.5) {
			ranged = false;
		}
		return ranged ? Tactic.RANGED : Tactic.MELEE;
	}

	/** Escapes are rolled once, when a retreat starts. */
	public enum Escape {
		RUN,
		PILLAR,
		PEARL
	}

	public static Escape escape(Habits habits, boolean hasBlocks, RandomGenerator random) {
		if (hasBlocks && random.nextDouble() < habits.pillarRate()) {
			return Escape.PILLAR;
		}
		if (random.nextDouble() < habits.pearlEscapeRate()) {
			return Escape.PEARL;
		}
		return Escape.RUN;
	}

	// --- Melee timing -------------------------------------------------------------------------

	/**
	 * Ticks to wait between swings for an average charge of {@code charge}: a spam-clicker
	 * (0.3) swings about three times as often as someone who waits for the full cooldown.
	 */
	public static int swingInterval(double charge, double fullCooldownTicks) {
		return (int) Math.max(4, Math.round(Math.max(0.0, Math.min(1.0, charge)) * fullCooldownTicks));
	}

	/** Vanilla's damage scale for an attack at {@code charge} (0.2 + 0.8 c^2). */
	public static double chargeDamageScale(double charge) {
		double c = Math.max(0.0, Math.min(1.0, charge));
		return 0.2 + c * c * 0.8;
	}

	/** Picks the side to strafe to next: right with probability (1 + bias) / 2. */
	public static int strafeSide(double bias, RandomGenerator random) {
		double right = (1.0 + Math.max(-1.0, Math.min(1.0, bias))) / 2.0;
		return random.nextDouble() < right ? 1 : -1;
	}
}

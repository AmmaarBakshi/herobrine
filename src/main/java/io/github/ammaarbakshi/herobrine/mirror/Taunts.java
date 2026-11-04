package io.github.ammaarbakshi.herobrine.mirror;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.random.RandomGenerator;

/**
 * What he says during a fight: short lines built from the player's real habits ("Left
 * again.", "You always heal at 3.5 hearts."). Each rule only applies once its trait is known,
 * so he never claims something he hasn't seen. The words live in the lang file under
 * {@code herobrine.taunt.<id>}; this class only decides which line fits and its argument.
 */
public final class Taunts {
	/** A line to say: lang key suffix plus its single formatted argument ("" if none). */
	public record Taunt(String id, String argument) {
		public String translationKey() {
			return "herobrine.taunt." + id;
		}
	}

	/** First line a few seconds in, then one every 15-30 s. */
	public static final int FIRST_DELAY = 5 * 20;
	public static final int MIN_INTERVAL = 15 * 20;
	public static final int MAX_INTERVAL = 30 * 20;

	/** Said when nothing more specific is known yet. */
	static final List<String> GENERIC = List.of("generic_1", "generic_2", "generic_3");

	private Taunts() {
	}

	/** Every line that is true of this player, most specific first. */
	public static List<Taunt> candidates(PlayerProfile profile, int minSamples) {
		int rare = ProfileAnalyzer.rareMinSamples(minSamples);
		List<Taunt> out = new ArrayList<>();
		if (known(profile, TraitId.STRAFE_BIAS, minSamples)) {
			double bias = value(profile, TraitId.STRAFE_BIAS);
			if (bias >= 0.3) {
				out.add(new Taunt("strafe_right", ""));
			} else if (bias <= -0.3) {
				out.add(new Taunt("strafe_left", ""));
			}
		}
		if (known(profile, TraitId.HEAL_THRESHOLD, rare) && known(profile, TraitId.HEAL_THRESHOLD_SPREAD, rare)
				&& value(profile, TraitId.HEAL_THRESHOLD_SPREAD) <= 0.15) {
			out.add(new Taunt("heal_at", hearts(value(profile, TraitId.HEAL_THRESHOLD))));
		}
		if (known(profile, TraitId.SHIELD_USE_RATE, minSamples) && value(profile, TraitId.SHIELD_USE_RATE) >= 0.5) {
			out.add(new Taunt("shield", ""));
		}
		if (known(profile, TraitId.RANGED_RATIO, minSamples) && value(profile, TraitId.RANGED_RATIO) >= 0.5) {
			out.add(new Taunt("kiting", ""));
		}
		if (known(profile, TraitId.PILLAR_RATE, rare) && value(profile, TraitId.PILLAR_RATE) >= 0.3) {
			out.add(new Taunt("pillar", ""));
		}
		if (known(profile, TraitId.PEARL_ESCAPE_RATE, rare) && value(profile, TraitId.PEARL_ESCAPE_RATE) >= 0.3) {
			out.add(new Taunt("pearl", ""));
		}
		if (known(profile, TraitId.AVG_ATTACK_CHARGE, minSamples) && value(profile, TraitId.AVG_ATTACK_CHARGE) <= 0.5) {
			out.add(new Taunt("spam", ""));
		}
		if (known(profile, TraitId.CRIT_RATE, minSamples) && value(profile, TraitId.CRIT_RATE) >= 0.4) {
			out.add(new Taunt("crits", ""));
		}
		if (known(profile, TraitId.JUMP_FREQUENCY, minSamples) && value(profile, TraitId.JUMP_FREQUENCY) >= 0.8) {
			out.add(new Taunt("jumping", ""));
		}
		if (known(profile, TraitId.PREFERRED_RANGE, minSamples) && value(profile, TraitId.PREFERRED_RANGE) <= 2.2) {
			out.add(new Taunt("close", ""));
		}
		if (known(profile, TraitId.RETREAT_THRESHOLD, rare) && value(profile, TraitId.RETREAT_THRESHOLD) >= 0.5) {
			out.add(new Taunt("retreat", hearts(value(profile, TraitId.RETREAT_THRESHOLD))));
		}
		if (profile.weaponPref().known(minSamples)) {
			out.add(new Taunt("weapon_" + profile.topWeapon().name().toLowerCase(Locale.ROOT), ""));
		}
		return out;
	}

	/**
	 * Picks the next line: a true one if there is any (never the same as {@code previous}),
	 * otherwise a generic one.
	 */
	public static Taunt pick(PlayerProfile profile, int minSamples, String previous, RandomGenerator random) {
		List<Taunt> candidates = candidates(profile, minSamples);
		candidates.removeIf(t -> t.id().equals(previous));
		if (candidates.isEmpty()) {
			List<String> generic = new ArrayList<>(GENERIC);
			generic.remove(previous);
			return new Taunt(generic.get(random.nextInt(generic.size())), "");
		}
		return candidates.get(random.nextInt(candidates.size()));
	}

	public static int nextInterval(RandomGenerator random) {
		return MIN_INTERVAL + random.nextInt(MAX_INTERVAL - MIN_INTERVAL + 1);
	}

	/** Health fraction to hearts, as a player would say it: 0.35 -> "3.5". */
	static String hearts(double fraction) {
		double hearts = Math.round(Math.max(0.0, Math.min(1.0, fraction)) * 20.0) / 2.0;
		return hearts == Math.floor(hearts) ? String.valueOf((int) hearts) : String.format(Locale.ROOT, "%.1f", hearts);
	}

	private static boolean known(PlayerProfile profile, TraitId id, int minSamples) {
		return profile.trait(id).known(minSamples);
	}

	private static double value(PlayerProfile profile, TraitId id) {
		return profile.trait(id).value();
	}
}

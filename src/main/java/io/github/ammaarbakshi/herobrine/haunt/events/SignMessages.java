package io.github.ammaarbakshi.herobrine.haunt.events;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.random.RandomGenerator;

import io.github.ammaarbakshi.herobrine.mirror.PlayerProfile;
import io.github.ammaarbakshi.herobrine.mirror.ProfileAnalyzer;
import io.github.ammaarbakshi.herobrine.mirror.TraitId;
import io.github.ammaarbakshi.herobrine.mirror.WeaponType;

/**
 * Chooses what a sign says. Some messages are generic; others are built from what Herobrine
 * has learned about the player, foreshadowing the Mirror ("LEFT. ALWAYS LEFT.").
 *
 * <p>Pure: returns lang keys and arguments. Each message has up to four lines at
 * {@code herobrine.sign.<id>.1} to {@code .4}; every line gets the same arguments.
 */
public final class SignMessages {
	public record Message(String id, List<Object> args) {
		public String lineKey(int line) {
			return "herobrine.sign." + id + "." + line;
		}
	}

	static final List<String> GENERIC = List.of("i_see_you", "why_run", "turn_around", "not_alone", "remember", "stop_digging", "behind_you");
	public static final int LINES = 4;

	private SignMessages() {
	}

	/** Profile-derived messages the player currently qualifies for (may be empty). */
	public static List<Message> personal(PlayerProfile profile, int minSamples, float maxHealth) {
		List<Message> out = new ArrayList<>();
		int rare = ProfileAnalyzer.rareMinSamples(minSamples);
		if (profile.trait(TraitId.STRAFE_BIAS).known(minSamples)) {
			double bias = profile.trait(TraitId.STRAFE_BIAS).value();
			if (bias < -0.3) {
				out.add(new Message("always_left", List.of()));
			} else if (bias > 0.3) {
				out.add(new Message("always_right", List.of()));
			}
		}
		if (profile.trait(TraitId.HEAL_THRESHOLD).known(rare)) {
			long hearts = Math.max(1, Math.round(profile.trait(TraitId.HEAL_THRESHOLD).value() * maxHealth / 2.0));
			out.add(new Message("heal_at", List.of(hearts)));
		}
		if (profile.trait(TraitId.SHIELD_USE_RATE).known(minSamples) && profile.trait(TraitId.SHIELD_USE_RATE).value() > 0.5) {
			out.add(new Message("shield", List.of()));
		}
		if (profile.trait(TraitId.PILLAR_RATE).known(rare) && profile.trait(TraitId.PILLAR_RATE).value() > 0.5) {
			out.add(new Message("pillar", List.of()));
		}
		if (profile.trait(TraitId.RANGED_RATIO).known(minSamples) && profile.trait(TraitId.RANGED_RATIO).value() > 0.5) {
			out.add(new Message("coward", List.of()));
		}
		if (profile.trait(TraitId.CRIT_RATE).known(minSamples) && profile.trait(TraitId.CRIT_RATE).value() > 0.4) {
			out.add(new Message("jumping", List.of()));
		}
		if (profile.weaponPref().known(minSamples)) {
			WeaponType weapon = profile.topWeapon();
			out.add(new Message("weapon_" + weapon.name().toLowerCase(Locale.ROOT), List.of()));
		}
		return out;
	}

	/** Every message id this class can produce; each needs {@link #LINES} lang lines. */
	public static List<String> allIds() {
		List<String> ids = new ArrayList<>(GENERIC);
		ids.addAll(List.of("always_left", "always_right", "heal_at", "shield", "pillar", "coward", "jumping"));
		for (WeaponType weapon : WeaponType.values()) {
			ids.add("weapon_" + weapon.name().toLowerCase(Locale.ROOT));
		}
		return ids;
	}

	/** Half the time a personal message if any qualifies, otherwise a generic one. */
	public static Message pick(PlayerProfile profile, int minSamples, float maxHealth, RandomGenerator random) {
		List<Message> personal = personal(profile, minSamples, maxHealth);
		if (!personal.isEmpty() && random.nextBoolean()) {
			return personal.get(random.nextInt(personal.size()));
		}
		return new Message(GENERIC.get(random.nextInt(GENERIC.size())), List.of());
	}
}

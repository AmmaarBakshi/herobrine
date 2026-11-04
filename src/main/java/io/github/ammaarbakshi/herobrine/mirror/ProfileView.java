package io.github.ammaarbakshi.herobrine.mirror;

import java.util.ArrayList;
import java.util.List;

/**
 * A read-only, display-ready copy of a profile: what the debug overlay and the Mirror Shard
 * screen show. Plain values only, so it can be sent to the client and rendered without the
 * client ever needing the recorder's logic.
 */
public record ProfileView(
		List<Entry> traits,
		WeaponType topWeapon,
		boolean weaponKnown,
		HealItem topHealItem,
		boolean healKnown,
		List<String> favoriteBlocks,
		double confidence,
		int encounters,
		boolean inCombat) {

	/** One trait; {@code key} is the {@link TraitId#key()}. */
	public record Entry(String key, double value, int samples, boolean known) {
		public TraitId id() {
			return TraitId.byKey(key);
		}
	}

	public ProfileView {
		traits = List.copyOf(traits);
		favoriteBlocks = List.copyOf(favoriteBlocks);
	}

	public static ProfileView of(PlayerProfile profile, int minSamples, int encounters, boolean inCombat) {
		int rare = ProfileAnalyzer.rareMinSamples(minSamples);
		List<Entry> traits = new ArrayList<>(TraitId.values().length);
		for (TraitId id : TraitId.values()) {
			Trait trait = profile.trait(id);
			traits.add(new Entry(id.key(), trait.value(), trait.samples(), trait.known(id.core() ? minSamples : rare)));
		}
		List<String> blocks = profile.favoriteBlocksRanked();
		return new ProfileView(traits, profile.topWeapon(), profile.weaponPref().known(minSamples),
				profile.topHealItem(), profile.healItemPref().known(rare), blocks.subList(0, Math.min(3, blocks.size())),
				ProfileAnalyzer.confidence(profile, minSamples), encounters, inCombat);
	}

	public Entry trait(TraitId id) {
		return traits.get(id.ordinal());
	}
}

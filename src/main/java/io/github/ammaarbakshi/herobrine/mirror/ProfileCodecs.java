package io.github.ammaarbakshi.herobrine.mirror;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Serialization for the mirror data model. Kept apart from the model classes so they stay plain
 * Java; this only depends on DataFixerUpper's {@link Codec}.
 *
 * <p>Traits are saved by their stable {@link TraitId#key()} so renaming enum constants or adding
 * traits never corrupts old saves; unknown keys are dropped on load.
 */
public final class ProfileCodecs {
	private ProfileCodecs() {
	}

	public static final Codec<Trait> TRAIT = RecordCodecBuilder.create(i -> i.group(
			Codec.DOUBLE.fieldOf("value").forGetter(Trait::value),
			Codec.INT.fieldOf("samples").forGetter(Trait::samples)
	).apply(i, Trait::new));

	public static final Codec<Distribution> DISTRIBUTION = RecordCodecBuilder.create(i -> i.group(
			Codec.DOUBLE.listOf().fieldOf("weights").forGetter(d -> toList(d.weights())),
			Codec.INT.fieldOf("samples").forGetter(Distribution::samples)
	).apply(i, (weights, samples) -> Distribution.of(weights.stream().mapToDouble(Double::doubleValue).toArray(), samples)));

	public static final Codec<GearSnapshot> GEAR = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.optionalFieldOf("weapon", "").forGetter(GearSnapshot::weapon),
			Codec.STRING.optionalFieldOf("head", "").forGetter(GearSnapshot::head),
			Codec.STRING.optionalFieldOf("chest", "").forGetter(GearSnapshot::chest),
			Codec.STRING.optionalFieldOf("legs", "").forGetter(GearSnapshot::legs),
			Codec.STRING.optionalFieldOf("feet", "").forGetter(GearSnapshot::feet),
			Codec.STRING.optionalFieldOf("offhand", "").forGetter(GearSnapshot::offhand)
	).apply(i, GearSnapshot::new));

	public static final Codec<PlayerProfile> PROFILE = RecordCodecBuilder.create(i -> i.group(
			Codec.unboundedMap(Codec.STRING, TRAIT).optionalFieldOf("traits", Map.of()).forGetter(ProfileCodecs::traitsByKey),
			DISTRIBUTION.optionalFieldOf("weaponPref", Distribution.empty(WeaponType.values().length)).forGetter(PlayerProfile::weaponPref),
			DISTRIBUTION.optionalFieldOf("healItemPref", Distribution.empty(HealItem.values().length)).forGetter(PlayerProfile::healItemPref),
			Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("favoriteBlocks", Map.of()).forGetter(PlayerProfile::favoriteBlocks),
			GEAR.optionalFieldOf("gear", GearSnapshot.EMPTY).forGetter(PlayerProfile::gear)
	).apply(i, ProfileCodecs::build));

	private static Map<String, Trait> traitsByKey(PlayerProfile profile) {
		Map<String, Trait> out = new LinkedHashMap<>();
		profile.traits().forEach((id, trait) -> out.put(id.key(), trait));
		return out;
	}

	private static PlayerProfile build(Map<String, Trait> traits, Distribution weapons, Distribution heals,
			Map<String, Integer> blocks, GearSnapshot gear) {
		PlayerProfile profile = new PlayerProfile();
		EnumMap<TraitId, Trait> byId = new EnumMap<>(TraitId.class);
		traits.forEach((key, trait) -> {
			TraitId id = TraitId.byKey(key);
			if (id != null) {
				byId.put(id, trait);
			}
		});
		byId.forEach(profile::setTrait);
		profile.setWeaponPref(weapons);
		profile.setHealItemPref(heals);
		profile.setFavoriteBlocks(blocks);
		profile.setGear(gear);
		return profile;
	}

	private static List<Double> toList(double[] values) {
		return java.util.Arrays.stream(values).boxed().toList();
	}
}

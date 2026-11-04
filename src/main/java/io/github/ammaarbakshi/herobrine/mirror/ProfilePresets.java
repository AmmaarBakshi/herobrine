package io.github.ammaarbakshi.herobrine.mirror;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Hand-made profiles for testing the Mirror AI without hours of real fighting
 * ({@code /herobrine profile <player> preset <name>}). Every trait is fully "known".
 */
public enum ProfilePresets {
	BOW_KITER {
		@Override
		void apply(PlayerProfile p) {
			weapon(p, WeaponType.SWORD);
			set(p, TraitId.AVG_ATTACK_CHARGE, 0.9);
			set(p, TraitId.CRIT_RATE, 0.1);
			set(p, TraitId.SPRINT_HIT_RATE, 0.2);
			set(p, TraitId.RANGED_RATIO, 0.85);
			set(p, TraitId.BOW_DRAW_TICKS, 18);
			set(p, TraitId.STRAFE_BIAS, 0.55);
			set(p, TraitId.STRAFE_INTENSITY, 0.7);
			set(p, TraitId.PREFERRED_RANGE, 14);
			set(p, TraitId.JUMP_FREQUENCY, 0.2);
			set(p, TraitId.SHIELD_USE_RATE, 0.0);
			set(p, TraitId.HEAL_THRESHOLD, 0.5);
			set(p, TraitId.HEAL_THRESHOLD_SPREAD, 0.12);
			set(p, TraitId.RETREAT_THRESHOLD, 0.9);
			heal(p, HealItem.GOLDEN_APPLE);
			p.setGear(new GearSnapshot("minecraft:iron_sword", "minecraft:leather_helmet", "minecraft:chainmail_chestplate",
					"minecraft:leather_leggings", "minecraft:iron_boots", ""));
		}
	},
	SWORD_SPAMMER {
		@Override
		void apply(PlayerProfile p) {
			weapon(p, WeaponType.SWORD);
			set(p, TraitId.AVG_ATTACK_CHARGE, 0.3);
			set(p, TraitId.CRIT_RATE, 0.05);
			set(p, TraitId.SPRINT_HIT_RATE, 0.65);
			set(p, TraitId.RANGED_RATIO, 0.0);
			set(p, TraitId.STRAFE_BIAS, -0.1);
			set(p, TraitId.STRAFE_INTENSITY, 0.2);
			set(p, TraitId.PREFERRED_RANGE, 1.8);
			set(p, TraitId.JUMP_FREQUENCY, 0.7);
			set(p, TraitId.SHIELD_USE_RATE, 0.0);
			set(p, TraitId.HEAL_THRESHOLD, 0.25);
			set(p, TraitId.HEAL_THRESHOLD_SPREAD, 0.15);
			set(p, TraitId.RETREAT_THRESHOLD, 0.1);
			heal(p, HealItem.FOOD);
			p.setGear(new GearSnapshot("minecraft:diamond_sword", "minecraft:iron_helmet", "minecraft:iron_chestplate",
					"minecraft:iron_leggings", "minecraft:iron_boots", ""));
		}
	},
	SHIELD_TURTLE {
		@Override
		void apply(PlayerProfile p) {
			weapon(p, WeaponType.SWORD);
			set(p, TraitId.AVG_ATTACK_CHARGE, 1.0);
			set(p, TraitId.CRIT_RATE, 0.4);
			set(p, TraitId.SPRINT_HIT_RATE, 0.1);
			set(p, TraitId.RANGED_RATIO, 0.05);
			set(p, TraitId.STRAFE_BIAS, 0.05);
			set(p, TraitId.STRAFE_INTENSITY, 0.15);
			set(p, TraitId.PREFERRED_RANGE, 3.0);
			set(p, TraitId.JUMP_FREQUENCY, 0.15);
			set(p, TraitId.SHIELD_USE_RATE, 0.85);
			set(p, TraitId.SHIELD_REACTION_TICKS, 4);
			set(p, TraitId.HEAL_THRESHOLD, 0.5);
			set(p, TraitId.HEAL_THRESHOLD_SPREAD, 0.05);
			set(p, TraitId.RETREAT_THRESHOLD, 0.3);
			heal(p, HealItem.GOLDEN_APPLE);
			p.setGear(new GearSnapshot("minecraft:iron_sword", "minecraft:iron_helmet", "minecraft:iron_chestplate",
					"minecraft:iron_leggings", "minecraft:iron_boots", "minecraft:shield"));
		}
	},
	PILLAR_COWARD {
		@Override
		void apply(PlayerProfile p) {
			weapon(p, WeaponType.SWORD);
			set(p, TraitId.AVG_ATTACK_CHARGE, 0.7);
			set(p, TraitId.CRIT_RATE, 0.15);
			set(p, TraitId.SPRINT_HIT_RATE, 0.2);
			set(p, TraitId.RANGED_RATIO, 0.3);
			set(p, TraitId.BOW_DRAW_TICKS, 14);
			set(p, TraitId.STRAFE_BIAS, -0.6);
			set(p, TraitId.STRAFE_INTENSITY, 0.5);
			set(p, TraitId.PREFERRED_RANGE, 5.0);
			set(p, TraitId.JUMP_FREQUENCY, 0.4);
			set(p, TraitId.SHIELD_USE_RATE, 0.0);
			set(p, TraitId.HEAL_THRESHOLD, 0.4);
			set(p, TraitId.HEAL_THRESHOLD_SPREAD, 0.1);
			set(p, TraitId.RETREAT_THRESHOLD, 0.6);
			set(p, TraitId.PILLAR_RATE, 0.9);
			set(p, TraitId.PEARL_ESCAPE_RATE, 0.5);
			heal(p, HealItem.FOOD);
			p.setFavoriteBlocks(Map.of("minecraft:cobblestone", 40, "minecraft:dirt", 20));
			p.setGear(new GearSnapshot("minecraft:stone_sword", "minecraft:leather_helmet", "minecraft:leather_chestplate",
					"minecraft:leather_leggings", "minecraft:leather_boots", ""));
		}
	};

	/** Large enough that every trait counts as known for any sane {@code minTraitSamples}. */
	static final int SAMPLES = 1000;

	abstract void apply(PlayerProfile profile);

	public PlayerProfile build() {
		PlayerProfile profile = new PlayerProfile();
		apply(profile);
		return profile;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static Optional<ProfilePresets> byId(String id) {
		return Arrays.stream(values()).filter(p -> p.id().equals(id)).findFirst();
	}

	public static List<String> ids() {
		return Arrays.stream(values()).map(ProfilePresets::id).toList();
	}

	private static void set(PlayerProfile p, TraitId id, double value) {
		p.setTrait(id, new Trait(value, SAMPLES));
	}

	private static void weapon(PlayerProfile p, WeaponType type) {
		double[] weights = new double[WeaponType.values().length];
		weights[type.ordinal()] = 1.0;
		p.setWeaponPref(Distribution.of(weights, SAMPLES));
	}

	private static void heal(PlayerProfile p, HealItem item) {
		double[] weights = new double[HealItem.values().length];
		weights[item.ordinal()] = 1.0;
		p.setHealItemPref(Distribution.of(weights, SAMPLES));
	}
}

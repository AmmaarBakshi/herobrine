package io.github.ammaarbakshi.herobrine.mirror;

import java.util.Optional;

/**
 * What the player fights with, as registry ids (e.g. {@code minecraft:diamond_sword}) so this
 * stays free of Minecraft types. Herobrine equips fresh, unenchanted copies of these.
 *
 * @param weapon   most-used weapon item, empty if they fight bare-handed
 * @param head     helmet id or empty
 * @param chest    chestplate id or empty
 * @param legs     leggings id or empty
 * @param feet     boots id or empty
 * @param offhand  offhand item (shield, totem, ...) or empty
 */
public record GearSnapshot(String weapon, String head, String chest, String legs, String feet, String offhand) {
	public static final GearSnapshot EMPTY = new GearSnapshot("", "", "", "", "", "");

	public GearSnapshot {
		weapon = orEmpty(weapon);
		head = orEmpty(head);
		chest = orEmpty(chest);
		legs = orEmpty(legs);
		feet = orEmpty(feet);
		offhand = orEmpty(offhand);
	}

	private static String orEmpty(String s) {
		return s == null ? "" : s;
	}

	public static Optional<String> nonEmpty(String id) {
		return id.isEmpty() ? Optional.empty() : Optional.of(id);
	}

	public GearSnapshot withWeapon(String id) {
		return new GearSnapshot(id, head, chest, legs, feet, offhand);
	}

	public GearSnapshot withArmor(String head, String chest, String legs, String feet) {
		return new GearSnapshot(weapon, head, chest, legs, feet, offhand);
	}

	public GearSnapshot withOffhand(String id) {
		return new GearSnapshot(weapon, head, chest, legs, feet, id);
	}
}

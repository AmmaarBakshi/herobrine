package io.github.ammaarbakshi.herobrine.memory;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

/**
 * Equipment progression, weakest first. Worked out from item ids ({@code iron_pickaxe},
 * {@code diamond_chestplate}...) so it covers every tool, weapon and armour piece of a material,
 * including ones added in later versions, without a hand-kept item list.
 */
public enum GearTier {
	NONE,
	WOODEN,
	STONE,
	COPPER,
	IRON,
	DIAMOND,
	NETHERITE;

	/** Item kinds that count: tools, weapons and armour. */
	private static final String[] KINDS = {
			"_sword", "_pickaxe", "_axe", "_shovel", "_hoe", "_spear",
			"_helmet", "_chestplate", "_leggings", "_boots"
	};

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public boolean isAbove(GearTier other) {
		return ordinal() > other.ordinal();
	}

	/**
	 * The tier of an item, from its id path ({@code minecraft:diamond_sword} -> {@code diamond_sword}),
	 * or {@link #NONE} if it isn't a tiered tool, weapon or armour piece. Gold and leather don't count:
	 * nobody upgrades to them.
	 */
	public static GearTier ofItemPath(String path) {
		String kind = null;
		for (String candidate : KINDS) {
			if (path.endsWith(candidate)) {
				kind = candidate;
				break;
			}
		}
		if (kind == null) {
			return NONE;
		}
		String material = path.substring(0, path.length() - kind.length());
		GearTier tier = byMaterial(material);
		return tier == null ? NONE : tier;
	}

	private static @Nullable GearTier byMaterial(String material) {
		return switch (material) {
			case "wooden" -> WOODEN;
			case "stone" -> STONE;
			case "copper" -> COPPER;
			case "iron" -> IRON;
			case "diamond" -> DIAMOND;
			case "netherite" -> NETHERITE;
			default -> null;
		};
	}

	public static GearTier byOrdinal(int ordinal) {
		GearTier[] values = values();
		return values[Math.max(0, Math.min(values.length - 1, ordinal))];
	}
}

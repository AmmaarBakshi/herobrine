package io.github.ammaarbakshi.herobrine.mirror;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything Herobrine has learned about how one player fights.
 *
 * <p>Mutable on purpose: the recorder updates it from the server thread several times a second,
 * and copying the whole profile on each sample would be waste. It is only ever touched from the
 * server thread; anything that crosses threads (network sync) takes a {@link #copy()}.
 */
public final class PlayerProfile {
	/** Combat block types remembered; least-used entries are evicted beyond this. */
	public static final int MAX_FAVORITE_BLOCKS = 8;

	private final EnumMap<TraitId, Trait> traits = new EnumMap<>(TraitId.class);
	private Distribution weaponPref = Distribution.empty(WeaponType.values().length);
	private Distribution healItemPref = Distribution.empty(HealItem.values().length);
	private final LinkedHashMap<String, Integer> favoriteBlocks = new LinkedHashMap<>();
	private GearSnapshot gear = GearSnapshot.EMPTY;

	public Trait trait(TraitId id) {
		return traits.getOrDefault(id, Trait.EMPTY);
	}

	/** Learned value, or the trait's built-in fallback until it has enough samples. */
	public double value(TraitId id, int minSamples) {
		return trait(id).valueOr(minSamples, id.fallback());
	}

	public void observe(TraitId id, double x) {
		traits.put(id, trait(id).update(x, id.alpha()));
	}

	public void setTrait(TraitId id, Trait trait) {
		traits.put(id, trait);
	}

	public Distribution weaponPref() {
		return weaponPref;
	}

	public WeaponType topWeapon() {
		return WeaponType.values()[weaponPref.top(WeaponType.SWORD.ordinal())];
	}

	public void observeWeapon(WeaponType type) {
		weaponPref = weaponPref.observe(type.ordinal(), TraitId.AVG_ATTACK_CHARGE.alpha());
	}

	public void setWeaponPref(Distribution distribution) {
		weaponPref = distribution.resized(WeaponType.values().length);
	}

	public Distribution healItemPref() {
		return healItemPref;
	}

	public HealItem topHealItem() {
		return HealItem.values()[healItemPref.top(HealItem.FOOD.ordinal())];
	}

	public void observeHealItem(HealItem item) {
		healItemPref = healItemPref.observe(item.ordinal(), TraitId.HEAL_THRESHOLD.alpha());
	}

	public void setHealItemPref(Distribution distribution) {
		healItemPref = distribution.resized(HealItem.values().length);
	}

	/** Records a block placed in combat. Bounded: evicts the least-used block when full. */
	public void observeBlock(String blockId) {
		favoriteBlocks.merge(blockId, 1, Integer::sum);
		if (favoriteBlocks.size() > MAX_FAVORITE_BLOCKS) {
			String least = null;
			int leastCount = Integer.MAX_VALUE;
			for (Map.Entry<String, Integer> e : favoriteBlocks.entrySet()) {
				if (!e.getKey().equals(blockId) && e.getValue() < leastCount) {
					least = e.getKey();
					leastCount = e.getValue();
				}
			}
			favoriteBlocks.remove(least);
		}
	}

	public Map<String, Integer> favoriteBlocks() {
		return Collections.unmodifiableMap(favoriteBlocks);
	}

	/** Block ids sorted most-placed first. */
	public List<String> favoriteBlocksRanked() {
		return favoriteBlocks.entrySet().stream()
				.sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
				.map(Map.Entry::getKey)
				.toList();
	}

	public void setFavoriteBlocks(Map<String, Integer> blocks) {
		favoriteBlocks.clear();
		blocks.entrySet().stream()
				.sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
				.limit(MAX_FAVORITE_BLOCKS)
				.forEach(e -> favoriteBlocks.put(e.getKey(), Math.max(1, e.getValue())));
	}

	public GearSnapshot gear() {
		return gear;
	}

	public void setGear(GearSnapshot gear) {
		this.gear = gear == null ? GearSnapshot.EMPTY : gear;
	}

	public Map<TraitId, Trait> traits() {
		return Collections.unmodifiableMap(traits);
	}

	public void reset() {
		traits.clear();
		weaponPref = Distribution.empty(WeaponType.values().length);
		healItemPref = Distribution.empty(HealItem.values().length);
		favoriteBlocks.clear();
		gear = GearSnapshot.EMPTY;
	}

	public PlayerProfile copy() {
		PlayerProfile copy = new PlayerProfile();
		copy.copyFrom(this);
		return copy;
	}

	public void copyFrom(PlayerProfile other) {
		traits.clear();
		traits.putAll(other.traits);
		weaponPref = other.weaponPref;
		healItemPref = other.healItemPref;
		favoriteBlocks.clear();
		favoriteBlocks.putAll(other.favoriteBlocks);
		gear = other.gear;
	}

	public boolean isEmpty() {
		return traits.isEmpty() && weaponPref.samples() == 0 && healItemPref.samples() == 0 && favoriteBlocks.isEmpty();
	}
}

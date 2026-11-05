package io.github.ammaarbakshi.herobrine.memory;

import java.util.Locale;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * Moments of a player's journey he recognises, each at most once. Some are read from the vanilla
 * advancement that already tracks them; the rest are detected by the observation controller.
 */
public enum Milestone {
	FIRST_HOUSE(null),
	FIRST_NIGHT(null),
	FIRST_MINING(null),
	FIRST_NETHER_PORTAL("story/enter_the_nether"),
	FIRST_ENCHANTMENT("story/enchant_item"),
	FIRST_VILLAGE(null),
	FIRST_DIAMONDS("story/mine_diamond"),
	FIRST_ELYTRA("end/elytra"),
	FIRST_DRAGON("end/kill_dragon"),
	FOUND_BERSERK(null);

	private final @Nullable String advancement;

	Milestone(@Nullable String advancement) {
		this.advancement = advancement;
	}

	/** Stable id, used in saves and as the {@code milestone:<id>} dialogue tag. */
	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** Path of the vanilla advancement ({@code minecraft} namespace) that marks it, if any. */
	public Optional<String> advancement() {
		return Optional.ofNullable(advancement);
	}

	public static Optional<Milestone> byId(String id) {
		for (Milestone milestone : values()) {
			if (milestone.id().equals(id)) {
				return Optional.of(milestone);
			}
		}
		return Optional.empty();
	}
}

package io.github.ammaarbakshi.herobrine.haunt.events;

import io.github.ammaarbakshi.herobrine.config.HerobrineConfig.Griefing;
import io.github.ammaarbakshi.herobrine.haunt.HauntPhase;

/**
 * Something Herobrine can do to a player. Implementations must be cheap to reject (check
 * {@link #weight} conditions without scanning the world) and bounded when they do fire.
 */
public interface HauntEvent {
	enum Category {
		/** World signs: block edits, sounds, messages. Listed by {@code /herobrine sign}. */
		SIGN,
		/** Herobrine himself appears. */
		SIGHTING
	}

	/** Stable id used by commands, e.g. {@code torch_thief}. */
	String id();

	default Category category() {
		return Category.SIGN;
	}

	/** Earliest phase at which the director may pick this event on its own. */
	default HauntPhase minPhase() {
		return HauntPhase.SIGNS;
	}

	/** Griefing level needed; {@link Griefing#NONE} for events that never touch blocks. */
	default Griefing requiredGriefing() {
		return Griefing.NONE;
	}

	/** Relative chance of being picked now; 0 means not possible right now. */
	int weight(EventContext ctx);

	/** Does it. Returns false if nothing suitable was found, so the director can try another. */
	boolean fire(EventContext ctx);
}

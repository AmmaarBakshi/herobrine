package io.github.ammaarbakshi.herobrine.mirror;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.entity.LivingEntity;

/**
 * Short-lived recorder bookkeeping for one player: whether they are fighting, who, and the
 * open "did they do X in time?" windows. Not saved; a relog simply starts a new fight.
 */
public final class CombatState {
	/** Server tick of the last hit given or taken against a hostile; -inf when never. */
	long lastCombatTick = Long.MIN_VALUE / 2;
	boolean inCombat;
	long episodeStart;
	boolean pillaredThisEpisode;
	@Nullable LivingEntity target;

	// Movement sampling
	boolean hasPrevious;
	double prevX;
	double prevZ;
	int prevJumps;
	int backingOffSamples;

	// Shield: ticks from an unblocked hit to raising the shield
	long shieldReactionFrom = -1;
	boolean wasRaisingShield;

	// Bow: last observed draw, so the arrow that appears next tick knows how long it was drawn
	int lastBowDrawTicks;
	long lastBowDrawTick = Long.MIN_VALUE / 2;

	// Pearl escape: opened when health drops low while they carry pearls
	long pearlWindowEnd = -1;
	boolean lowHealthArmed = true;

	/** A debug HUD update is due; sent outside the measured recorder time. */
	boolean debugPending;

	public boolean inCombat() {
		return inCombat;
	}

	public @Nullable LivingEntity target() {
		return target;
	}
}

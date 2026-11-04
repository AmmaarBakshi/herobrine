package io.github.ammaarbakshi.herobrine.mirror;

import java.util.ArrayDeque;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
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
	/** Blocks they put under their own feet this fight, newest last (the anti-pillar counter's targets). */
	private final ArrayDeque<BlockPos> pillarBlocks = new ArrayDeque<>();

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

	public static final int MAX_PILLAR_BLOCKS = 8;

	public void rememberPillarBlock(BlockPos pos) {
		pillarBlocks.addLast(pos.immutable());
		while (pillarBlocks.size() > MAX_PILLAR_BLOCKS) {
			pillarBlocks.removeFirst();
		}
	}

	void clearPillarBlocks() {
		pillarBlocks.clear();
	}

	public List<BlockPos> pillarBlocks() {
		return List.copyOf(pillarBlocks);
	}

	public void forgetPillarBlock(BlockPos pos) {
		pillarBlocks.remove(pos);
	}
}

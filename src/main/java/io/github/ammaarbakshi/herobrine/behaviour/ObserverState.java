package io.github.ammaarbakshi.herobrine.behaviour;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;

import io.github.ammaarbakshi.herobrine.memory.BossKind;

/**
 * What the observer is following about one player right now: weather, caves, building, nearby
 * bosses, places. Transient (not saved, fresh after a relog or respawn): anything worth keeping
 * goes into {@link io.github.ammaarbakshi.herobrine.memory.ObserverMemory} instead.
 */
public final class ObserverState {
	/** Game time this player entity started being observed (join or respawn). */
	public long since = -1;
	/** Server tick of this player's next look-around (staggered per player). */
	public long nextScan = -1;
	/** Server tick of the next "does he appear now?" roll. */
	public long nextAppearanceRoll = -1;

	public boolean nearVillage;
	public boolean nearBerserk;
	public boolean nearHome;
	/** What kind of place he chose for the current appearance (tree, river, home...), for the lines. */
	public @Nullable String flavour;
	/** Weather he last saw them out in: clear, rain or thunder. */
	public String weather = "clear";

	/** Game time they went underground, or -1 while they're on the surface. */
	public long undergroundSince = -1;
	/** Underground ticks not yet counted as a whole minute in memory. */
	public int undergroundTicks;
	/** He has had his chance to comment on this trip underground. */
	public boolean caveNoticed;

	/** Building: where a burst of block placing is centred, how many, since when. */
	public @Nullable BlockPos buildCenter;
	public int buildCount;
	public long buildStart;

	/** Game time until which a boss fight is considered on: he keeps his distance. */
	public long bossFightUntil = -1;
	/** When each boss kind was last in contact with the player; a long gap starts a new encounter. */
	public final Map<BossKind, Long> bossNoticed = new EnumMap<>(BossKind.class);
	/** {@code kind:phase} pairs already offered to the voice this encounter: one comment per phase. */
	public final Set<String> bossPhases = new HashSet<>();

	/**
	 * Overworld day number last checked for "survived the night". A death or relog makes a fresh
	 * state, so a night only counts if {@link #since} is before it began.
	 */
	public long dayChecked = -1;

	public boolean inBossFight(long now) {
		return now < bossFightUntil;
	}
}

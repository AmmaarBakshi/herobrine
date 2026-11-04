package io.github.ammaarbakshi.herobrine.haunt;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Per-player haunting progress, persisted with the player (survives relogs, deaths, restarts).
 * Mutable and server-thread only; see {@link io.github.ammaarbakshi.herobrine.registry.ModAttachments}.
 */
public final class HauntState {
	public static final Codec<HauntState> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.DOUBLE.optionalFieldOf("level", 0.0).forGetter(s -> s.level),
			Codec.LONG.optionalFieldOf("lastDayCounted", -1L).forGetter(s -> s.lastDayCounted),
			Codec.LONG.optionalFieldOf("jumpscareDay", -1L).forGetter(s -> s.jumpscareDay),
			Codec.INT.optionalFieldOf("jumpscaresToday", 0).forGetter(s -> s.jumpscaresToday),
			Codec.LONG.optionalFieldOf("fakeChatDay", -1L).forGetter(s -> s.fakeChatDay),
			Codec.INT.optionalFieldOf("sightings", 0).forGetter(s -> s.sightings)
	).apply(i, HauntState::new));

	private double level;
	private long lastDayCounted;
	private long jumpscareDay;
	private int jumpscaresToday;
	private long fakeChatDay;
	private int sightings;

	public HauntState() {
		this(0.0, -1L, -1L, 0, -1L, 0);
	}

	private HauntState(double level, long lastDayCounted, long jumpscareDay, int jumpscaresToday, long fakeChatDay, int sightings) {
		this.level = clamp(level);
		this.lastDayCounted = lastDayCounted;
		this.jumpscareDay = jumpscareDay;
		this.jumpscaresToday = jumpscaresToday;
		this.fakeChatDay = fakeChatDay;
		this.sightings = sightings;
	}

	/** Fractional so slow trickles (e.g. +1 per minute underground) accumulate exactly. */
	public double level() {
		return level;
	}

	public int levelInt() {
		return (int) Math.floor(level);
	}

	public void setLevel(double level) {
		this.level = clamp(level);
	}

	public void add(double amount) {
		setLevel(level + amount);
	}

	public long lastDayCounted() {
		return lastDayCounted;
	}

	public void setLastDayCounted(long day) {
		this.lastDayCounted = day;
	}

	public boolean canJumpscare(long day, int maxPerDay) {
		return (jumpscareDay != day ? 0 : jumpscaresToday) < maxPerDay;
	}

	public void recordJumpscare(long day) {
		if (jumpscareDay != day) {
			jumpscareDay = day;
			jumpscaresToday = 0;
		}
		jumpscaresToday++;
	}

	public boolean canFakeChat(long day) {
		return fakeChatDay != day;
	}

	public void recordFakeChat(long day) {
		fakeChatDay = day;
	}

	public int sightings() {
		return sightings;
	}

	public void recordSighting() {
		sightings++;
	}

	private static double clamp(double v) {
		if (Double.isNaN(v)) {
			return 0.0;
		}
		return Math.max(0.0, Math.min(HauntPhase.MAX_LEVEL, v));
	}
}

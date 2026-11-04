package io.github.ammaarbakshi.herobrine.encounter;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.ammaarbakshi.herobrine.mirror.Habit;

/**
 * Every fight a player has had with the Mirror, persisted with the player. Only the most
 * recent {@link #MAX_RECORDS} fights are kept in full; the totals count all of them.
 */
public final class EncounterHistory {
	public static final int MAX_RECORDS = 16;
	/** At most one natural confrontation per in-game day. */
	public static final long NATURAL_COOLDOWN_DAYS = 1;

	public static final Codec<EncounterHistory> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.optionalFieldOf("total", 0).forGetter(h -> h.total),
			Codec.INT.optionalFieldOf("wins", 0).forGetter(h -> h.wins),
			EncounterRecord.CODEC.listOf().optionalFieldOf("recent", List.of()).forGetter(h -> h.recent)
	).apply(i, EncounterHistory::new));

	private int total;
	private int wins;
	private final List<EncounterRecord> recent;

	public EncounterHistory() {
		this(0, 0, List.of());
	}

	private EncounterHistory(int total, int wins, List<EncounterRecord> recent) {
		this.total = Math.max(0, total);
		this.wins = Math.max(0, Math.min(this.total, wins));
		this.recent = new ArrayList<>(recent);
	}

	public int total() {
		return total;
	}

	public int wins() {
		return wins;
	}

	public int losses() {
		return total - wins;
	}

	public List<EncounterRecord> recent() {
		return List.copyOf(recent);
	}

	public void add(EncounterRecord record) {
		total++;
		if (record.won()) {
			wins++;
		}
		recent.add(record);
		while (recent.size() > MAX_RECORDS) {
			recent.removeFirst();
		}
	}

	/** What the last fight showed the player relies on; empty before the first fight. */
	public List<Habit> lastFocus() {
		return recent.isEmpty() ? List.of() : recent.getLast().focus();
	}

	/** True if a natural confrontation may happen on {@code day}. */
	public boolean naturalAllowed(long day) {
		return recent.isEmpty() || day - recent.getLast().day() >= NATURAL_COOLDOWN_DAYS;
	}

	public void clear() {
		total = 0;
		wins = 0;
		recent.clear();
	}
}

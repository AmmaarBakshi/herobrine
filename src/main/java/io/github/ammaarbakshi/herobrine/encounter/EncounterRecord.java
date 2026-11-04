package io.github.ammaarbakshi.herobrine.encounter;

import java.util.List;
import java.util.Locale;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.ammaarbakshi.herobrine.mirror.Habit;

/**
 * One finished Mirror fight.
 *
 * @param day          in-game day the fight ended on
 * @param won          true if the player killed him
 * @param durationTicks how long the fight lasted
 * @param healsUsed    heals Herobrine spent
 * @param focus        the player's most-relied-on habits as of this fight: what he counters next time
 */
public record EncounterRecord(long day, boolean won, int durationTicks, int healsUsed, List<Habit> focus) {
	private static final Codec<Habit> HABIT = Codec.STRING.comapFlatMap(s -> {
		try {
			return DataResult.success(Habit.valueOf(s.toUpperCase(Locale.ROOT)));
		} catch (IllegalArgumentException e) {
			return DataResult.error(() -> "Unknown habit: " + s);
		}
	}, h -> h.name().toLowerCase(Locale.ROOT));

	public static final Codec<EncounterRecord> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.fieldOf("day").forGetter(EncounterRecord::day),
			Codec.BOOL.fieldOf("won").forGetter(EncounterRecord::won),
			Codec.INT.optionalFieldOf("durationTicks", 0).forGetter(EncounterRecord::durationTicks),
			Codec.INT.optionalFieldOf("healsUsed", 0).forGetter(EncounterRecord::healsUsed),
			HABIT.listOf().lenientOptionalFieldOf("focus", List.of()).forGetter(EncounterRecord::focus)
	).apply(i, EncounterRecord::new));

	public EncounterRecord {
		focus = List.copyOf(focus);
	}
}

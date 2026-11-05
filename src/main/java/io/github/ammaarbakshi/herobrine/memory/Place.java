package io.github.ammaarbakshi.herobrine.memory;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Where and when something happened. Plain values (dimension id as a string) so the memory model
 * stays free of Minecraft classes and can be unit tested.
 *
 * @param dimension dimension id, e.g. {@code minecraft:overworld}
 * @param time      overworld game time (ticks), which is saved with the world and never runs backwards
 */
public record Place(String dimension, int x, int y, int z, long time) {
	public static final Codec<Place> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("dimension").forGetter(Place::dimension),
			Codec.INT.fieldOf("x").forGetter(Place::x),
			Codec.INT.fieldOf("y").forGetter(Place::y),
			Codec.INT.fieldOf("z").forGetter(Place::z),
			Codec.LONG.optionalFieldOf("time", -1L).forGetter(Place::time)
	).apply(i, Place::new));

	public double distanceSqr(String otherDimension, double ox, double oy, double oz) {
		if (!dimension.equals(otherDimension)) {
			return Double.POSITIVE_INFINITY;
		}
		double dx = x + 0.5 - ox;
		double dy = y - oy;
		double dz = z + 0.5 - oz;
		return dx * dx + dy * dy + dz * dz;
	}
}

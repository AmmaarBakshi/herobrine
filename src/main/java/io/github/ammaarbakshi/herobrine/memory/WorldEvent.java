package io.github.ammaarbakshi.herobrine.memory;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Something important that happened in the player's world (a boss killed, a milestone reached,
 * the memorial found), with where and when. Kept as a short diary, newest last.
 */
public record WorldEvent(String id, Place place) {
	public static final Codec<WorldEvent> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("id").forGetter(WorldEvent::id),
			Place.CODEC.fieldOf("place").forGetter(WorldEvent::place)
	).apply(i, WorldEvent::new));
}

package io.github.ammaarbakshi.herobrine.registry;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

import io.github.ammaarbakshi.herobrine.HerobrineMod;

/** Original sounds, synthesized by tools/gen_sounds.py (see CREDITS.md). */
public final class ModSounds {
	public static final Holder.Reference<SoundEvent> JUMPSCARE = register("jumpscare");
	public static final Holder.Reference<SoundEvent> DRONE = register("drone");
	public static final Holder.Reference<SoundEvent> WHISPER = register("whisper");
	public static final Holder.Reference<SoundEvent> HEARTBEAT = register("heartbeat");

	private ModSounds() {
	}

	private static Holder.Reference<SoundEvent> register(String name) {
		Identifier id = HerobrineMod.id(name);
		return Registry.registerForHolder(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	public static void init() {
	}
}

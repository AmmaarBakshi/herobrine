package io.github.ammaarbakshi.herobrine.haunt;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Positional sounds only the haunted player hears. Sent straight to their connection instead of
 * through the level, so a friend standing next to them hears nothing (and doubts them).
 */
public final class PrivateSounds {
	private PrivateSounds() {
	}

	public static void play(ServerPlayer player, Holder<SoundEvent> sound, SoundSource source, Vec3 at, float volume, float pitch) {
		player.connection.send(new ClientboundSoundPacket(sound, source, at.x, at.y, at.z, volume, pitch, player.getRandom().nextLong()));
	}

	public static void play(ServerPlayer player, SoundEvent sound, SoundSource source, Vec3 at, float volume, float pitch) {
		play(player, BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), source, at, volume, pitch);
	}
}

package io.github.ammaarbakshi.herobrine.haunt.events;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import io.github.ammaarbakshi.herobrine.haunt.HauntPhase;
import io.github.ammaarbakshi.herobrine.haunt.HauntScheduler;
import io.github.ammaarbakshi.herobrine.haunt.PrivateSounds;
import io.github.ammaarbakshi.herobrine.registry.ModSounds;
import io.github.ammaarbakshi.herobrine.registry.ModTags;

/**
 * Sounds with nothing behind them, heard only by the haunted player: footsteps closing in from
 * behind, a door creaking open and shut, someone mining inside the wall. Later phases add a
 * whisper and a heartbeat. Never touches the world.
 */
final class AmbientSoundEvent implements HauntEvent {
	private enum Cue {
		FOOTSTEPS,
		DOOR,
		MINING,
		WHISPER,
		HEARTBEAT
	}

	@Override
	public String id() {
		return "ambient_sound";
	}

	@Override
	public int weight(EventContext ctx) {
		return 12;
	}

	@Override
	public boolean fire(EventContext ctx) {
		RandomSource random = ctx.random();
		boolean late = ctx.phase().ordinal() >= HauntPhase.STALKING.ordinal() || ctx.forced();
		Cue[] cues = late ? Cue.values() : new Cue[] {Cue.FOOTSTEPS, Cue.DOOR, Cue.MINING};
		// Try every cue once, starting at a random one: mining needs rock nearby, the rest always work.
		int start = random.nextInt(cues.length);
		for (int i = 0; i < cues.length; i++) {
			if (play(ctx, cues[(start + i) % cues.length])) {
				return true;
			}
		}
		return false;
	}

	private static boolean play(EventContext ctx, Cue cue) {
		return switch (cue) {
			case FOOTSTEPS -> footsteps(ctx);
			case DOOR -> door(ctx);
			case MINING -> mining(ctx);
			case WHISPER -> {
				PrivateSounds.play(ctx.player(), ModSounds.WHISPER, SoundSource.AMBIENT, behind(ctx.player(), 1.5), 0.7F, 1.0F);
				yield true;
			}
			case HEARTBEAT -> {
				PrivateSounds.play(ctx.player(), ModSounds.HEARTBEAT, SoundSource.AMBIENT, ctx.player().getEyePosition(), 0.8F, 1.0F);
				yield true;
			}
		};
	}

	/** Five to eight steps walking up behind them, stopping just short. */
	private static boolean footsteps(EventContext ctx) {
		ServerPlayer player = ctx.player();
		RandomSource random = ctx.random();
		int steps = 5 + random.nextInt(4);
		int interval = 6 + random.nextInt(3);
		for (int s = 0; s < steps; s++) {
			double distance = 9.0 - s * (6.0 / steps);
			HauntScheduler.schedule(1 + s * interval, () -> {
				if (player.hasDisconnected() || !player.isAlive()) {
					return;
				}
				Vec3 at = behind(player, distance);
				BlockState ground = player.level().getBlockState(BlockPos.containing(at).below());
				SoundType type = ground.isAir() ? SoundType.STONE : ground.getSoundType();
				PrivateSounds.play(player, type.getStepSound(), SoundSource.HOSTILE, at, type.getVolume() * 0.3F, type.getPitch());
			});
		}
		return true;
	}

	/** A door creaks open somewhere close behind them, and a moment later swings shut. */
	private static boolean door(EventContext ctx) {
		ServerPlayer player = ctx.player();
		RandomSource random = ctx.random();
		Vec3 at = behind(player, 6 + random.nextDouble() * 6).add(random.nextDouble() * 4 - 2, 0, random.nextDouble() * 4 - 2);
		PrivateSounds.play(player, SoundEvents.WOODEN_DOOR_OPEN, SoundSource.BLOCKS, at, 0.8F, 0.85F + random.nextFloat() * 0.1F);
		HauntScheduler.schedule(25 + random.nextInt(30), () -> {
			if (!player.hasDisconnected()) {
				PrivateSounds.play(player, SoundEvents.WOODEN_DOOR_CLOSE, SoundSource.BLOCKS, at, 0.8F, 0.85F);
			}
		});
		return true;
	}

	/** Pick-on-stone from a solid block of rock a few metres away: hits, then a break. */
	private static boolean mining(EventContext ctx) {
		ServerPlayer player = ctx.player();
		RandomSource random = ctx.random();
		BlockPos rock = findRock(player.level(), player.blockPosition(), random);
		if (rock == null) {
			return false;
		}
		BlockState state = player.level().getBlockState(rock);
		SoundType type = state.getSoundType();
		Vec3 at = Vec3.atCenterOf(rock);
		int hits = 4 + random.nextInt(4);
		for (int h = 0; h < hits; h++) {
			SoundEvent sound = h == hits - 1 ? type.getBreakSound() : type.getHitSound();
			float volume = h == hits - 1 ? 0.6F : 0.35F;
			HauntScheduler.schedule(1 + h * 5, () -> {
				if (!player.hasDisconnected()) {
					PrivateSounds.play(player, sound, SoundSource.BLOCKS, at, volume, type.getPitch() * 0.8F);
				}
			});
		}
		return true;
	}

	/** A rock block 4-8 blocks away with rock on every side (so the sound comes from inside). */
	private static @Nullable BlockPos findRock(ServerLevel level, BlockPos origin, RandomSource random) {
		for (int i = 0; i < 12; i++) {
			BlockPos pos = origin.offset(Mth.randomBetweenInclusive(random, -8, 8), Mth.randomBetweenInclusive(random, -3, 3),
					Mth.randomBetweenInclusive(random, -8, 8));
			int distSqr = (int) pos.distSqr(origin);
			if (distSqr < 4 * 4 || distSqr > 8 * 8 || !level.isLoaded(pos) || !isRock(level, pos)) {
				continue;
			}
			if (isRock(level, pos.above()) && isRock(level, pos.below()) && isRock(level, pos.north())
					&& isRock(level, pos.south()) && isRock(level, pos.east()) && isRock(level, pos.west())) {
				return pos;
			}
		}
		return null;
	}

	private static boolean isRock(ServerLevel level, BlockPos pos) {
		return level.getBlockState(pos).is(ModTags.TUNNEL_CARVABLE);
	}

	/** A point {@code distance} blocks straight behind the player, at ear height. */
	private static Vec3 behind(ServerPlayer player, double distance) {
		double yaw = Math.toRadians(player.getYRot());
		return player.getEyePosition().add(Math.sin(yaw) * distance, -0.5, -Math.cos(yaw) * distance);
	}
}

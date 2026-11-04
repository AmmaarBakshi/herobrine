package io.github.ammaarbakshi.herobrine.item;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

import io.github.ammaarbakshi.herobrine.net.ModNetworking;

/**
 * Dropped when the Mirror is beaten. Looking into it shows what he learned about you: your
 * own profile, sent fresh from the server each time so it keeps up as you change.
 */
public final class MirrorShardItem extends Item {
	public MirrorShardItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player instanceof ServerPlayer serverPlayer) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 0.6F);
			ModNetworking.sendMirrorProfile(serverPlayer);
		}
		return InteractionResult.SUCCESS;
	}
}

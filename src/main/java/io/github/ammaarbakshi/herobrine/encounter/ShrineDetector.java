package io.github.ammaarbakshi.herobrine.encounter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;

import io.github.ammaarbakshi.herobrine.encounter.ConfrontationManager.StartResult;
import io.github.ammaarbakshi.herobrine.encounter.ConfrontationManager.Trigger;
import io.github.ammaarbakshi.herobrine.encounter.ShrinePattern.Part;
import io.github.ammaarbakshi.herobrine.perf.Perf;

/**
 * Watches for someone lighting a shrine. Costs nothing until a player uses flint and steel
 * or a fire charge on netherrack; the 19-block pattern is checked once, the tick after.
 */
public final class ShrineDetector {
	private record Pending(ServerLevel level, BlockPos netherrack, UUID player) {
	}

	private static final List<Pending> PENDING = new ArrayList<>();

	private ShrineDetector() {
	}

	public static void init() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer && !player.isSpectator()) {
				ItemStack stack = player.getItemInHand(hand);
				if ((stack.is(Items.FLINT_AND_STEEL) || stack.is(Items.FIRE_CHARGE))
						&& level.getBlockState(hit.getBlockPos()).is(Blocks.NETHERRACK)) {
					// The fire isn't there yet: vanilla places it after this callback.
					PENDING.add(new Pending(serverLevel, hit.getBlockPos().immutable(), player.getUUID()));
				}
			}
			return InteractionResult.PASS;
		});
		ServerTickEvents.END_SERVER_TICK.register(Perf.timed(Perf.WORLD, ShrineDetector::tick));
	}

	private static void tick(MinecraftServer server) {
		if (PENDING.isEmpty()) {
			return;
		}
		List<Pending> pending = List.copyOf(PENDING);
		PENDING.clear();
		for (Pending p : pending) {
			ServerPlayer player = server.getPlayerList().getPlayer(p.player());
			if (player == null || player.level() != p.level() || !isLitShrine(p.level(), p.netherrack())) {
				continue;
			}
			StartResult result = ConfrontationManager.begin(player, Trigger.SHRINE);
			if (result == StartResult.PEACEFUL) {
				player.sendOverlayMessage(Component.translatable("herobrine.shrine.peaceful"));
			}
		}
	}

	public static boolean isLitShrine(ServerLevel level, BlockPos netherrack) {
		return ShrinePattern.matches((dx, dy, dz) -> part(level.getBlockState(netherrack.offset(dx, dy, dz))));
	}

	private static Part part(BlockState state) {
		if (state.is(Blocks.GOLD_BLOCK)) {
			return Part.GOLD;
		}
		if (state.is(Blocks.NETHERRACK)) {
			return Part.NETHERRACK;
		}
		if (state.is(Blocks.REDSTONE_TORCH) || state.is(Blocks.REDSTONE_WALL_TORCH)) {
			return Part.REDSTONE_TORCH;
		}
		if (state.is(BlockTags.FIRE)) {
			return Part.FIRE;
		}
		return Part.OTHER;
	}
}

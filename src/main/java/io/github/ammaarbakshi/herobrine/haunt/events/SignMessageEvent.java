package io.github.ammaarbakshi.herobrine.haunt.events;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.phys.Vec3;

import io.github.ammaarbakshi.herobrine.config.HerobrineConfig.Griefing;
import io.github.ammaarbakshi.herobrine.haunt.WorldEditLog;
import io.github.ammaarbakshi.herobrine.haunt.WorldGuard;
import io.github.ammaarbakshi.herobrine.registry.ModAttachments;
import io.github.ammaarbakshi.herobrine.sighting.SightingController;
import io.github.ammaarbakshi.herobrine.sighting.SpawnFinder;

/**
 * Plants a waxed sign where the player will walk: ahead of them, or on a path they took
 * earlier, always out of sight. The text is translatable, so it shows in the player's language.
 */
final class SignMessageEvent implements HauntEvent {
	@Override
	public String id() {
		return "sign";
	}

	@Override
	public Griefing requiredGriefing() {
		return Griefing.NATURAL_ONLY;
	}

	@Override
	public int weight(EventContext ctx) {
		return 10;
	}

	@Override
	public boolean fire(EventContext ctx) {
		ServerPlayer player = ctx.player();
		ServerLevel level = ctx.level();
		for (BlockPos candidate : candidates(ctx)) {
			BlockPos feet = SpawnFinder.findStandable(level, candidate.getX(), candidate.getZ(), candidate.getY(), 3, false);
			if (feet == null || feet.distSqr(player.blockPosition()) < 5 * 5
					|| WorldGuard.isProtected(level, feet) || WorldGuard.isWatched(level, feet) || WorldGuard.isWatched(level, feet.above())) {
				continue;
			}
			return place(ctx, level, feet);
		}
		return false;
	}

	/** Straight ahead first, then places they walked through a while ago. */
	private static List<BlockPos> candidates(EventContext ctx) {
		ServerPlayer player = ctx.player();
		List<BlockPos> out = new ArrayList<>();
		Vec3 look = player.getViewVector(1.0F);
		Vec3 flat = new Vec3(look.x, 0, look.z);
		if (flat.lengthSqr() > 1.0E-4) {
			flat = flat.normalize();
			for (int d : new int[] {14, 18, 11, 22}) {
				out.add(BlockPos.containing(player.position().add(flat.scale(d))));
			}
		}
		List<BlockPos> path = ctx.trail().path();
		for (int i = 1; i < path.size(); i++) {
			out.add(path.get(i));
		}
		return out;
	}

	private boolean place(EventContext ctx, ServerLevel level, BlockPos pos) {
		ServerPlayer player = ctx.player();
		float yawFromPlayer = SightingController.yawToward(player.position(), Vec3.atBottomCenterOf(pos));
		BlockState sign = Blocks.OAK_SIGN.defaultBlockState()
				.setValue(StandingSignBlock.ROTATION, RotationSegment.convertToSegment(yawFromPlayer + 180.0F));

		WorldEditLog.Recorder edit = ctx.log().begin(level, id());
		edit.set(pos, sign, Block.UPDATE_ALL);
		if (!(level.getBlockEntity(pos) instanceof SignBlockEntity entity)) {
			edit.rollback();
			return false;
		}
		SignMessages.Message message = SignMessages.pick(ModAttachments.profile(player), ctx.config().minTraitSamples,
				player.getMaxHealth(), new Random(ctx.random().nextLong()));
		entity.setText(text(message), SignTextSlot.FRONT);
		entity.setWaxed(true);
		entity.setChanged();
		level.sendBlockUpdated(pos, sign, sign, Block.UPDATE_ALL);
		return edit.commit();
	}

	static SignText text(SignMessages.Message message) {
		List<Component> lines = new ArrayList<>(SignMessages.LINES);
		for (int i = 1; i <= SignMessages.LINES; i++) {
			lines.add(Component.translatableWithFallback(message.lineKey(i), "", message.args().toArray()));
		}
		return new SignText(lines, lines, DyeColor.BLACK, false);
	}
}

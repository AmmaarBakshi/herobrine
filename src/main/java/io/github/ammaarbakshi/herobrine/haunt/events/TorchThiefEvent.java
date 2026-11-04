package io.github.ammaarbakshi.herobrine.haunt.events;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import io.github.ammaarbakshi.herobrine.config.HerobrineConfig.Griefing;
import io.github.ammaarbakshi.herobrine.haunt.WorldEditLog;
import io.github.ammaarbakshi.herobrine.haunt.WorldGuard;
import io.github.ammaarbakshi.herobrine.registry.ModTags;

/** Takes up to three torches from a cave the player lit earlier. They come back to darkness. */
final class TorchThiefEvent implements HauntEvent {
	private static final int MAX_TORCHES = 3;
	private static final int RADIUS = 8;
	private static final int HEIGHT = 4;
	private static final int CAVES_TO_SEARCH = 3;
	/** Don't steal from where they are standing right now. */
	private static final double MIN_DISTANCE_SQR = 12 * 12;

	@Override
	public String id() {
		return "torch_thief";
	}

	@Override
	public Griefing requiredGriefing() {
		return Griefing.TORCHES_ONLY;
	}

	@Override
	public int weight(EventContext ctx) {
		return ctx.trail().caves().isEmpty() ? 0 : 10;
	}

	@Override
	public boolean fire(EventContext ctx) {
		ServerLevel level = ctx.level();
		List<BlockPos> torches = new ArrayList<>();
		int searched = 0;
		for (BlockPos cave : ctx.trail().caves()) {
			if (searched >= CAVES_TO_SEARCH || torches.size() >= MAX_TORCHES) {
				break;
			}
			if (cave.distSqr(ctx.player().blockPosition()) < MIN_DISTANCE_SQR && !ctx.forced()) {
				continue;
			}
			searched++;
			// Bounded cube scan around one remembered spot: at most 17*9*17 block reads.
			for (BlockPos pos : BlockPos.betweenClosed(cave.offset(-RADIUS, -HEIGHT, -RADIUS), cave.offset(RADIUS, HEIGHT, RADIUS))) {
				if (level.isLoaded(pos) && level.getBlockState(pos).is(ModTags.STEALABLE_TORCHES)) {
					torches.add(pos.immutable());
				}
			}
		}
		if (torches.isEmpty()) {
			return false;
		}
		Collections.shuffle(torches, new Random(ctx.random().nextLong()));
		WorldEditLog.Recorder edit = ctx.log().begin(level, id());
		for (BlockPos pos : torches) {
			if (edit.count() >= MAX_TORCHES) {
				break;
			}
			if (!WorldGuard.isProtected(level, pos) && !WorldGuard.isWatched(level, pos)) {
				edit.set(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			}
		}
		return edit.commit();
	}
}

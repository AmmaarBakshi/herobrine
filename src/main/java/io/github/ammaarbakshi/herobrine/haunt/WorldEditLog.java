package io.github.ammaarbakshi.herobrine.haunt;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import io.github.ammaarbakshi.herobrine.HerobrineMod;

/**
 * Every block Herobrine has changed, grouped per event, so {@code /herobrine undo} can put the
 * world back. Saved with the world (overworld data storage) so undo survives restarts.
 */
public final class WorldEditLog extends SavedData {
	/** Oldest batches are forgotten past this, keeping the save file small. */
	public static final int MAX_BATCHES = 512;

	public record Change(BlockPos pos, BlockState before, BlockState after) {
		static final Codec<Change> CODEC = RecordCodecBuilder.create(i -> i.group(
				BlockPos.CODEC.fieldOf("pos").forGetter(Change::pos),
				BlockState.CODEC.fieldOf("before").forGetter(Change::before),
				BlockState.CODEC.fieldOf("after").forGetter(Change::after)
		).apply(i, Change::new));
	}

	public record Batch(ResourceKey<Level> dimension, String event, long gameTime, List<Change> changes) {
		static final Codec<Batch> CODEC = RecordCodecBuilder.create(i -> i.group(
				Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(Batch::dimension),
				Codec.STRING.fieldOf("event").forGetter(Batch::event),
				Codec.LONG.fieldOf("gameTime").forGetter(Batch::gameTime),
				Change.CODEC.listOf().fieldOf("changes").forGetter(Batch::changes)
		).apply(i, Batch::new));
	}

	private static final Codec<WorldEditLog> CODEC = Batch.CODEC.listOf()
			.xmap(WorldEditLog::new, log -> List.copyOf(log.batches));

	public static final SavedDataType<WorldEditLog> TYPE =
			// No vanilla datafixer applies to mod data; Fabric API accepts null here.
			new SavedDataType<>(HerobrineMod.id("world_edits"), WorldEditLog::new, CODEC, null);

	private final Deque<Batch> batches = new ArrayDeque<>();

	public WorldEditLog() {
	}

	private WorldEditLog(List<Batch> batches) {
		this.batches.addAll(batches);
	}

	public static WorldEditLog get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	public int size() {
		return batches.size();
	}

	/** Starts recording one event's changes. Nothing is logged until {@link Recorder#commit()}. */
	public Recorder begin(ServerLevel level, String event) {
		return new Recorder(level, event);
	}

	/**
	 * Reverts the newest {@code count} events. A block is only restored if it still holds what
	 * Herobrine put there, so anything the player changed since is left alone.
	 *
	 * @return number of blocks restored
	 */
	public int undo(MinecraftServer server, int count) {
		int restored = 0;
		for (int i = 0; i < count && !batches.isEmpty(); i++) {
			Batch batch = batches.removeLast();
			ServerLevel level = server.getLevel(batch.dimension());
			if (level == null) {
				continue;
			}
			List<Change> changes = batch.changes();
			for (int c = changes.size() - 1; c >= 0; c--) {
				Change change = changes.get(c);
				if (level.isLoaded(change.pos()) && level.getBlockState(change.pos()) == change.after()) {
					level.setBlock(change.pos(), change.before(), Block.UPDATE_ALL);
					restored++;
				}
			}
		}
		setDirty();
		return restored;
	}

	private void add(Batch batch) {
		batches.addLast(batch);
		while (batches.size() > MAX_BATCHES) {
			batches.removeFirst();
		}
		setDirty();
	}

	/** Collects one event's block changes; commit to log them, or roll back to undo them. */
	public final class Recorder {
		private final ServerLevel level;
		private final String event;
		private final List<Change> changes = new ArrayList<>();

		private Recorder(ServerLevel level, String event) {
			this.level = level;
			this.event = event;
		}

		public void set(BlockPos pos, BlockState state, int flags) {
			BlockState before = level.getBlockState(pos);
			if (before == state) {
				return;
			}
			if (level.setBlock(pos, state, flags)) {
				changes.add(new Change(pos.immutable(), before, level.getBlockState(pos)));
			}
		}

		public int count() {
			return changes.size();
		}

		public boolean commit() {
			if (changes.isEmpty()) {
				return false;
			}
			add(new Batch(level.dimension(), event, level.getGameTime(), List.copyOf(changes)));
			return true;
		}

		/** Puts back everything recorded so far (e.g. a tunnel that turned out too short). */
		public void rollback() {
			for (int i = changes.size() - 1; i >= 0; i--) {
				Change change = changes.get(i);
				level.setBlock(change.pos(), change.before(), Block.UPDATE_ALL);
			}
			changes.clear();
		}
	}
}

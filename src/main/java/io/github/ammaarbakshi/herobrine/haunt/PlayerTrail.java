package io.github.ammaarbakshi.herobrine.haunt;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Short-term memory of one player: the last 10 seconds of movement (for the ghost replay) and
 * places they recently were (caves, mining spots, their path). Not persisted: it's only about
 * the last few minutes, and starts fresh after a relog or respawn.
 *
 * <p>Movement is kept in primitive ring buffers so recording costs no allocation.
 */
public final class PlayerTrail {
	/** Ticks between movement samples. */
	public static final int SAMPLE_INTERVAL = 2;
	/** 10 seconds of movement. */
	public static final int REPLAY_SAMPLES = 10 * 20 / SAMPLE_INTERVAL;
	private static final int MAX_PLACES = 16;
	private static final double PLACE_SPACING_SQR = 8 * 8;

	/** One recorded instant of movement. */
	public record Sample(double x, double y, double z, float yaw, float pitch, boolean crouching) {
	}

	private final double[] xs = new double[REPLAY_SAMPLES];
	private final double[] ys = new double[REPLAY_SAMPLES];
	private final double[] zs = new double[REPLAY_SAMPLES];
	private final float[] yaws = new float[REPLAY_SAMPLES];
	private final float[] pitches = new float[REPLAY_SAMPLES];
	private final boolean[] crouch = new boolean[REPLAY_SAMPLES];
	private int head;
	private int size;

	private final Deque<BlockPos> caves = new ArrayDeque<>();
	private final Deque<BlockPos> mined = new ArrayDeque<>();
	private final Deque<BlockPos> path = new ArrayDeque<>();

	private @Nullable ResourceKey<Level> dimension;

	/** Server tick at which the haunt director may next consider an event for this player. */
	public long nextEventTick = -1;

	/** Positions only make sense in one dimension: forget everything when the player changes it. */
	public void track(ServerPlayer player) {
		ResourceKey<Level> current = player.level().dimension();
		if (current != dimension) {
			dimension = current;
			head = 0;
			size = 0;
			caves.clear();
			mined.clear();
			path.clear();
		}
	}

	public void recordMovement(ServerPlayer player) {
		xs[head] = player.getX();
		ys[head] = player.getY();
		zs[head] = player.getZ();
		yaws[head] = player.getYRot();
		pitches[head] = player.getXRot();
		crouch[head] = player.isCrouching();
		head = (head + 1) % REPLAY_SAMPLES;
		if (size < REPLAY_SAMPLES) {
			size++;
		}
	}

	public boolean hasFullReplay() {
		return size == REPLAY_SAMPLES;
	}

	/** Recorded movement, oldest first. */
	public Sample[] replay() {
		Sample[] out = new Sample[size];
		int start = (head - size + REPLAY_SAMPLES) % REPLAY_SAMPLES;
		for (int i = 0; i < size; i++) {
			int j = (start + i) % REPLAY_SAMPLES;
			out[i] = new Sample(xs[j], ys[j], zs[j], yaws[j], pitches[j], crouch[j]);
		}
		return out;
	}

	public void rememberCave(BlockPos pos) {
		remember(caves, pos);
	}

	public void rememberMined(BlockPos pos) {
		remember(mined, pos);
	}

	public void rememberPath(BlockPos pos) {
		remember(path, pos);
	}

	/** Most recent first. */
	public List<BlockPos> caves() {
		return List.copyOf(caves);
	}

	public List<BlockPos> mined() {
		return List.copyOf(mined);
	}

	public List<BlockPos> path() {
		return List.copyOf(path);
	}

	private static void remember(Deque<BlockPos> places, BlockPos pos) {
		BlockPos last = places.peekFirst();
		if (last != null && last.distSqr(pos) < PLACE_SPACING_SQR) {
			return;
		}
		places.addFirst(pos.immutable());
		if (places.size() > MAX_PLACES) {
			places.removeLast();
		}
	}
}

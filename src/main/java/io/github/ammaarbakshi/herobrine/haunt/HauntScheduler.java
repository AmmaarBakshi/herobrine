package io.github.ammaarbakshi.herobrine.haunt;

import java.util.PriorityQueue;

import net.minecraft.server.MinecraftServer;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import io.github.ammaarbakshi.herobrine.perf.Perf;

/**
 * Runs small delayed actions on the server thread (a footstep every few ticks, a door that
 * closes a second after it opened). One priority queue, so an idle server pays one peek per tick.
 */
public final class HauntScheduler {
	private record Task(long tick, long order, Runnable action) {
	}

	private static final PriorityQueue<Task> QUEUE = new PriorityQueue<>((a, b) ->
			a.tick != b.tick ? Long.compare(a.tick, b.tick) : Long.compare(a.order, b.order));
	private static long now;
	private static long counter;

	private HauntScheduler() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(Perf.timed(Perf.WORLD, HauntScheduler::tick));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> QUEUE.clear());
	}

	public static void schedule(int delayTicks, Runnable action) {
		QUEUE.add(new Task(now + Math.max(1, delayTicks), counter++, action));
	}

	private static void tick(MinecraftServer server) {
		now++;
		while (!QUEUE.isEmpty() && QUEUE.peek().tick <= now) {
			QUEUE.poll().action.run();
		}
	}
}

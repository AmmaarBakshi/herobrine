package io.github.ammaarbakshi.herobrine.perf;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/**
 * The mod's cost meters, read by {@code /herobrine perf}:
 * <ul>
 *   <li>{@link #RECORDER}: the behaviour recorder, per online player per tick;</li>
 *   <li>{@link #WORLD}: the haunt director, scheduler, sightings, shrine and confrontations,
 *       per server tick;</li>
 *   <li>{@link #MIRROR_AI}: one boss's AI and movement, per tick he is fighting;</li>
 *   <li>{@link #OBSERVER}: the Living Observer (voice, appearances, observations), per server tick.</li>
 * </ul>
 */
public final class Perf {
	public static final PerfMeter RECORDER = new PerfMeter();
	public static final PerfMeter WORLD = new PerfMeter();
	public static final PerfMeter MIRROR_AI = new PerfMeter();
	public static final PerfMeter OBSERVER = new PerfMeter();

	private Perf() {
	}

	/** Closes each server tick's {@link #WORLD} and {@link #OBSERVER} units before the next tick's handlers run. */
	public static void init() {
		ServerTickEvents.START_SERVER_TICK.register(server -> {
			WORLD.endUnit();
			OBSERVER.endUnit();
		});
	}

	/** Wraps a server tick handler so its time is charged to {@code meter}. */
	public static ServerTickEvents.EndTick timed(PerfMeter meter, ServerTickEvents.EndTick handler) {
		return server -> {
			long start = System.nanoTime();
			try {
				handler.onEndTick(server);
			} finally {
				meter.charge(System.nanoTime() - start);
			}
		};
	}

	public static void resetAll() {
		RECORDER.reset();
		WORLD.reset();
		MIRROR_AI.reset();
		OBSERVER.reset();
	}
}

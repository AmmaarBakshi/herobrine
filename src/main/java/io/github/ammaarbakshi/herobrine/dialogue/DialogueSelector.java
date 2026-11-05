package io.github.ammaarbakshi.herobrine.dialogue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.random.RandomGenerator;

import org.jspecify.annotations.Nullable;

/**
 * Picks what he says. Pure logic, so every rule is unit tested:
 * <ol>
 *   <li>lines are trimmed to those whose conditions fit (tags, memory ranges), that are off
 *       their own cooldown, and, for {@code once} lines, never said;</li>
 *   <li>each survivor's own {@code chance} is rolled;</li>
 *   <li>anything among the last {@link io.github.ammaarbakshi.herobrine.memory.ObserverMemory#RECENT_LINES}
 *       lines is dropped: silence is better than repeating himself;</li>
 *   <li>only the highest priority survivors stay, and one is drawn by weight.</li>
 * </ol>
 */
public final class DialogueSelector {
	private DialogueSelector() {
	}

	/** One line out of {@code lines}, or empty if none may be said right now. */
	public static Optional<DialogueLine> pick(List<DialogueLine> lines, Set<String> tags, DialogueLine.MemoryLookup memory,
			DialogueHistory history, long now, RandomGenerator random) {
		List<DialogueLine> pool = new ArrayList<>();
		for (DialogueLine line : lines) {
			if (available(line, history, now) && line.fits(tags, memory)) {
				pool.add(line);
			}
		}
		pool.removeIf(line -> line.chance() < 1.0 && random.nextDouble() >= line.chance());
		pool.removeIf(line -> history.recentlySaid(line.id()));
		if (pool.isEmpty()) {
			return Optional.empty();
		}
		int top = Integer.MIN_VALUE;
		for (DialogueLine line : pool) {
			top = Math.max(top, line.priority());
		}
		int priority = top;
		pool.removeIf(line -> line.priority() != priority);
		return Optional.ofNullable(weighted(pool, random));
	}

	/** Not used up and off cooldown; says nothing about whether it fits the moment. */
	public static boolean available(DialogueLine line, DialogueHistory history, long now) {
		if (line.weight() <= 0) {
			return false;
		}
		long last = history.lastSaid(line.id());
		if (last < 0) {
			return true;
		}
		return !line.once() && now - last >= line.cooldownSeconds() * 20L;
	}

	private static @Nullable DialogueLine weighted(List<DialogueLine> pool, RandomGenerator random) {
		int total = 0;
		for (DialogueLine line : pool) {
			total += line.weight();
		}
		if (total <= 0) {
			return null;
		}
		int roll = random.nextInt(total);
		for (DialogueLine line : pool) {
			roll -= line.weight();
			if (roll < 0) {
				return line;
			}
		}
		return pool.getLast();
	}
}

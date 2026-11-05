package io.github.ammaarbakshi.herobrine.dialogue;

import java.util.List;

/**
 * How he reacts to one kind of moment, from {@code data/herobrine/dialogue/triggers.json}.
 *
 * @param id              trigger name used by the code ({@code encounter}, {@code progression}...)
 * @param categories      line categories to draw from, tried in order: put rare categories first
 *                        (each has its own chance and cooldown, see {@link CategoryRule})
 * @param chance          chance (0-1) that he says anything at all when this happens
 * @param cooldownSeconds game time before this trigger can make him speak again
 * @param minDelaySeconds shortest pause between the moment and the line
 * @param maxDelaySeconds longest pause
 * @param patienceSeconds how long a line may wait for a quiet moment (no other line just said,
 *                        not mid-fight) before it is dropped
 * @param appearChance    chance that he also shows himself somewhere nearby while speaking, if he
 *                        hasn't appeared recently
 * @param inCombat        may speak while the player is fighting
 */
public record TriggerRule(
		String id,
		List<String> categories,
		double chance,
		int cooldownSeconds,
		double minDelaySeconds,
		double maxDelaySeconds,
		int patienceSeconds,
		double appearChance,
		boolean inCombat) {

	public TriggerRule {
		categories = List.copyOf(categories);
		if (maxDelaySeconds < minDelaySeconds) {
			maxDelaySeconds = minDelaySeconds;
		}
	}

	public int minDelayTicks() {
		return (int) Math.round(minDelaySeconds * 20.0);
	}

	public int maxDelayTicks() {
		return (int) Math.round(maxDelaySeconds * 20.0);
	}

	/** A rule for a trigger that triggers.json doesn't mention: always speak, from one category. */
	public static TriggerRule fallback(String id) {
		return new TriggerRule(id, List.of(id), 1.0, 0, 1.0, 3.0, 30, 0.0, false);
	}
}

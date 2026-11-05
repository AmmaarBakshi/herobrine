package io.github.ammaarbakshi.herobrine.dialogue;

/**
 * Limits on a whole category of lines, from the {@code categories} section of triggers.json.
 * This is what keeps the rare categories rare: the fourth-wall lines, for example, have a small
 * chance and a cooldown of hours, whichever trigger offers them.
 *
 * @param chance          chance (0-1) the category is used when a trigger offers it
 * @param cooldownSeconds game time after any line of this category before another may be said
 */
public record CategoryRule(String name, double chance, int cooldownSeconds) {
	public static CategoryRule open(String name) {
		return new CategoryRule(name, 1.0, 0);
	}
}

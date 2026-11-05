package io.github.ammaarbakshi.herobrine.dialogue;

import java.util.Optional;
import java.util.Set;
import java.util.random.RandomGenerator;

import org.jspecify.annotations.Nullable;

/**
 * Decides, for one trigger, whether he speaks and which line. Two steps, because a line may have
 * to wait for a quiet moment:
 * <ol>
 *   <li>{@link #accepts}: rolled once, the moment it happens: the trigger's chance and cooldown;</li>
 *   <li>{@link #choose}: when the line is due: each offered category in order, with that category's
 *       own chance and cooldown, then {@link DialogueSelector} inside it.</li>
 * </ol>
 * {@code forced} (debug commands) skips every chance and cooldown, but a line must still fit.
 */
public final class DialoguePlanner {
	/** The chosen line (or null) and why, for {@code /herobrine voice} feedback. */
	public record Plan(@Nullable DialogueLine line, String reason) {
		public Optional<DialogueLine> optional() {
			return Optional.ofNullable(line);
		}
	}

	private DialoguePlanner() {
	}

	public static boolean accepts(TriggerRule rule, DialogueHistory history, long now, RandomGenerator random, boolean forced) {
		if (forced) {
			return true;
		}
		long last = history.triggerLastFired(rule.id());
		if (last >= 0 && now - last < rule.cooldownSeconds() * 20L) {
			return false;
		}
		return rule.chance() >= 1.0 || random.nextDouble() < rule.chance();
	}

	public static Plan choose(DialogueCatalog catalog, TriggerRule rule, Set<String> tags, DialogueLine.MemoryLookup memory,
			DialogueHistory history, long now, RandomGenerator random, boolean forced) {
		String reason = "no line fits";
		for (String category : rule.categories()) {
			CategoryRule limits = catalog.categoryRule(category);
			if (!forced) {
				long last = history.categoryLastSaid(category);
				if (last >= 0 && now - last < limits.cooldownSeconds() * 20L) {
					reason = category + " is on cooldown";
					continue;
				}
				if (limits.chance() < 1.0 && random.nextDouble() >= limits.chance()) {
					reason = category + " not chosen this time";
					continue;
				}
			}
			Optional<DialogueLine> line = DialogueSelector.pick(catalog.category(category), tags, memory, history, now, random);
			if (line.isPresent()) {
				return new Plan(line.get(), category);
			}
		}
		return new Plan(null, reason);
	}
}

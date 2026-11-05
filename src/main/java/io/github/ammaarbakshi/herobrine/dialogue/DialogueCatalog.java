package io.github.ammaarbakshi.herobrine.dialogue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Every line he can say and the rules for when he says them, loaded from the data pack (see
 * {@link DialogueParser}). Immutable: a reload builds a new catalog and swaps it in.
 */
public final class DialogueCatalog {
	public static final DialogueCatalog EMPTY = new DialogueCatalog(List.of(), Map.of(), Map.of());

	private final Map<String, DialogueLine> lines = new LinkedHashMap<>();
	private final Map<String, List<DialogueLine>> byCategory = new LinkedHashMap<>();
	private final Map<String, TriggerRule> triggers;
	private final Map<String, CategoryRule> categories;

	public DialogueCatalog(Collection<DialogueLine> lines, Map<String, TriggerRule> triggers, Map<String, CategoryRule> categories) {
		for (DialogueLine line : lines) {
			this.lines.put(line.id(), line);
			byCategory.computeIfAbsent(line.category(), c -> new ArrayList<>()).add(line);
		}
		byCategory.replaceAll((category, list) -> List.copyOf(list));
		this.triggers = Collections.unmodifiableMap(new LinkedHashMap<>(triggers));
		this.categories = Collections.unmodifiableMap(new LinkedHashMap<>(categories));
	}

	public Optional<DialogueLine> line(String id) {
		return Optional.ofNullable(lines.get(id));
	}

	/** Every line, in file order. */
	public Collection<DialogueLine> lines() {
		return Collections.unmodifiableCollection(lines.values());
	}

	public int size() {
		return lines.size();
	}

	public List<DialogueLine> category(String category) {
		return byCategory.getOrDefault(category, List.of());
	}

	/** Categories that have at least one line. */
	public Set<String> categoryNames() {
		return Collections.unmodifiableSet(byCategory.keySet());
	}

	public boolean hasTrigger(String id) {
		return triggers.containsKey(id);
	}

	/** The rule for {@code id}, or a permissive fallback if triggers.json doesn't define it. */
	public TriggerRule trigger(String id) {
		TriggerRule rule = triggers.get(id);
		return rule != null ? rule : TriggerRule.fallback(id);
	}

	public Map<String, TriggerRule> triggers() {
		return triggers;
	}

	public CategoryRule categoryRule(String category) {
		CategoryRule rule = categories.get(category);
		return rule != null ? rule : CategoryRule.open(category);
	}

	public Map<String, CategoryRule> categoryRules() {
		return categories;
	}
}

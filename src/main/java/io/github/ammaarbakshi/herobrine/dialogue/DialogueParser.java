package io.github.ammaarbakshi.herobrine.dialogue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * Reads dialogue.json and triggers.json. Every data pack may ship both files; they stack like
 * tags: later packs add lines and override rules with the same id, and a file with
 * {@code "replace": true} discards everything below it.
 *
 * <p>Mistakes never stop the game: a broken entry is skipped with a warning naming the file and
 * the line, and everything else still loads.
 */
public final class DialogueParser {
	private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+");
	private static final Pattern AUDIO = Pattern.compile("([a-z0-9_.-]+:)?[a-z0-9_./-]+");

	private DialogueParser() {
	}

	/** Collects files in load order (lowest priority pack first) into one catalog. */
	public static final class Builder {
		private final Map<String, DialogueLine> lines = new LinkedHashMap<>();
		private final Map<String, TriggerRule> triggers = new LinkedHashMap<>();
		private final Map<String, CategoryRule> categories = new LinkedHashMap<>();
		private final Consumer<String> warnings;

		public Builder(Consumer<String> warnings) {
			this.warnings = warnings;
		}

		/** One dialogue.json. */
		public Builder addLines(String source, JsonElement json) {
			if (!json.isJsonObject()) {
				warnings.accept(source + ": expected an object with a \"lines\" list");
				return this;
			}
			JsonObject root = json.getAsJsonObject();
			if (bool(root, "replace", false)) {
				lines.clear();
			}
			JsonElement list = root.get("lines");
			if (list == null || !list.isJsonArray()) {
				warnings.accept(source + ": missing \"lines\" list");
				return this;
			}
			int index = 0;
			for (JsonElement element : list.getAsJsonArray()) {
				index++;
				try {
					if (!element.isJsonObject()) {
						throw new IllegalArgumentException("not an object");
					}
					DialogueLine line = parseLine(element.getAsJsonObject());
					if (lines.containsKey(line.id())) {
						lines.remove(line.id()); // re-insert so an override keeps the newer pack's order
					}
					lines.put(line.id(), line);
				} catch (RuntimeException e) {
					warnings.accept(source + ": line " + index + describe(element) + " skipped: " + e.getMessage());
				}
			}
			return this;
		}

		/** One triggers.json. */
		public Builder addTriggers(String source, JsonElement json) {
			if (!json.isJsonObject()) {
				warnings.accept(source + ": expected an object");
				return this;
			}
			JsonObject root = json.getAsJsonObject();
			if (bool(root, "replace", false)) {
				triggers.clear();
				categories.clear();
			}
			JsonObject triggerObject = object(root, "triggers");
			if (triggerObject != null) {
				for (Map.Entry<String, JsonElement> entry : triggerObject.entrySet()) {
					try {
						triggers.put(entry.getKey(), parseTrigger(entry.getKey(), asObject(entry.getValue())));
					} catch (RuntimeException e) {
						warnings.accept(source + ": trigger \"" + entry.getKey() + "\" skipped: " + e.getMessage());
					}
				}
			}
			JsonObject categoryObject = object(root, "categories");
			if (categoryObject != null) {
				for (Map.Entry<String, JsonElement> entry : categoryObject.entrySet()) {
					try {
						JsonObject o = asObject(entry.getValue());
						categories.put(entry.getKey(), new CategoryRule(entry.getKey(),
								unit(number(o, "chance", 1.0)), Math.max(0, (int) number(o, "cooldown", 0))));
					} catch (RuntimeException e) {
						warnings.accept(source + ": category \"" + entry.getKey() + "\" skipped: " + e.getMessage());
					}
				}
			}
			return this;
		}

		/** Builds the catalog and reports rules and lines that can never meet. */
		public DialogueCatalog build() {
			Set<String> withLines = new HashSet<>();
			lines.values().forEach(line -> withLines.add(line.category()));
			Set<String> offered = new HashSet<>();
			for (TriggerRule rule : triggers.values()) {
				for (String category : rule.categories()) {
					offered.add(category);
					if (!withLines.contains(category)) {
						warnings.accept("Trigger \"" + rule.id() + "\" offers category \"" + category + "\", which has no lines");
					}
				}
			}
			if (!triggers.isEmpty()) {
				for (String category : withLines) {
					if (!offered.contains(category)) {
						warnings.accept("No trigger offers category \"" + category + "\": its lines can only be played by command");
					}
				}
			}
			return new DialogueCatalog(new ArrayList<>(lines.values()), triggers, categories);
		}
	}

	// --- Entries ------------------------------------------------------------------------------

	static DialogueLine parseLine(JsonObject o) {
		String id = string(o, "id", "");
		if (!ID.matcher(id).matches()) {
			throw new IllegalArgumentException("\"id\" must be lowercase letters, digits, '_', '.' or '-', was \"" + id + "\"");
		}
		int dot = id.indexOf('.');
		String category = string(o, "category", dot > 0 ? id.substring(0, dot) : "");
		if (!ID.matcher(category).matches()) {
			throw new IllegalArgumentException("missing or invalid \"category\"");
		}
		String text = string(o, "text", "");
		if (text.isBlank()) {
			throw new IllegalArgumentException("missing \"text\"");
		}
		String audio = string(o, "audio", DialogueLine.defaultAudio(id, category));
		if (!AUDIO.matcher(audio).matches()) {
			throw new IllegalArgumentException("\"audio\" must look like voice/random/hello (lowercase, no .ogg), was \"" + audio + "\"");
		}
		if (audio.indexOf(':') < 0) {
			audio = DialogueLine.DEFAULT_NAMESPACE + ":" + audio;
		}
		int weight = (int) number(o, "weight", DialogueLine.DEFAULT_WEIGHT);
		if (weight < 0) {
			throw new IllegalArgumentException("\"weight\" can't be negative");
		}

		Set<String> tags = Set.of();
		Set<String> notTags = Set.of();
		Map<String, IntRange> memory = Map.of();
		JsonObject when = object(o, "when");
		if (when != null) {
			tags = strings(when, "tags");
			notTags = strings(when, "not_tags");
			memory = ranges(when, "memory");
		}
		return new DialogueLine(id, category, audio, text, string(o, "note", ""),
				(int) number(o, "priority", 0), weight, unit(number(o, "chance", 1.0)),
				Math.max(0, (int) number(o, "cooldown", 0)), bool(o, "once", false), tags, notTags, memory);
	}

	static TriggerRule parseTrigger(String id, JsonObject o) {
		List<String> categories = new ArrayList<>(strings(o, "categories"));
		if (categories.isEmpty()) {
			categories.add(id);
		}
		double minDelay = 1.0;
		double maxDelay = 3.0;
		JsonElement delay = o.get("delay");
		if (delay != null) {
			if (delay.isJsonArray() && delay.getAsJsonArray().size() == 2) {
				minDelay = delay.getAsJsonArray().get(0).getAsDouble();
				maxDelay = delay.getAsJsonArray().get(1).getAsDouble();
			} else if (delay.isJsonPrimitive()) {
				minDelay = maxDelay = delay.getAsDouble();
			} else {
				throw new IllegalArgumentException("\"delay\" must be seconds or [min, max]");
			}
		}
		return new TriggerRule(id, categories, unit(number(o, "chance", 1.0)), Math.max(0, (int) number(o, "cooldown", 0)),
				Math.max(0.0, minDelay), Math.max(0.0, maxDelay), Math.max(0, (int) number(o, "patience", 30)),
				unit(number(o, "appear", 0.0)), bool(o, "in_combat", false));
	}

	// --- JSON helpers -------------------------------------------------------------------------

	private static String describe(JsonElement element) {
		if (element.isJsonObject() && element.getAsJsonObject().get("id") instanceof JsonPrimitive id) {
			return " (\"" + id.getAsString() + "\")";
		}
		return "";
	}

	private static JsonObject asObject(JsonElement element) {
		if (!element.isJsonObject()) {
			throw new IllegalArgumentException("expected an object");
		}
		return element.getAsJsonObject();
	}

	private static @Nullable JsonObject object(JsonObject o, String key) {
		JsonElement element = o.get(key);
		if (element == null || element.isJsonNull()) {
			return null;
		}
		return asObject(element);
	}

	private static String string(JsonObject o, String key, String fallback) {
		JsonElement element = o.get(key);
		if (element == null || element.isJsonNull()) {
			return fallback;
		}
		if (!element.isJsonPrimitive()) {
			throw new IllegalArgumentException("\"" + key + "\" must be text");
		}
		return element.getAsString();
	}

	private static double number(JsonObject o, String key, double fallback) {
		JsonElement element = o.get(key);
		if (element == null || element.isJsonNull()) {
			return fallback;
		}
		if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
			throw new IllegalArgumentException("\"" + key + "\" must be a number");
		}
		return element.getAsDouble();
	}

	private static boolean bool(JsonObject o, String key, boolean fallback) {
		JsonElement element = o.get(key);
		if (element == null || element.isJsonNull()) {
			return fallback;
		}
		if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
			throw new IllegalArgumentException("\"" + key + "\" must be true or false");
		}
		return element.getAsBoolean();
	}

	private static Set<String> strings(JsonObject o, String key) {
		JsonElement element = o.get(key);
		if (element == null || element.isJsonNull()) {
			return Set.of();
		}
		if (!element.isJsonArray()) {
			throw new IllegalArgumentException("\"" + key + "\" must be a list");
		}
		Set<String> out = new LinkedHashSet<>();
		for (JsonElement item : (JsonArray) element) {
			out.add(item.getAsString());
		}
		return out;
	}

	private static Map<String, IntRange> ranges(JsonObject o, String key) {
		JsonObject object = object(o, key);
		if (object == null) {
			return Map.of();
		}
		Map<String, IntRange> out = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
			JsonElement value = entry.getValue();
			if (!value.isJsonPrimitive()) {
				throw new IllegalArgumentException("memory \"" + entry.getKey() + "\" must be a number or a range like \"5..\"");
			}
			out.put(entry.getKey(), IntRange.parse(value.getAsString()));
		}
		return out;
	}

	private static double unit(double value) {
		return Double.isNaN(value) ? 0.0 : Math.max(0.0, Math.min(1.0, value));
	}
}

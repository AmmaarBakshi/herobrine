package io.github.ammaarbakshi.herobrine.dialogue;

import java.util.Map;
import java.util.Set;

/**
 * One recorded line, as written in {@code data/herobrine/dialogue/dialogue.json}.
 *
 * @param id              unique, stable id, e.g. {@code progression.iron}; also the lang key suffix
 *                        ({@code herobrine.voice.<id>}) for translated captions
 * @param category        which set of lines it belongs to; also its audio folder
 * @param audio           sound file path inside {@code assets/<namespace>/sounds/}, without
 *                        {@code .ogg}, e.g. {@code herobrine:voice/progression/iron}
 * @param text            exactly what is said: shown as the caption, and in place of the voice if
 *                        the recording is missing
 * @param note            delivery note for the voice actor; not used in game
 * @param priority        among the lines that fit, only the highest priority ones are considered
 * @param weight          relative chance among lines of the same priority
 * @param chance          chance (0-1) that the line is considered at all when it fits
 * @param cooldownSeconds game time before this line may be said again
 * @param once            said at most once per player, ever
 * @param tags            context tags that must all be present (e.g. {@code tier:iron})
 * @param notTags         context tags that must all be absent
 * @param memory          ranges the player's memory counters must fall in (e.g. deaths 5..)
 */
public record DialogueLine(
		String id,
		String category,
		String audio,
		String text,
		String note,
		int priority,
		int weight,
		double chance,
		int cooldownSeconds,
		boolean once,
		Set<String> tags,
		Set<String> notTags,
		Map<String, IntRange> memory) {

	public static final int DEFAULT_WEIGHT = 10;
	public static final String DEFAULT_NAMESPACE = "herobrine";

	public DialogueLine {
		tags = Set.copyOf(tags);
		notTags = Set.copyOf(notTags);
		memory = Map.copyOf(memory);
	}

	/**
	 * The audio path used when a line doesn't name one: {@code herobrine:voice/<category>/<name>},
	 * where name is the id without its {@code <category>.} prefix. So {@code death.again} in
	 * category {@code death} is {@code assets/herobrine/sounds/voice/death/again.ogg}.
	 */
	public static String defaultAudio(String id, String category) {
		String name = id.startsWith(category + ".") ? id.substring(category.length() + 1) : id;
		return DEFAULT_NAMESPACE + ":voice/" + category + "/" + name.replace('.', '_');
	}

	/** The namespace of {@link #audio}. */
	public String audioNamespace() {
		int colon = audio.indexOf(':');
		return colon < 0 ? DEFAULT_NAMESPACE : audio.substring(0, colon);
	}

	/** The path of {@link #audio} inside {@code assets/<namespace>/sounds/}, without {@code .ogg}. */
	public String audioPath() {
		int colon = audio.indexOf(':');
		return colon < 0 ? audio : audio.substring(colon + 1);
	}

	/** Where the recording lives in the mod jar or a resource pack. */
	public String audioFile() {
		return "assets/" + audioNamespace() + "/sounds/" + audioPath() + ".ogg";
	}

	/** Lang key for a translated caption; the English {@link #text} is the fallback. */
	public String translationKey() {
		return "herobrine.voice." + id;
	}

	/** True if the line's own conditions hold for this context. */
	public boolean fits(Set<String> contextTags, MemoryLookup counters) {
		if (!contextTags.containsAll(tags)) {
			return false;
		}
		for (String tag : notTags) {
			if (contextTags.contains(tag)) {
				return false;
			}
		}
		for (Map.Entry<String, IntRange> condition : memory.entrySet()) {
			if (!condition.getValue().test(counters.get(condition.getKey()))) {
				return false;
			}
		}
		return true;
	}

	/** Reads a memory counter by name; 0 for unknown names. */
	@FunctionalInterface
	public interface MemoryLookup {
		int get(String counter);
	}
}

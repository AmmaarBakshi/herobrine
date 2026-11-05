package io.github.ammaarbakshi.herobrine.memory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.ammaarbakshi.herobrine.dialogue.DialogueHistory;

/**
 * Everything Herobrine remembers about one player's journey: deaths, sightings, how they react to
 * him, bosses, gear, milestones, where he last stood, important events, and every line he has said
 * and when. Persisted with the player (survives relogs, deaths and restarts).
 *
 * <p>Numbers are kept by stable names ({@link Counters}) instead of fields: dialogue.json's memory
 * conditions use the same names, and a new counter never breaks an old save.
 *
 * <p>Mutable and server-thread only. It only changes when something happens (an appearance, a
 * death, a line), never per tick. Plain Java plus DFU codecs, so it is unit tested directly.
 */
public final class ObserverMemory implements DialogueHistory {
	/** Lines that count as "just said" and are avoided while anything else fits. */
	public static final int RECENT_LINES = 8;
	public static final int MAX_ENCOUNTER_TIMES = 8;
	public static final int MAX_EVENTS = 32;

	public static final Codec<ObserverMemory> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.unboundedMap(Codec.STRING, Codec.INT).lenientOptionalFieldOf("counters", Map.of()).forGetter(m -> m.counters),
			Codec.STRING.listOf().lenientOptionalFieldOf("milestones", List.of()).forGetter(m -> List.copyOf(m.milestones)),
			Codec.unboundedMap(Codec.STRING, Codec.LONG).lenientOptionalFieldOf("lines", Map.of()).forGetter(m -> m.lineTimes),
			Codec.unboundedMap(Codec.STRING, Codec.LONG).lenientOptionalFieldOf("categories", Map.of()).forGetter(m -> m.categoryTimes),
			Codec.unboundedMap(Codec.STRING, Codec.LONG).lenientOptionalFieldOf("triggers", Map.of()).forGetter(m -> m.triggerTimes),
			Codec.STRING.listOf().lenientOptionalFieldOf("recent", List.of()).forGetter(m -> List.copyOf(m.recentLines)),
			Codec.LONG.listOf().lenientOptionalFieldOf("encounterTimes", List.of()).forGetter(m -> List.copyOf(m.encounterTimes)),
			Place.CODEC.lenientOptionalFieldOf("lastAppearance").forGetter(m -> Optional.ofNullable(m.lastAppearance)),
			Place.CODEC.lenientOptionalFieldOf("home").forGetter(m -> Optional.ofNullable(m.home)),
			WorldEvent.CODEC.listOf().lenientOptionalFieldOf("events", List.of()).forGetter(m -> List.copyOf(m.events)),
			Codec.LONG.lenientOptionalFieldOf("firstMet", -1L).forGetter(m -> m.firstMet),
			Codec.LONG.lenientOptionalFieldOf("lastSpoke", -1L).forGetter(m -> m.lastSpoke),
			Codec.BOOL.lenientOptionalFieldOf("baselined", false).forGetter(m -> m.baselined)
	).apply(i, ObserverMemory::new));

	private final Map<String, Integer> counters = new HashMap<>();
	private final Set<String> milestones = new LinkedHashSet<>();
	private final Map<String, Long> lineTimes = new HashMap<>();
	private final Map<String, Long> categoryTimes = new HashMap<>();
	private final Map<String, Long> triggerTimes = new HashMap<>();
	private final Deque<String> recentLines = new ArrayDeque<>();
	private final Deque<Long> encounterTimes = new ArrayDeque<>();
	private final Deque<WorldEvent> events = new ArrayDeque<>();
	private @Nullable Place lastAppearance;
	private @Nullable Place home;
	private long firstMet = -1;
	private long lastSpoke = -1;
	private boolean baselined;

	public ObserverMemory() {
	}

	private ObserverMemory(Map<String, Integer> counters, List<String> milestones, Map<String, Long> lines,
			Map<String, Long> categories, Map<String, Long> triggers, List<String> recent, List<Long> encounterTimes,
			Optional<Place> lastAppearance, Optional<Place> home, List<WorldEvent> events,
			long firstMet, long lastSpoke, boolean baselined) {
		counters.forEach((key, value) -> this.counters.put(Counters.normalize(key), Math.max(0, value)));
		this.milestones.addAll(milestones);
		this.lineTimes.putAll(lines);
		this.categoryTimes.putAll(categories);
		this.triggerTimes.putAll(triggers);
		recent.stream().skip(Math.max(0, recent.size() - RECENT_LINES)).forEach(this.recentLines::addLast);
		encounterTimes.stream().skip(Math.max(0, encounterTimes.size() - MAX_ENCOUNTER_TIMES)).forEach(this.encounterTimes::addLast);
		this.lastAppearance = lastAppearance.orElse(null);
		this.home = home.orElse(null);
		events.stream().skip(Math.max(0, events.size() - MAX_EVENTS)).forEach(this.events::addLast);
		this.firstMet = firstMet;
		this.lastSpoke = lastSpoke;
		this.baselined = baselined;
	}

	// --- Counters -----------------------------------------------------------------------------

	public int counter(String key) {
		return counters.getOrDefault(Counters.normalize(key), 0);
	}

	public void increment(String key) {
		add(key, 1);
	}

	public void add(String key, int amount) {
		counters.merge(Counters.normalize(key), amount, (a, b) -> Math.max(0, a + b));
	}

	public void set(String key, int value) {
		counters.put(Counters.normalize(key), Math.max(0, value));
	}

	/** Every stored counter, sorted by name. */
	public Map<String, Integer> counters() {
		return Collections.unmodifiableMap(new TreeMap<>(counters));
	}

	// --- Milestones ---------------------------------------------------------------------------

	public boolean hasMilestone(Milestone milestone) {
		return milestones.contains(milestone.id());
	}

	/** Returns true the first time, false if it was already known. */
	public boolean addMilestone(Milestone milestone) {
		return milestones.add(milestone.id());
	}

	public Set<String> milestones() {
		return Collections.unmodifiableSet(milestones);
	}

	// --- Appearances and encounters -----------------------------------------------------------

	/** He showed himself (whether or not they noticed). */
	public void recordAppearance(Place place) {
		increment(Counters.APPEARANCES);
		lastAppearance = place;
	}

	public @Nullable Place lastAppearance() {
		return lastAppearance;
	}

	public long lastAppearanceTime() {
		return lastAppearance == null ? -1 : lastAppearance.time();
	}

	/** They saw him. */
	public void recordEncounter(long now) {
		increment(Counters.ENCOUNTERS);
		set(Counters.IGNORED_STREAK, 0);
		if (firstMet < 0) {
			firstMet = now;
		}
		encounterTimes.addLast(now);
		while (encounterTimes.size() > MAX_ENCOUNTER_TIMES) {
			encounterTimes.removeFirst();
		}
	}

	/** He appeared and left without being noticed. */
	public void recordIgnored() {
		increment(Counters.IGNORED);
		increment(Counters.IGNORED_STREAK);
	}

	public long firstMet() {
		return firstMet;
	}

	public long lastEncounter() {
		return encounterTimes.isEmpty() ? -1 : encounterTimes.getLast();
	}

	/** Oldest first. */
	public List<Long> encounterTimes() {
		return List.copyOf(encounterTimes);
	}

	// --- Places and events --------------------------------------------------------------------

	public @Nullable Place home() {
		return home;
	}

	public void setHome(@Nullable Place home) {
		this.home = home;
	}

	public void recordEvent(WorldEvent event) {
		events.addLast(event);
		while (events.size() > MAX_EVENTS) {
			events.removeFirst();
		}
	}

	/** Oldest first. */
	public List<WorldEvent> events() {
		return List.copyOf(events);
	}

	// --- Dialogue history ---------------------------------------------------------------------

	@Override
	public long lastSaid(String lineId) {
		return lineTimes.getOrDefault(lineId, -1L);
	}

	@Override
	public boolean recentlySaid(String lineId) {
		return recentLines.contains(lineId);
	}

	@Override
	public long categoryLastSaid(String category) {
		return categoryTimes.getOrDefault(category, -1L);
	}

	@Override
	public long triggerLastFired(String trigger) {
		return triggerTimes.getOrDefault(trigger, -1L);
	}

	@Override
	public long lastSpoke() {
		return lastSpoke;
	}

	public void recordTrigger(String trigger, long now) {
		triggerTimes.put(trigger, now);
	}

	public void recordLine(String lineId, String category, long now) {
		lineTimes.put(lineId, now);
		categoryTimes.put(category, now);
		recentLines.remove(lineId);
		recentLines.addLast(lineId);
		while (recentLines.size() > RECENT_LINES) {
			recentLines.removeFirst();
		}
		lastSpoke = now;
		increment(Counters.LINES);
	}

	/** Most recent last. */
	public List<String> recentLines() {
		return List.copyOf(recentLines);
	}

	public int linesKnown() {
		return lineTimes.size();
	}

	// --- Lifecycle ----------------------------------------------------------------------------

	/**
	 * False until the observer has looked at this player once. That first look records what they
	 * already have (gear, advancements) without comment, so installing the mod mid-world doesn't
	 * set off a speech about every diamond they found last year.
	 */
	public boolean baselined() {
		return baselined;
	}

	public void markBaselined() {
		baselined = true;
	}

	/** Forgets everything; for {@code /herobrine memory <player> reset}. */
	public void clear() {
		counters.clear();
		milestones.clear();
		lineTimes.clear();
		categoryTimes.clear();
		triggerTimes.clear();
		recentLines.clear();
		encounterTimes.clear();
		events.clear();
		lastAppearance = null;
		home = null;
		firstMet = -1;
		lastSpoke = -1;
		baselined = false;
	}

	/** A copy for tests and debugging. */
	public ObserverMemory copy() {
		return new ObserverMemory(counters, new ArrayList<>(milestones), lineTimes, categoryTimes, triggerTimes,
				new ArrayList<>(recentLines), new ArrayList<>(encounterTimes), Optional.ofNullable(lastAppearance),
				Optional.ofNullable(home), new ArrayList<>(events), firstMet, lastSpoke, baselined);
	}
}

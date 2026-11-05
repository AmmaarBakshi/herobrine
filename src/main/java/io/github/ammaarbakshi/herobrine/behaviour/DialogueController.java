package io.github.ammaarbakshi.herobrine.behaviour;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.random.RandomGenerator;

import org.jspecify.annotations.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import io.github.ammaarbakshi.herobrine.HerobrineMod;
import io.github.ammaarbakshi.herobrine.config.HerobrineConfig;
import io.github.ammaarbakshi.herobrine.dialogue.DialogueCatalog;
import io.github.ammaarbakshi.herobrine.dialogue.DialogueLine;
import io.github.ammaarbakshi.herobrine.dialogue.DialogueParser;
import io.github.ammaarbakshi.herobrine.dialogue.DialoguePlanner;
import io.github.ammaarbakshi.herobrine.dialogue.TriggerRule;
import io.github.ammaarbakshi.herobrine.encounter.ConfrontationManager;
import io.github.ammaarbakshi.herobrine.memory.ObserverMemory;
import io.github.ammaarbakshi.herobrine.net.VoiceLinePayload;
import io.github.ammaarbakshi.herobrine.registry.ModAttachments;
import io.github.ammaarbakshi.herobrine.sighting.SightingController;

/**
 * Herobrine's voice. Other systems report moments ({@link #trigger}); this decides whether he
 * answers, waits for a quiet moment, picks the line and sends it to that one player.
 *
 * <p>The lines live in {@code data/herobrine/dialogue/dialogue.json} and the rules in
 * {@code triggers.json}, reloaded with {@code /reload}. A trigger's chance and cooldown are rolled
 * the moment it happens; if he does want to speak, the line waits (up to the trigger's patience)
 * for the gap since his last line to pass and for any fight to end. Nothing here runs unless a
 * line is waiting.
 */
public final class DialogueController {
	public static final Identifier DIALOGUE_FILE = HerobrineMod.id("dialogue/dialogue.json");
	public static final Identifier TRIGGERS_FILE = HerobrineMod.id("dialogue/triggers.json");
	/** The category that is switched off by {@code fourthWall: false}. */
	public static final String FOURTH_WALL = "fourth_wall";
	private static final int MAX_WAITING = 4;
	/** Waiting lines are looked at every this many ticks: a quarter second is precise enough for speech. */
	private static final int CHECK_INTERVAL = 5;

	private record Waiting(String trigger, Set<String> tags, long due, long expires) {
	}

	private static volatile DialogueCatalog catalog = DialogueCatalog.EMPTY;
	private static final Map<UUID, List<Waiting>> WAITING = new HashMap<>();

	private DialogueController() {
	}

	public static void init() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> load(server.getResourceManager()));
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> {
			if (success) {
				load(resources);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> WAITING.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			WAITING.clear();
			catalog = DialogueCatalog.EMPTY;
		});
	}

	public static DialogueCatalog catalog() {
		return catalog;
	}

	// --- Loading ------------------------------------------------------------------------------

	/** Reads every pack's dialogue.json and triggers.json, lowest priority first. */
	public static DialogueCatalog load(ResourceManager resources) {
		List<String> warnings = new ArrayList<>();
		DialogueParser.Builder builder = new DialogueParser.Builder(warnings::add);
		for (Resource resource : resources.getResourceStack(DIALOGUE_FILE)) {
			read(resource, DIALOGUE_FILE, builder::addLines, warnings);
		}
		for (Resource resource : resources.getResourceStack(TRIGGERS_FILE)) {
			read(resource, TRIGGERS_FILE, builder::addTriggers, warnings);
		}
		DialogueCatalog loaded = builder.build();
		warnings.forEach(warning -> HerobrineMod.LOGGER.warn("Dialogue: {}", warning));
		catalog = loaded;
		HerobrineMod.LOGGER.info("Loaded {} voice lines in {} categories and {} triggers", loaded.size(),
				loaded.categoryNames().size(), loaded.triggers().size());
		return loaded;
	}

	private static void read(Resource resource, Identifier id, BiConsumer<String, JsonElement> sink, List<String> warnings) {
		String source = resource.sourcePackId() + ":" + id;
		try (Reader reader = resource.openAsReader()) {
			sink.accept(source, JsonParser.parseReader(reader));
		} catch (IOException | JsonParseException e) {
			warnings.add(source + ": " + e.getMessage());
		}
	}

	// --- Triggers -----------------------------------------------------------------------------

	public static boolean voiceEnabled() {
		return Observer.active() && HerobrineConfig.get().voice;
	}

	/** Something happened that he may comment on. Cheap: the trigger's chance is rolled right away. */
	public static void trigger(ServerPlayer player, String trigger, String... tags) {
		trigger(player, trigger, Set.of(tags));
	}

	public static void trigger(ServerPlayer player, String trigger, Set<String> tags) {
		if (!voiceEnabled() || player.isSpectator()) {
			return;
		}
		TriggerRule rule = catalog.trigger(trigger);
		ObserverMemory memory = ModAttachments.memory(player);
		long now = Observer.now(player);
		RandomGenerator random = Observer.random(player);
		if (!DialoguePlanner.accepts(rule, memory, now, random, false)) {
			return;
		}
		int spread = Math.max(0, rule.maxDelayTicks() - rule.minDelayTicks());
		long due = now + rule.minDelayTicks() + (spread == 0 ? 0 : random.nextInt(spread + 1));
		List<Waiting> waiting = WAITING.computeIfAbsent(player.getUUID(), id -> new ArrayList<>());
		// A newer moment of the same kind replaces the older one: iron then diamond says diamond.
		waiting.removeIf(w -> w.trigger().equals(rule.id()));
		if (waiting.size() >= MAX_WAITING) {
			waiting.removeFirst();
		}
		waiting.add(new Waiting(rule.id(), Set.copyOf(tags), due, due + rule.patienceSeconds() * 20L));
	}

	/**
	 * For commands: answers a trigger right now, ignoring chances, cooldowns and the gap, but still
	 * only with a line that fits. Returns what was decided.
	 */
	public static DialoguePlanner.Plan force(ServerPlayer player, String trigger, Set<String> tags) {
		TriggerRule rule = effectiveRule(catalog.trigger(trigger));
		ObserverMemory memory = ModAttachments.memory(player);
		long now = Observer.now(player);
		DialoguePlanner.Plan plan = DialoguePlanner.choose(catalog, rule, ObserverContext.tags(player, tags),
				ObserverContext.counters(player, memory, now), memory, now, Observer.random(player), true);
		if (plan.line() != null) {
			say(player, plan.line());
			memory.recordTrigger(rule.id(), now);
		}
		return plan;
	}

	/** Says one specific line now, whatever the rules say; for commands and tests. */
	public static void say(ServerPlayer player, DialogueLine line) {
		ObserverMemory memory = ModAttachments.memory(player);
		long now = Observer.now(player);
		int duration = durationTicks(line.text());
		if (ServerPlayNetworking.canSend(player, VoiceLinePayload.TYPE)) {
			ServerPlayNetworking.send(player, new VoiceLinePayload(line.id(), line.audio(), line.text(), duration));
		} else {
			// A client without the mod gets the words in chat; the fallbacks cover its missing lang file.
			player.sendSystemMessage(Component.translatable("chat.type.text",
					Component.translatableWithFallback("entity.herobrine.herobrine", "Herobrine"),
					Component.translatableWithFallback(line.translationKey(), line.text())));
		}
		memory.recordLine(line.id(), line.category(), now);
		// If he is standing in view, he stays until he has finished.
		SightingController.holdFor(player, duration);
		HerobrineMod.LOGGER.debug("Herobrine says {} to {}", line.id(), player.getName().getString());
	}

	/** Lines waiting for {@code player}, for {@code /herobrine voice}. */
	public static int waiting(ServerPlayer player) {
		List<Waiting> waiting = WAITING.get(player.getUUID());
		return waiting == null ? 0 : waiting.size();
	}

	// --- Ticking ------------------------------------------------------------------------------

	static void tick(MinecraftServer server) {
		if (WAITING.isEmpty() || server.getTickCount() % CHECK_INTERVAL != 0) {
			return;
		}
		long now = Observer.now(server);
		Iterator<Map.Entry<UUID, List<Waiting>>> players = WAITING.entrySet().iterator();
		while (players.hasNext()) {
			Map.Entry<UUID, List<Waiting>> entry = players.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			List<Waiting> waiting = entry.getValue();
			if (player == null || !voiceEnabled()) {
				players.remove();
				continue;
			}
			Iterator<Waiting> it = waiting.iterator();
			while (it.hasNext()) {
				Waiting w = it.next();
				if (now < w.due()) {
					continue;
				}
				TriggerRule rule = effectiveRule(catalog.trigger(w.trigger()));
				if (quiet(player, rule, now)) {
					it.remove();
					speak(player, rule, w.tags(), now);
					break; // one line at a time; the gap holds back the rest
				}
				if (now > w.expires()) {
					it.remove();
				}
			}
			if (waiting.isEmpty()) {
				players.remove();
			}
		}
	}

	/** Not mid-fight, alive, and long enough since his last line. */
	private static boolean quiet(ServerPlayer player, TriggerRule rule, long now) {
		if (!player.isAlive() || player.isSpectator() || ConfrontationManager.isFighting(player)) {
			return false;
		}
		if (!rule.inCombat() && ModAttachments.combat(player).inCombat()) {
			return false;
		}
		long last = ModAttachments.memory(player).lastSpoke();
		return last < 0 || now - last >= HerobrineConfig.get().voiceMinGapSeconds * 20L;
	}

	private static void speak(ServerPlayer player, TriggerRule rule, Set<String> triggerTags, long now) {
		ObserverMemory memory = ModAttachments.memory(player);
		RandomGenerator random = Observer.random(player);
		DialoguePlanner.Plan plan = DialoguePlanner.choose(catalog, rule, ObserverContext.tags(player, triggerTags),
				ObserverContext.counters(player, memory, now), memory, now, random, false);
		DialogueLine line = plan.line();
		if (line == null) {
			return;
		}
		say(player, line);
		memory.recordTrigger(rule.id(), now);
		if (rule.appearChance() > 0 && random.nextDouble() < rule.appearChance()) {
			AppearanceController.appearForComment(player);
		}
	}

	private static TriggerRule effectiveRule(TriggerRule rule) {
		if (HerobrineConfig.get().fourthWall || !rule.categories().contains(FOURTH_WALL)) {
			return rule;
		}
		List<String> categories = new ArrayList<>(rule.categories());
		categories.remove(FOURTH_WALL);
		return new TriggerRule(rule.id(), categories, rule.chance(), rule.cooldownSeconds(), rule.minDelaySeconds(),
				rule.maxDelaySeconds(), rule.patienceSeconds(), rule.appearChance(), rule.inCombat());
	}

	/** About how long he takes to say {@code text}: slowly, with a pause after. */
	public static int durationTicks(@Nullable String text) {
		if (text == null || text.isBlank()) {
			return 60;
		}
		int words = text.strip().split("\\s+").length;
		return Math.max(60, Math.min(300, 30 + words * 9));
	}
}

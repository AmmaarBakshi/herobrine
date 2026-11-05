package io.github.ammaarbakshi.herobrine.behaviour;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

import io.github.ammaarbakshi.herobrine.config.HerobrineConfig;
import io.github.ammaarbakshi.herobrine.encounter.ConfrontationManager;
import io.github.ammaarbakshi.herobrine.entity.HerobrineEntity;
import io.github.ammaarbakshi.herobrine.entity.HerobrineEntity.Mode;
import io.github.ammaarbakshi.herobrine.haunt.HauntLevel;
import io.github.ammaarbakshi.herobrine.memory.Counters;
import io.github.ammaarbakshi.herobrine.memory.ObserverMemory;
import io.github.ammaarbakshi.herobrine.registry.ModAttachments;
import io.github.ammaarbakshi.herobrine.sighting.SightingController;
import io.github.ammaarbakshi.herobrine.sighting.SightingController.Outcome;

/**
 * When and how Herobrine shows himself. Rarity first: every appearance (haunt sightings
 * included) shares one cooldown ({@code appearanceCooldownMinutes}), and after it a roll once a
 * minute ({@code appearanceChancePerMinute}) decides, so the gaps are never regular and are
 * sometimes hours long. He is likelier near the BERSERK memorial, when the player is alone, and
 * at night; he never appears mid-fight, and keeps away from boss fights unless allowed.
 *
 * <p>It also listens to every apparition's life ({@link SightingController.Listener}) and turns
 * it into memory: an encounter when they notice him (and maybe a line), and how it ended.
 */
public final class AppearanceController implements SightingController.Listener {
	/** The ways he can appear on his own. */
	public enum Kind {
		OBSERVER,
		DISTANT,
		BEHIND,
		EYES;

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	private static final int ROLL_INTERVAL = 60 * 20;
	/** Since the last time they saw him, this long counts as "you were gone a while". */
	private static final long LONG_ABSENCE = 2 * 60 * Observer.TICKS_PER_MINUTE;

	private static long tick;

	public static void init() {
		SightingController.setListener(new AppearanceController());
	}

	static void tick(MinecraftServer server) {
		tick++;
		if (!Observer.active()) {
			return;
		}
		List<ServerPlayer> players = server.getPlayerList().getPlayers();
		for (int i = 0; i < players.size(); i++) {
			// Spread over the minute: each player rolls on their own tick.
			if ((tick + i * 37L) % ROLL_INTERVAL == 0) {
				roll(players.get(i));
			}
		}
	}

	private static void roll(ServerPlayer player) {
		HerobrineConfig config = HerobrineConfig.get();
		if (!mayAppear(player, config)) {
			return;
		}
		double chance = Math.min(1.0, config.appearanceChancePerMinute * modifier(player));
		if (player.getRandom().nextDouble() < chance) {
			appear(player, null);
		}
	}

	// --- The gate -----------------------------------------------------------------------------

	/**
	 * The shared rarity gate: true once the cooldown since his last appearance (or since he began
	 * watching this player) is over. Always true with the observer switched off, so the classic
	 * haunt keeps its own pacing.
	 */
	public static boolean offCooldown(ServerPlayer player) {
		if (!Observer.active()) {
			return true;
		}
		ObserverMemory memory = ModAttachments.memory(player);
		long since = Math.max(memory.lastAppearanceTime(), memory.observedSince());
		long cooldown = HerobrineConfig.get().appearanceCooldownMinutes * Observer.TICKS_PER_MINUTE;
		return since < 0 || Observer.now(player) - since >= cooldown;
	}

	/** Free to appear right now: alive, calm, alone with him, and off cooldown. */
	public static boolean mayAppear(ServerPlayer player, HerobrineConfig config) {
		if (!player.isAlive() || player.isSpectator() || player.isSleeping()) {
			return false;
		}
		if (SightingController.isActive(player) || ConfrontationManager.isFighting(player) || ModAttachments.combat(player).inCombat()) {
			return false;
		}
		if (ModAttachments.observer(player).inBossFight(Observer.now(player)) && !config.appearDuringBossFights) {
			return false;
		}
		return offCooldown(player);
	}

	private static double modifier(ServerPlayer player) {
		ObserverState state = ModAttachments.observer(player);
		double modifier = 1.0;
		if (state.nearBerserk) {
			// He lingers near the memorial.
			modifier *= 4.0;
		}
		if (HauntLevel.isAlone(player)) {
			modifier *= 1.25;
		}
		if (player.level().getServer().overworld().isDarkOutside()) {
			modifier *= 1.25;
		}
		return modifier;
	}

	// --- Appearing ----------------------------------------------------------------------------

	/**
	 * Makes him appear now: as {@code kind}, or as a kind picked by the configured weights (in
	 * the order the draw gives, falling back if one finds nowhere to stand). Returns the kind that
	 * worked, or null.
	 */
	public static @Nullable Kind appear(ServerPlayer player, @Nullable Kind kind) {
		List<Kind> order = kind != null ? List.of(kind) : drawOrder(player);
		for (Kind candidate : order) {
			if (spawn(player, candidate)) {
				return candidate;
			}
		}
		return null;
	}

	/** While he comments on something: sometimes he is also standing somewhere nearby. */
	public static void appearForComment(ServerPlayer player) {
		if (mayAppear(player, HerobrineConfig.get())) {
			if (!spawn(player, Kind.OBSERVER)) {
				spawn(player, Kind.DISTANT);
			}
		}
	}

	private static boolean spawn(ServerPlayer player, Kind kind) {
		ObserverState state = ModAttachments.observer(player);
		return switch (kind) {
			case OBSERVER -> {
				AppearanceSpots.Spot spot = AppearanceSpots.observer(player);
				state.flavour = spot == null ? null : spot.flavour();
				yield spot != null && SightingController.spawnObserver(player, spot.feet(), spot.fadeIn());
			}
			case EYES -> {
				AppearanceSpots.Spot spot = AppearanceSpots.eyes(player);
				state.flavour = spot == null ? null : spot.flavour();
				yield spot != null && SightingController.spawnEyes(player, spot.feet());
			}
			case DISTANT -> {
				state.flavour = "distant";
				yield SightingController.spawnDistant(player);
			}
			case BEHIND -> {
				state.flavour = "behind";
				yield SightingController.spawnStalker(player);
			}
		};
	}

	/** Every kind with a positive weight, drawn without replacement by weight. */
	private static List<Kind> drawOrder(ServerPlayer player) {
		HerobrineConfig.AppearanceWeights weights = HerobrineConfig.get().appearanceWeights;
		ObserverState state = ModAttachments.observer(player);
		boolean dark = !player.level().canSeeSky(player.blockPosition().above())
				|| player.level().getServer().overworld().isDarkOutside();
		List<Kind> kinds = new ArrayList<>();
		List<Integer> pool = new ArrayList<>();
		add(kinds, pool, Kind.OBSERVER, weights.observer * (state.nearHome || state.nearVillage ? 2 : 1));
		add(kinds, pool, Kind.DISTANT, weights.distant);
		add(kinds, pool, Kind.BEHIND, weights.behind);
		add(kinds, pool, Kind.EYES, dark ? weights.eyes : 0);

		RandomSource random = player.getRandom();
		List<Kind> order = new ArrayList<>();
		while (!kinds.isEmpty()) {
			int total = pool.stream().mapToInt(Integer::intValue).sum();
			int roll = random.nextInt(total);
			int index = 0;
			while (roll >= pool.get(index)) {
				roll -= pool.get(index);
				index++;
			}
			order.add(kinds.remove(index));
			pool.remove(index);
		}
		return order;
	}

	private static void add(List<Kind> kinds, List<Integer> pool, Kind kind, int weight) {
		if (weight > 0) {
			kinds.add(kind);
			pool.add(weight);
		}
	}

	// --- What happened ------------------------------------------------------------------------

	@Override
	public void appeared(ServerPlayer player, HerobrineEntity herobrine) {
		ModAttachments.memory(player).recordAppearance(Observer.place(player.level(), herobrine.blockPosition(), Observer.now(player)));
	}

	@Override
	public void noticed(ServerPlayer player, HerobrineEntity herobrine) {
		ObserverMemory memory = ModAttachments.memory(player);
		long now = Observer.now(player);
		long previous = memory.lastEncounter();
		boolean first = memory.counter(Counters.ENCOUNTERS) == 0;
		memory.recordEncounter(now);
		if (herobrine.mode() == Mode.JUMPSCARE || herobrine.mode() == Mode.GHOST) {
			// No small talk after a scream or a silent replay.
			return;
		}
		Set<String> tags = new HashSet<>();
		tags.add("mode:" + herobrine.mode().name().toLowerCase(Locale.ROOT));
		String flavour = ModAttachments.observer(player).flavour;
		if (flavour != null) {
			tags.add("place:" + flavour);
		}
		if (first) {
			tags.add("first_meeting");
		} else if (previous >= 0 && now - previous >= LONG_ABSENCE) {
			tags.add("long_absence");
		}
		DialogueController.trigger(player, "encounter", tags);
	}

	@Override
	public void ended(ServerPlayer player, Mode mode, boolean seen, Outcome outcome) {
		ObserverMemory memory = ModAttachments.memory(player);
		ModAttachments.observer(player).flavour = null;
		String reaction = switch (outcome) {
			case STARED_DOWN -> {
				memory.increment(Counters.STARED);
				yield "stared";
			}
			case APPROACHED -> {
				memory.increment(Counters.APPROACHED);
				yield seen ? "approached" : null;
			}
			case LOOKED_AWAY -> {
				memory.increment(Counters.LOOKED_AWAY);
				yield "looked_away";
			}
			case TIMED_OUT -> {
				if (seen) {
					yield null;
				}
				memory.recordIgnored();
				yield "ignored";
			}
			default -> null;
		};
		if (reaction != null && mode != Mode.JUMPSCARE && mode != Mode.GHOST) {
			DialogueController.trigger(player, "reaction", "outcome:" + reaction, "mode:" + mode.name().toLowerCase(Locale.ROOT));
		}
	}
}

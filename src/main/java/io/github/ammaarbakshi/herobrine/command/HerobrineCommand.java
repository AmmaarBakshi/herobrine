package io.github.ammaarbakshi.herobrine.command;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import io.github.ammaarbakshi.herobrine.HerobrineMod;
import io.github.ammaarbakshi.herobrine.behaviour.AppearanceController;
import io.github.ammaarbakshi.herobrine.behaviour.DialogueController;
import io.github.ammaarbakshi.herobrine.config.HerobrineConfig;
import io.github.ammaarbakshi.herobrine.dialogue.DialogueCatalog;
import io.github.ammaarbakshi.herobrine.dialogue.DialogueLine;
import io.github.ammaarbakshi.herobrine.dialogue.DialoguePlanner;
import io.github.ammaarbakshi.herobrine.encounter.ConfrontationManager;
import io.github.ammaarbakshi.herobrine.encounter.EncounterHistory;
import io.github.ammaarbakshi.herobrine.haunt.HauntDirector;
import io.github.ammaarbakshi.herobrine.haunt.HauntLevel;
import io.github.ammaarbakshi.herobrine.haunt.HauntPhase;
import io.github.ammaarbakshi.herobrine.haunt.HauntState;
import io.github.ammaarbakshi.herobrine.haunt.WorldEditLog;
import io.github.ammaarbakshi.herobrine.haunt.events.HauntEvent;
import io.github.ammaarbakshi.herobrine.haunt.events.HauntEvents;
import io.github.ammaarbakshi.herobrine.memory.Counters;
import io.github.ammaarbakshi.herobrine.memory.ObserverMemory;
import io.github.ammaarbakshi.herobrine.memory.Place;
import io.github.ammaarbakshi.herobrine.memory.WorldEvent;
import io.github.ammaarbakshi.herobrine.mirror.PlayerProfile;
import io.github.ammaarbakshi.herobrine.mirror.ProfileAnalyzer;
import io.github.ammaarbakshi.herobrine.mirror.ProfilePresets;
import io.github.ammaarbakshi.herobrine.mirror.ProfileView;
import io.github.ammaarbakshi.herobrine.perf.Perf;
import io.github.ammaarbakshi.herobrine.perf.PerfMeter;
import io.github.ammaarbakshi.herobrine.registry.ModAttachments;
import io.github.ammaarbakshi.herobrine.sighting.SightingController;

/** {@code /herobrine ...} debug and admin commands. Op level 2 (game masters) only. */
public final class HerobrineCommand {
	private HerobrineCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("herobrine")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));

		root.then(Commands.literal("status")
				.executes(ctx -> status(ctx, ctx.getSource().getPlayerOrException()))
				.then(Commands.argument("player", EntityArgument.player())
						.executes(ctx -> status(ctx, EntityArgument.getPlayer(ctx, "player")))));

		root.then(Commands.literal("haunt")
				.then(Commands.literal("set")
						.then(Commands.argument("player", EntityArgument.player())
								.then(Commands.argument("level", IntegerArgumentType.integer(0, HauntPhase.MAX_LEVEL))
										.executes(HerobrineCommand::setHaunt)))));

		root.then(Commands.literal("sighting")
				.then(Commands.argument("player", EntityArgument.player())
						.executes(ctx -> sighting(ctx, SightingKind.DISTANT))
						.then(Commands.literal("stalk").executes(ctx -> sighting(ctx, SightingKind.STALK)))
						.then(Commands.literal("jumpscare").executes(ctx -> sighting(ctx, SightingKind.JUMPSCARE)))));

		root.then(Commands.literal("sign")
				.then(Commands.argument("event", StringArgumentType.word())
						.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(HauntEvents.signIds(), builder))
						.executes(ctx -> sign(ctx, ctx.getSource().getPlayerOrException()))
						.then(Commands.argument("player", EntityArgument.player())
								.executes(ctx -> sign(ctx, EntityArgument.getPlayer(ctx, "player"))))));

		root.then(Commands.literal("undo")
				.executes(ctx -> undo(ctx, 1))
				.then(Commands.argument("count", IntegerArgumentType.integer(1, WorldEditLog.MAX_BATCHES))
						.executes(ctx -> undo(ctx, IntegerArgumentType.getInteger(ctx, "count")))));

		root.then(Commands.literal("profile")
				.then(Commands.argument("player", EntityArgument.player())
						.executes(HerobrineCommand::showProfile)
						.then(Commands.literal("reset").executes(HerobrineCommand::resetProfile))
						.then(Commands.literal("preset")
								.then(Commands.argument("name", StringArgumentType.word())
										.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(ProfilePresets.ids(), builder))
										.executes(HerobrineCommand::presetProfile)))));

		root.then(Commands.literal("perf")
				.executes(HerobrineCommand::perf)
				.then(Commands.literal("reset").executes(ctx -> {
					Perf.resetAll();
					return perf(ctx);
				})));

		root.then(Commands.literal("summon")
				.then(Commands.argument("player", EntityArgument.player())
						.executes(HerobrineCommand::summon)));

		root.then(Commands.literal("reload")
				.executes(HerobrineCommand::reload));

		root.then(Commands.literal("memory")
				.then(Commands.argument("player", EntityArgument.player())
						.executes(HerobrineCommand::showMemory)
						.then(Commands.literal("reset").executes(HerobrineCommand::resetMemory))));

		root.then(Commands.literal("appear")
				.then(Commands.argument("player", EntityArgument.player())
						.executes(ctx -> appear(ctx, null))
						.then(Commands.argument("kind", StringArgumentType.word())
								.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
										Arrays.stream(AppearanceController.Kind.values()).map(AppearanceController.Kind::id), builder))
								.executes(ctx -> appear(ctx, StringArgumentType.getString(ctx, "kind"))))));

		root.then(Commands.literal("voice")
				.then(Commands.literal("say")
						.then(Commands.argument("player", EntityArgument.player())
								.then(Commands.argument("line", StringArgumentType.word())
										.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
												DialogueController.catalog().lines().stream().map(DialogueLine::id), builder))
										.executes(HerobrineCommand::sayLine))))
				.then(Commands.literal("trigger")
						.then(Commands.argument("player", EntityArgument.player())
								.then(Commands.argument("trigger", StringArgumentType.word())
										.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
												DialogueController.catalog().triggers().keySet(), builder))
										.executes(ctx -> forceTrigger(ctx, ""))
										.then(Commands.argument("tags", StringArgumentType.greedyString())
												.executes(ctx -> forceTrigger(ctx, StringArgumentType.getString(ctx, "tags")))))))
				.then(Commands.literal("recordings")
						.executes(HerobrineCommand::recordings)));

		dispatcher.register(root);
	}

	private static int status(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
		HerobrineConfig config = HerobrineConfig.get();
		HauntState haunt = ModAttachments.haunt(player);
		HauntPhase phase = HauntLevel.phase(player);
		double confidence = ProfileAnalyzer.confidence(ModAttachments.profile(player), config.minTraitSamples);
		EncounterHistory history = ModAttachments.encounters(player);

		CommandSourceStack source = ctx.getSource();
		source.sendSuccess(() -> Component.translatable("herobrine.command.status.header", player.getDisplayName())
				.withStyle(ChatFormatting.DARK_RED), false);
		source.sendSuccess(() -> Component.translatable("herobrine.command.status.haunt",
				haunt.levelInt(), HauntPhase.MAX_LEVEL, Component.translatable(phase.translationKey())), false);
		source.sendSuccess(() -> Component.translatable("herobrine.command.status.confidence",
				Math.round(confidence * 100)), false);
		source.sendSuccess(() -> Component.translatable("herobrine.command.status.encounters",
				history.total(), history.wins(), history.losses()), false);
		source.sendSuccess(() -> Component.translatable("herobrine.command.status.sightings", haunt.sightings()), false);
		ObserverMemory memory = ModAttachments.memory(player);
		source.sendSuccess(() -> Component.translatable("herobrine.command.status.observer",
				memory.counter(Counters.ENCOUNTERS), memory.counter(Counters.APPEARANCES), memory.counter(Counters.LINES),
				memory.counter(Counters.DEATHS)), false);
		return haunt.levelInt();
	}

	private static int setHaunt(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		int level = IntegerArgumentType.getInteger(ctx, "level");
		HauntLevel.set(player, level);
		HauntPhase phase = HauntLevel.phase(player);
		ctx.getSource().sendSuccess(() -> Component.translatable("herobrine.command.haunt.set",
				player.getDisplayName(), level, Component.translatable(phase.translationKey())), true);
		return level;
	}

	private enum SightingKind {
		DISTANT, STALK, JUMPSCARE
	}

	private static int sighting(CommandContext<CommandSourceStack> ctx, SightingKind kind) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		SightingController.end(player.getUUID());
		boolean spawned = switch (kind) {
			case DISTANT -> SightingController.spawnDistant(player);
			case STALK -> SightingController.spawnStalker(player);
			case JUMPSCARE -> SightingController.spawnJumpscare(player);
		};
		if (!spawned) {
			ctx.getSource().sendFailure(Component.translatable("herobrine.command.sighting.failed", player.getDisplayName()));
			return 0;
		}
		ctx.getSource().sendSuccess(() -> Component.translatable("herobrine.command.sighting.success", player.getDisplayName()), true);
		return 1;
	}

	private static int sign(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
		String id = StringArgumentType.getString(ctx, "event");
		HauntEvent event = HauntEvents.byId(id);
		if (event == null) {
			ctx.getSource().sendFailure(Component.translatable("herobrine.command.sign.unknown", id, String.join(", ", HauntEvents.signIds())));
			return 0;
		}
		return switch (HauntDirector.force(player, event)) {
			case FIRED -> {
				ctx.getSource().sendSuccess(() -> Component.translatable("herobrine.command.sign.success", id, player.getDisplayName()), true);
				yield 1;
			}
			case NOTHING_FOUND -> {
				ctx.getSource().sendFailure(Component.translatable("herobrine.command.sign.nothing", id, player.getDisplayName()));
				yield 0;
			}
			case DISABLED -> {
				ctx.getSource().sendFailure(Component.translatable("herobrine.command.sign.disabled", id));
				yield 0;
			}
		};
	}

	private static int undo(CommandContext<CommandSourceStack> ctx, int count) {
		WorldEditLog log = WorldEditLog.get(ctx.getSource().getServer());
		int events = Math.min(count, log.size());
		int blocks = log.undo(ctx.getSource().getServer(), count);
		ctx.getSource().sendSuccess(() -> Component.translatable("herobrine.command.undo.success", events, blocks), true);
		return blocks;
	}

	private static int showProfile(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		HerobrineConfig config = HerobrineConfig.get();
		PlayerProfile profile = ModAttachments.profile(player);
		ProfileView view = ProfileView.of(profile, config.minTraitSamples, 0, ModAttachments.combat(player).inCombat());
		CommandSourceStack source = ctx.getSource();
		source.sendSuccess(() -> Component.translatable("herobrine.command.profile.header", player.getDisplayName(),
				Math.round(view.confidence() * 100)).withStyle(ChatFormatting.DARK_RED), false);
		for (ProfileView.Entry entry : view.traits()) {
			source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "  %s = %.3f (%d samples)", entry.key(), entry.value(), entry.samples()))
					.withStyle(entry.known() ? ChatFormatting.WHITE : ChatFormatting.GRAY), false);
		}
		source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "  weaponPref = %s %s (%d samples)",
				view.topWeapon().name().toLowerCase(Locale.ROOT), weights(profile.weaponPref().weights()), profile.weaponPref().samples()))
				.withStyle(view.weaponKnown() ? ChatFormatting.WHITE : ChatFormatting.GRAY), false);
		source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "  healItemPref = %s (%d samples)",
				view.topHealItem().name().toLowerCase(Locale.ROOT), profile.healItemPref().samples()))
				.withStyle(view.healKnown() ? ChatFormatting.WHITE : ChatFormatting.GRAY), false);
		source.sendSuccess(() -> Component.literal("  favoriteBlocks = " + profile.favoriteBlocksRanked()), false);
		source.sendSuccess(() -> Component.literal("  gear = " + profile.gear()), false);
		return (int) Math.round(view.confidence() * 100);
	}

	private static String weights(double[] weights) {
		StringBuilder sb = new StringBuilder("[");
		for (int i = 0; i < weights.length; i++) {
			sb.append(i == 0 ? "" : " ").append(String.format(Locale.ROOT, "%.2f", weights[i]));
		}
		return sb.append(']').toString();
	}

	private static int resetProfile(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		ModAttachments.profile(player).reset();
		ctx.getSource().sendSuccess(() -> Component.translatable("herobrine.command.profile.reset", player.getDisplayName()), true);
		return 1;
	}

	private static int presetProfile(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		String name = StringArgumentType.getString(ctx, "name");
		ProfilePresets preset = ProfilePresets.byId(name).orElse(null);
		if (preset == null) {
			ctx.getSource().sendFailure(Component.translatable("herobrine.command.profile.unknown_preset", name, String.join(", ", ProfilePresets.ids())));
			return 0;
		}
		ModAttachments.profile(player).copyFrom(preset.build());
		ctx.getSource().sendSuccess(() -> Component.translatable("herobrine.command.profile.preset", player.getDisplayName(), name), true);
		return 1;
	}

	private static int summon(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		ConfrontationManager.StartResult result = ConfrontationManager.begin(player, ConfrontationManager.Trigger.COMMAND);
		if (result != ConfrontationManager.StartResult.STARTED) {
			ctx.getSource().sendFailure(Component.translatable("herobrine.command.summon." + result.name().toLowerCase(Locale.ROOT),
					player.getDisplayName()));
			return 0;
		}
		ctx.getSource().sendSuccess(() -> Component.translatable("herobrine.command.summon.success", player.getDisplayName()), true);
		return 1;
	}

	private static int perf(CommandContext<CommandSourceStack> ctx) {
		sendPerf(ctx, "recorder", Perf.RECORDER);
		sendPerf(ctx, "world", Perf.WORLD);
		sendPerf(ctx, "mirror", Perf.MIRROR_AI);
		return (int) Math.round(Perf.RECORDER.snapshot().averageMicros());
	}

	private static void sendPerf(CommandContext<CommandSourceStack> ctx, String key, PerfMeter meter) {
		PerfMeter.Snapshot snapshot = meter.snapshot();
		ctx.getSource().sendSuccess(() -> Component.translatable("herobrine.command.perf." + key,
				String.format(Locale.ROOT, "%.2f", snapshot.averageMicros()),
				String.format(Locale.ROOT, "%.1f", snapshot.worstMicros()), snapshot.units()), false);
	}

	// --- The Living Observer --------------------------------------------------------------------

	private static int showMemory(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		ObserverMemory memory = ModAttachments.memory(player);
		CommandSourceStack source = ctx.getSource();
		source.sendSuccess(() -> Component.translatable("herobrine.command.memory.header", player.getDisplayName())
				.withStyle(ChatFormatting.DARK_RED), false);
		StringBuilder counters = new StringBuilder();
		for (Map.Entry<String, Integer> entry : memory.counters().entrySet()) {
			counters.append(counters.isEmpty() ? "" : ", ").append(entry.getKey()).append('=').append(entry.getValue());
		}
		line(source, "counters", counters.isEmpty() ? "-" : counters.toString());
		line(source, "milestones", memory.milestones().isEmpty() ? "-" : String.join(", ", memory.milestones()));
		line(source, "last appearance", describe(memory.lastAppearance()));
		line(source, "home", describe(memory.home()));
		line(source, "recent lines", memory.recentLines().isEmpty() ? "-" : String.join(", ", memory.recentLines()));
		List<WorldEvent> events = memory.events();
		line(source, "events", events.isEmpty() ? "-" : String.join(", ",
				events.subList(Math.max(0, events.size() - 8), events.size()).stream().map(WorldEvent::id).toList()));
		line(source, "waiting lines", Integer.toString(DialogueController.waiting(player)));
		return memory.counter(Counters.ENCOUNTERS);
	}

	private static void line(CommandSourceStack source, String label, String value) {
		source.sendSuccess(() -> Component.literal("  " + label + ": ").withStyle(ChatFormatting.GRAY)
				.append(Component.literal(value).withStyle(ChatFormatting.WHITE)), false);
	}

	private static String describe(@Nullable Place place) {
		return place == null ? "-" : String.format(Locale.ROOT, "%s %d %d %d", place.dimension(), place.x(), place.y(), place.z());
	}

	private static int resetMemory(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		ModAttachments.memory(player).clear();
		ctx.getSource().sendSuccess(() -> Component.translatable("herobrine.command.memory.reset", player.getDisplayName()), true);
		return 1;
	}

	private static int appear(CommandContext<CommandSourceStack> ctx, @Nullable String kindName)
			throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		AppearanceController.Kind kind = null;
		if (kindName != null) {
			kind = Arrays.stream(AppearanceController.Kind.values()).filter(k -> k.id().equals(kindName)).findFirst().orElse(null);
			if (kind == null) {
				ctx.getSource().sendFailure(Component.translatable("herobrine.command.appear.unknown", kindName));
				return 0;
			}
		}
		SightingController.end(player.getUUID());
		AppearanceController.Kind result = AppearanceController.appear(player, kind);
		if (result == null) {
			ctx.getSource().sendFailure(Component.translatable("herobrine.command.sighting.failed", player.getDisplayName()));
			return 0;
		}
		ctx.getSource().sendSuccess(() -> Component.translatable("herobrine.command.appear.success", result.id(), player.getDisplayName()), true);
		return 1;
	}

	private static int sayLine(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		String id = StringArgumentType.getString(ctx, "line");
		DialogueLine line = DialogueController.catalog().line(id).orElse(null);
		if (line == null) {
			ctx.getSource().sendFailure(Component.translatable("herobrine.command.voice.unknown_line", id));
			return 0;
		}
		DialogueController.say(player, line);
		ctx.getSource().sendSuccess(() -> Component.translatable("herobrine.command.voice.said", id, player.getDisplayName(),
				hasRecording(line) ? Component.translatable("herobrine.command.voice.recorded") : Component.translatable("herobrine.command.voice.caption")), false);
		return 1;
	}

	private static int forceTrigger(CommandContext<CommandSourceStack> ctx, String tagText) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		String trigger = StringArgumentType.getString(ctx, "trigger");
		Set<String> tags = new HashSet<>();
		for (String tag : tagText.trim().split("\\s+")) {
			if (!tag.isBlank()) {
				tags.add(tag);
			}
		}
		DialoguePlanner.Plan plan = DialogueController.force(player, trigger, tags);
		if (plan.line() == null) {
			ctx.getSource().sendFailure(Component.translatable("herobrine.command.voice.nothing", trigger, plan.reason()));
			return 0;
		}
		ctx.getSource().sendSuccess(() -> Component.translatable("herobrine.command.voice.said", plan.line().id(), player.getDisplayName(),
				hasRecording(plan.line()) ? Component.translatable("herobrine.command.voice.recorded") : Component.translatable("herobrine.command.voice.caption")), false);
		return 1;
	}

	/** Lists which lines still need a recording, per category: a to-do list for the voice session. */
	private static int recordings(CommandContext<CommandSourceStack> ctx) {
		DialogueCatalog catalog = DialogueController.catalog();
		int recorded = 0;
		CommandSourceStack source = ctx.getSource();
		for (String category : catalog.categoryNames()) {
			List<DialogueLine> lines = catalog.category(category);
			List<String> missing = lines.stream().filter(line -> !hasRecording(line)).map(DialogueLine::id).toList();
			recorded += lines.size() - missing.size();
			source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "  %s: %d/%d recorded", category,
					lines.size() - missing.size(), lines.size())).withStyle(missing.isEmpty() ? ChatFormatting.GREEN : ChatFormatting.GRAY), false);
		}
		int total = recorded;
		source.sendSuccess(() -> Component.translatable("herobrine.command.voice.recordings", total, catalog.size()), false);
		return recorded;
	}

	/** True if the recording ships in the mod jar. Resource packs on clients can still add or replace it. */
	private static boolean hasRecording(DialogueLine line) {
		return HerobrineMod.class.getClassLoader().getResource(line.audioFile()) != null;
	}

	private static int reload(CommandContext<CommandSourceStack> ctx) {
		HerobrineConfig.load();
		ctx.getSource().sendSuccess(() -> Component.translatable("herobrine.command.reload.success"), true);
		return 1;
	}
}

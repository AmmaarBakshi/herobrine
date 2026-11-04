package io.github.ammaarbakshi.herobrine.command;

import java.util.Locale;

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

import io.github.ammaarbakshi.herobrine.config.HerobrineConfig;
import io.github.ammaarbakshi.herobrine.encounter.ConfrontationManager;
import io.github.ammaarbakshi.herobrine.haunt.HauntDirector;
import io.github.ammaarbakshi.herobrine.haunt.HauntLevel;
import io.github.ammaarbakshi.herobrine.haunt.HauntPhase;
import io.github.ammaarbakshi.herobrine.haunt.HauntState;
import io.github.ammaarbakshi.herobrine.haunt.WorldEditLog;
import io.github.ammaarbakshi.herobrine.haunt.events.HauntEvent;
import io.github.ammaarbakshi.herobrine.haunt.events.HauntEvents;
import io.github.ammaarbakshi.herobrine.mirror.CounterPlan;
import io.github.ammaarbakshi.herobrine.mirror.PlayerBehaviorRecorder;
import io.github.ammaarbakshi.herobrine.mirror.PlayerProfile;
import io.github.ammaarbakshi.herobrine.mirror.ProfileAnalyzer;
import io.github.ammaarbakshi.herobrine.mirror.ProfilePresets;
import io.github.ammaarbakshi.herobrine.mirror.ProfileView;
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
					PlayerBehaviorRecorder.resetCost();
					return perf(ctx);
				})));

		root.then(Commands.literal("summon")
				.then(Commands.argument("player", EntityArgument.player())
						.executes(HerobrineCommand::summon)));

		root.then(Commands.literal("reload")
				.executes(HerobrineCommand::reload));

		dispatcher.register(root);
	}

	private static int status(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
		HerobrineConfig config = HerobrineConfig.get();
		HauntState haunt = ModAttachments.haunt(player);
		HauntPhase phase = HauntLevel.phase(player);
		double confidence = ProfileAnalyzer.confidence(ModAttachments.profile(player), config.minTraitSamples);
		int encounters = 0;

		CommandSourceStack source = ctx.getSource();
		source.sendSuccess(() -> Component.translatable("herobrine.command.status.header", player.getDisplayName())
				.withStyle(ChatFormatting.DARK_RED), false);
		source.sendSuccess(() -> Component.translatable("herobrine.command.status.haunt",
				haunt.levelInt(), HauntPhase.MAX_LEVEL, Component.translatable(phase.translationKey())), false);
		source.sendSuccess(() -> Component.translatable("herobrine.command.status.confidence",
				Math.round(confidence * 100)), false);
		source.sendSuccess(() -> Component.translatable("herobrine.command.status.encounters", encounters), false);
		source.sendSuccess(() -> Component.translatable("herobrine.command.status.sightings", haunt.sightings()), false);
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
		if (ConfrontationManager.summon(player, CounterPlan.NONE) == null) {
			ctx.getSource().sendFailure(Component.translatable("herobrine.command.summon.failed", player.getDisplayName()));
			return 0;
		}
		ctx.getSource().sendSuccess(() -> Component.translatable("herobrine.command.summon.success", player.getDisplayName()), true);
		return 1;
	}

	private static int perf(CommandContext<CommandSourceStack> ctx) {
		PlayerBehaviorRecorder.Cost cost = PlayerBehaviorRecorder.cost();
		ctx.getSource().sendSuccess(() -> Component.translatable("herobrine.command.perf",
				String.format(Locale.ROOT, "%.2f", cost.averageMicrosPerPlayerTick()),
				String.format(Locale.ROOT, "%.1f", cost.worstMicros()), cost.playerTicks()), false);
		return (int) Math.round(cost.averageMicrosPerPlayerTick());
	}

	private static int reload(CommandContext<CommandSourceStack> ctx) {
		HerobrineConfig.load();
		ctx.getSource().sendSuccess(() -> Component.translatable("herobrine.command.reload.success"), true);
		return 1;
	}
}

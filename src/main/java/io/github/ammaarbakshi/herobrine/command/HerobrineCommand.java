package io.github.ammaarbakshi.herobrine.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import io.github.ammaarbakshi.herobrine.config.HerobrineConfig;
import io.github.ammaarbakshi.herobrine.haunt.HauntLevel;
import io.github.ammaarbakshi.herobrine.haunt.HauntPhase;
import io.github.ammaarbakshi.herobrine.haunt.HauntState;
import io.github.ammaarbakshi.herobrine.mirror.ProfileAnalyzer;
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

	private static int reload(CommandContext<CommandSourceStack> ctx) {
		HerobrineConfig.load();
		ctx.getSource().sendSuccess(() -> Component.translatable("herobrine.command.reload.success"), true);
		return 1;
	}
}

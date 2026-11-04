package io.github.ammaarbakshi.herobrine.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import io.github.ammaarbakshi.herobrine.config.HerobrineConfig;
import io.github.ammaarbakshi.herobrine.haunt.HauntPhase;

/** {@code /herobrine ...} debug and admin commands. Op level 2 (game masters) only. */
public final class HerobrineCommand {
	private HerobrineCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("herobrine")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("status")
						.executes(ctx -> status(ctx, ctx.getSource().getPlayerOrException()))
						.then(Commands.argument("player", EntityArgument.player())
								.executes(ctx -> status(ctx, EntityArgument.getPlayer(ctx, "player")))))
				.then(Commands.literal("reload")
						.executes(HerobrineCommand::reload)));
	}

	private static int status(CommandContext<CommandSourceStack> ctx, ServerPlayer player) throws CommandSyntaxException {
		HerobrineConfig config = HerobrineConfig.get();
		Status status = Status.of(player);
		HauntPhase phase = HauntPhase.of(status.hauntLevel(),
				config.sightingsThreshold, config.stalkingThreshold, config.confrontationThreshold);

		CommandSourceStack source = ctx.getSource();
		source.sendSuccess(() -> Component.translatable("herobrine.command.status.header", player.getDisplayName())
				.withStyle(ChatFormatting.DARK_RED), false);
		source.sendSuccess(() -> Component.translatable("herobrine.command.status.haunt",
				status.hauntLevel(), HauntPhase.MAX_LEVEL, Component.translatable(phase.translationKey())), false);
		source.sendSuccess(() -> Component.translatable("herobrine.command.status.confidence",
				Math.round(status.confidence() * 100)), false);
		source.sendSuccess(() -> Component.translatable("herobrine.command.status.encounters",
				status.encounters()), false);
		return status.hauntLevel();
	}

	private static int reload(CommandContext<CommandSourceStack> ctx) {
		HerobrineConfig.load();
		ctx.getSource().sendSuccess(() -> Component.translatable("herobrine.command.reload.success"), true);
		return 1;
	}

	/** Snapshot of a player's haunt state for display. */
	private record Status(int hauntLevel, double confidence, int encounters) {
		static Status of(ServerPlayer player) {
			// Placeholder until per-player persistence lands; values then come from attachments.
			return new Status(0, 0.0, 0);
		}
	}
}

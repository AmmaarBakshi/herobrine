package io.github.ammaarbakshi.herobrine.haunt.events;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * A single grey "Herobrine joined the game" line, sent only to the haunted player. At most once
 * per in-game day and very rarely picked; can be switched off with {@code fakeChatMessages}.
 */
final class FakeChatEvent implements HauntEvent {
	@Override
	public String id() {
		return "fake_chat";
	}

	@Override
	public int weight(EventContext ctx) {
		return ctx.config().fakeChatMessages && ctx.haunt().canFakeChat(ctx.day()) ? 1 : 0;
	}

	@Override
	public boolean fire(EventContext ctx) {
		if (!ctx.config().fakeChatMessages) {
			return false;
		}
		// Vanilla's own translation key, so it reads exactly like a real join in every language.
		ctx.player().sendSystemMessage(Component.translatable("multiplayer.player.joined", "Herobrine").withStyle(ChatFormatting.GRAY));
		ctx.haunt().recordFakeChat(ctx.day());
		return true;
	}
}

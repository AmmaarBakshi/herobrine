package io.github.ammaarbakshi.herobrine.registry;

import net.minecraft.world.entity.player.Player;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

import io.github.ammaarbakshi.herobrine.HerobrineMod;
import io.github.ammaarbakshi.herobrine.haunt.HauntState;
import io.github.ammaarbakshi.herobrine.mirror.PlayerProfile;
import io.github.ammaarbakshi.herobrine.mirror.ProfileCodecs;

/**
 * Per-player persistent data via the Fabric Data Attachment API. All of it is saved with the
 * player and copied to the new player entity on death, so nothing is lost on respawn.
 *
 * <p>The attached objects are mutable and only touched on the server thread. Player data is
 * written out whenever the player is saved, so in-place mutation is persisted without needing
 * to re-set the attachment.
 */
public final class ModAttachments {
	public static final AttachmentType<HauntState> HAUNT = AttachmentRegistry.create(HerobrineMod.id("haunt"),
			builder -> builder.persistent(HauntState.CODEC).copyOnDeath().initializer(HauntState::new));

	public static final AttachmentType<PlayerProfile> PROFILE = AttachmentRegistry.create(HerobrineMod.id("profile"),
			builder -> builder.persistent(ProfileCodecs.PROFILE).copyOnDeath().initializer(PlayerProfile::new));

	private ModAttachments() {
	}

	public static void init() {
	}

	public static HauntState haunt(Player player) {
		return player.getAttachedOrCreate(HAUNT);
	}

	public static PlayerProfile profile(Player player) {
		return player.getAttachedOrCreate(PROFILE);
	}
}

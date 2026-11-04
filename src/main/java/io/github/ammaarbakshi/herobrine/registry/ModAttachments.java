package io.github.ammaarbakshi.herobrine.registry;

import net.minecraft.world.entity.player.Player;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

import io.github.ammaarbakshi.herobrine.HerobrineMod;
import io.github.ammaarbakshi.herobrine.encounter.EncounterHistory;
import io.github.ammaarbakshi.herobrine.haunt.HauntState;
import io.github.ammaarbakshi.herobrine.haunt.PlayerTrail;
import io.github.ammaarbakshi.herobrine.mirror.CombatState;
import io.github.ammaarbakshi.herobrine.mirror.PlayerProfile;
import io.github.ammaarbakshi.herobrine.mirror.ProfileCodecs;

/**
 * Per-player data via the Fabric Data Attachment API. Haunt and profile are saved with the
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

	public static final AttachmentType<EncounterHistory> ENCOUNTERS = AttachmentRegistry.create(HerobrineMod.id("encounters"),
			builder -> builder.persistent(EncounterHistory.CODEC).copyOnDeath().initializer(EncounterHistory::new));

	/** Transient: not saved and not copied on death, so it starts fresh after a relog or respawn. */
	public static final AttachmentType<PlayerTrail> TRAIL = AttachmentRegistry.createDefaulted(HerobrineMod.id("trail"), PlayerTrail::new);

	/** Transient recorder bookkeeping for the fight in progress. */
	public static final AttachmentType<CombatState> COMBAT = AttachmentRegistry.createDefaulted(HerobrineMod.id("combat"), CombatState::new);

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

	public static EncounterHistory encounters(Player player) {
		return player.getAttachedOrCreate(ENCOUNTERS);
	}

	public static PlayerTrail trail(Player player) {
		return player.getAttachedOrCreate(TRAIL);
	}

	public static CombatState combat(Player player) {
		return player.getAttachedOrCreate(COMBAT);
	}
}

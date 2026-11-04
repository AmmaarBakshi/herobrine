package io.github.ammaarbakshi.herobrine.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import io.github.ammaarbakshi.herobrine.config.HerobrineConfig;
import io.github.ammaarbakshi.herobrine.entity.ai.HerobrineMoveControl;
import io.github.ammaarbakshi.herobrine.entity.ai.MirrorBowGoal;
import io.github.ammaarbakshi.herobrine.entity.ai.MirrorBrain;
import io.github.ammaarbakshi.herobrine.entity.ai.MirrorHealGoal;
import io.github.ammaarbakshi.herobrine.entity.ai.MirrorMeleeGoal;
import io.github.ammaarbakshi.herobrine.entity.ai.MirrorRetreatGoal;
import io.github.ammaarbakshi.herobrine.entity.ai.MirrorShieldGoal;
import io.github.ammaarbakshi.herobrine.entity.ai.MirrorTacticSelector;
import io.github.ammaarbakshi.herobrine.entity.ai.PillarGoal;
import io.github.ammaarbakshi.herobrine.entity.ai.StareGoal;
import io.github.ammaarbakshi.herobrine.entity.ai.StrafeGoal;
import io.github.ammaarbakshi.herobrine.mirror.CounterPlan;
import io.github.ammaarbakshi.herobrine.mirror.PlayerProfile;
import io.github.ammaarbakshi.herobrine.registry.ModAttachments;

/**
 * Herobrine. One instance always belongs to exactly one player (its "watched" player);
 * other players can see him, but only the watched player drives his behaviour.
 *
 * <p>He is never spawned naturally: only the haunt director, the sighting controller and the
 * confrontation manager create him, and they pick a {@link Mode} up front.
 */
public final class HerobrineEntity extends Monster {
	public enum Mode {
		/** Spawn egg / debug: stands and stares at the nearest player. Saved with the chunk. */
		IDLE(true),
		/** Distant sighting: stares, vanishes when looked at, approached or after a timeout. */
		SIGHTING(false),
		/** Close behind the player; gone the moment they turn around. */
		STALKING(false),
		/** A few ticks right in the player's face. */
		JUMPSCARE(false),
		/** Silently re-enacts the player's own recent movement. */
		GHOST(false),
		/** The Mirror boss fight. Saved so a chunk unload mid-fight resumes safely. */
		BOSS(true),
		/** Stands over the respawn point after the player loses, then leaves. */
		AFTERMATH(false);

		private final boolean persistent;

		Mode(boolean persistent) {
			this.persistent = persistent;
		}

		public boolean persistent() {
			return persistent;
		}

		static Mode byName(String name) {
			try {
				return valueOf(name.toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException e) {
				return IDLE;
			}
		}
	}

	public static final double DEFAULT_HEALTH = 40.0;
	/** Roughly a walking player; goals scale it up for sprinting. */
	public static final double BASE_SPEED = 0.3;

	private Mode mode = Mode.IDLE;
	private @Nullable UUID watchedPlayerId;
	private int ageInMode;
	/** Boss mode only; rebuilt lazily from the saved fields below after a reload. */
	private @Nullable MirrorBrain brain;
	private int savedHealsUsed;
	private List<BlockPos> savedPlacedBlocks = List.of();
	private @Nullable BlockState savedPillarBlock;
	private final ServerBossEvent bossEvent = new ServerBossEvent(Mth.createInsecureUUID(this.random),
			Component.translatable("entity.herobrine.herobrine"), BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.PROGRESS);

	public HerobrineEntity(EntityType<? extends HerobrineEntity> type, Level level) {
		super(type, level);
		this.moveControl = new HerobrineMoveControl(this);
		// His gear is a copy of the player's; it must never drop (no duping).
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			setDropChance(slot, 0.0F);
		}
		setPersistenceRequired();
	}

	{
		bossEvent.setDarkenScreen(true);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, DEFAULT_HEALTH)
				.add(Attributes.MOVEMENT_SPEED, BASE_SPEED)
				.add(Attributes.FOLLOW_RANGE, 64.0)
				.add(Attributes.ATTACK_DAMAGE, 1.0);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		// Boss mode: the selector picks a tactic, the rest act on it (see MirrorTacticSelector).
		goalSelector.addGoal(1, new MirrorTacticSelector(this));
		goalSelector.addGoal(2, new MirrorHealGoal(this));
		goalSelector.addGoal(2, new MirrorRetreatGoal(this));
		goalSelector.addGoal(2, new PillarGoal(this));
		goalSelector.addGoal(3, new MirrorBowGoal(this));
		goalSelector.addGoal(3, new MirrorMeleeGoal(this));
		goalSelector.addGoal(4, new StrafeGoal(this));
		goalSelector.addGoal(4, new MirrorShieldGoal(this));
		// Every other mode: stand still and stare.
		goalSelector.addGoal(5, new StareGoal(this));
	}

	// --- Identity ----------------------------------------------------------------------------

	public Mode mode() {
		return mode;
	}

	public void setMode(Mode mode) {
		this.mode = mode;
		this.ageInMode = 0;
	}

	public int ageInMode() {
		return ageInMode;
	}

	public @Nullable UUID watchedPlayerId() {
		return watchedPlayerId;
	}

	public void setWatchedPlayer(@Nullable Player player) {
		this.watchedPlayerId = player == null ? null : player.getUUID();
	}

	/** The player this instance belongs to, if they are online and in the same level. */
	public @Nullable ServerPlayer watchedPlayer() {
		if (watchedPlayerId == null || !(level() instanceof ServerLevel serverLevel)) {
			return null;
		}
		return serverLevel.getPlayerByUUID(watchedPlayerId) instanceof ServerPlayer player ? player : null;
	}

	// --- The Mirror --------------------------------------------------------------------------

	/**
	 * Turns this instance into the boss for {@code player}: their health budget, their gear,
	 * and a brain built from a snapshot of their profile.
	 */
	public void startBoss(ServerPlayer player, CounterPlan plan) {
		HerobrineConfig config = HerobrineConfig.get();
		setMode(Mode.BOSS);
		setWatchedPlayer(player);
		getAttribute(Attributes.MAX_HEALTH).setBaseValue(config.bossHealth);
		setHealth(getMaxHealth());
		PlayerProfile profile = ModAttachments.profile(player);
		brain = MirrorBrain.create(this, profile, config, plan, 0);
		brain.equip(profile.gear());
	}

	/** The boss brain, or null outside boss mode (or while his player is offline). */
	public @Nullable MirrorBrain brain() {
		if (mode != Mode.BOSS) {
			return null;
		}
		if (brain == null) {
			ServerPlayer player = watchedPlayer();
			if (player == null) {
				return null;
			}
			// Reloaded mid-fight: same player, same heal budget, same blocks to clean up.
			brain = MirrorBrain.create(this, ModAttachments.profile(player), HerobrineConfig.get(), CounterPlan.NONE, savedHealsUsed);
			brain.restorePlaced(savedPlacedBlocks);
			savedPlacedBlocks = List.of();
		}
		return brain;
	}

	/** Leaves without a trace: no death animation, no particles, no drops. */
	public void vanish() {
		discard();
	}

	// --- Lifecycle ---------------------------------------------------------------------------

	@Override
	public void tick() {
		super.tick();
		ageInMode++;
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (mode == Mode.BOSS) {
			bossEvent.setProgress(getHealth() / getMaxHealth());
		}
	}

	@Override
	public void startSeenByPlayer(ServerPlayer player) {
		super.startSeenByPlayer(player);
		if (mode == Mode.BOSS) {
			bossEvent.addPlayer(player);
		}
	}

	@Override
	public void stopSeenByPlayer(ServerPlayer player) {
		super.stopSeenByPlayer(player);
		bossEvent.removePlayer(player);
	}

	@Override
	public void remove(RemovalReason reason) {
		if (reason.shouldDestroy() && level() instanceof ServerLevel serverLevel) {
			// The fight is over (or he was removed): take the pillar back down.
			if (brain != null) {
				brain.cleanUp(serverLevel);
			} else if (savedPillarBlock != null) {
				MirrorBrain.removeBlocks(serverLevel, savedPlacedBlocks, savedPillarBlock);
			}
			bossEvent.removeAllPlayers();
		}
		super.remove(reason);
	}

	@Override
	public boolean shouldBeSaved() {
		// Sightings are transient: if the chunk unloads he was simply never there.
		return mode.persistent() && super.shouldBeSaved();
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return false;
	}

	@Override
	public boolean canPickUpLoot() {
		return false;
	}

	@Override
	public boolean canBeSeenAsEnemy() {
		// Other mobs (golems, wolves, ...) use this check when picking targets, so they ignore him.
		return false;
	}

	@Override
	public boolean isPushable() {
		return mode == Mode.BOSS && super.isPushable();
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		if (mode == Mode.IDLE || mode == Mode.BOSS) {
			return super.hurtServer(level, source, damage);
		}
		// Apparitions can't be fought: anything that touches him (arrows included) makes him vanish.
		if (!source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			vanish();
		}
		return false;
	}

	@Override
	protected boolean shouldDropLoot(ServerLevel level) {
		return false;
	}

	// --- Persistence -------------------------------------------------------------------------

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putString("HerobrineMode", mode.name().toLowerCase(Locale.ROOT));
		output.storeNullable("WatchedPlayer", UUIDUtil.CODEC, watchedPlayerId);
		if (mode == Mode.BOSS) {
			output.putInt("BossHealsUsed", brain != null ? brain.healsUsed() : savedHealsUsed);
			List<BlockPos> placed = brain != null ? brain.placedBlocks() : savedPlacedBlocks;
			BlockState pillar = brain != null ? brain.pillarBlock() : savedPillarBlock;
			if (!placed.isEmpty() && pillar != null) {
				output.store("PillarBlocks", BlockPos.CODEC.listOf(), placed);
				output.store("PillarBlock", BlockState.CODEC, pillar);
			}
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		mode = Mode.byName(input.getStringOr("HerobrineMode", "idle"));
		watchedPlayerId = input.read("WatchedPlayer", UUIDUtil.CODEC).orElse(null);
		savedHealsUsed = input.getIntOr("BossHealsUsed", 0);
		savedPlacedBlocks = new ArrayList<>(input.read("PillarBlocks", BlockPos.CODEC.listOf()).orElse(List.of()));
		savedPillarBlock = input.read("PillarBlock", BlockState.CODEC).orElse(null);
		brain = null;
	}
}

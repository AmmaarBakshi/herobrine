package io.github.ammaarbakshi.herobrine.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import io.github.ammaarbakshi.herobrine.config.HerobrineConfig;
import io.github.ammaarbakshi.herobrine.entity.ai.AntiPillarGoal;
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
import io.github.ammaarbakshi.herobrine.perf.Perf;
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
		/**
		 * The Living Observer: stands in plain view and watches for a while. Leaves when the player
		 * walks up to him, or once they have seen him and look away.
		 */
		OBSERVER(false),
		/** Only his glowing eyes, in the dark. Gone if approached or stared at for too long. */
		EYES(false),
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

	/** How he arrives and leaves; synced so the client can fade him in and out. */
	public enum Fade {
		/** There at once (the classic way: he is simply there when you turn). */
		INSTANT,
		FADING_IN,
		FADING_OUT
	}

	public static final int FADE_IN_TICKS = 30;
	public static final int FADE_OUT_TICKS = 16;

	private static final EntityDataAccessor<Byte> DATA_MODE = SynchedEntityData.defineId(HerobrineEntity.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Byte> DATA_FADE = SynchedEntityData.defineId(HerobrineEntity.class, EntityDataSerializers.BYTE);

	public static final double DEFAULT_HEALTH = 40.0;
	/** Roughly a walking player; goals scale it up for sprinting. */
	public static final double BASE_SPEED = 0.3;
	/** Vanilla survival reach, eyes to the target's hitbox. */
	public static final double PLAYER_REACH = 3.0;

	private Mode mode = Mode.IDLE;
	private @Nullable UUID watchedPlayerId;
	private int ageInMode;
	/** Age at which a fading-out apparition is removed; -1 while he isn't leaving. */
	private int leaveAt = -1;
	/** Client only: {@link #tickCount} when the current fade began. */
	private int fadeStartTick;
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

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_MODE, (byte) Mode.IDLE.ordinal());
		builder.define(DATA_FADE, (byte) Fade.INSTANT.ordinal());
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
		super.onSyncedDataUpdated(accessor);
		if (accessor.equals(DATA_FADE)) {
			fadeStartTick = tickCount;
		} else if (accessor.equals(DATA_MODE) && level().isClientSide()) {
			mode = Mode.values()[Math.floorMod(entityData.get(DATA_MODE), Mode.values().length)];
		}
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
		goalSelector.addGoal(2, new AntiPillarGoal(this));
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
		entityData.set(DATA_MODE, (byte) mode.ordinal());
	}

	public Fade fade() {
		return Fade.values()[Math.floorMod(entityData.get(DATA_FADE), Fade.values().length)];
	}

	/** Fades in from nothing instead of simply being there. Call before adding him to the level. */
	public void fadeIn() {
		entityData.set(DATA_FADE, (byte) Fade.FADING_IN.ordinal());
	}

	/** True while he is leaving: he can no longer be touched and is about to be removed. */
	public boolean isLeaving() {
		return leaveAt >= 0;
	}

	/**
	 * Client only: how visible he is right now, 0 to 1. Fades are timed on the client from the
	 * moment the change arrived, so they are smooth whatever the server's tick rate.
	 */
	public float visibility(float partialTick) {
		float elapsed = tickCount - fadeStartTick + partialTick;
		return switch (fade()) {
			case INSTANT -> 1.0F;
			case FADING_IN -> Mth.clamp(elapsed / FADE_IN_TICKS, 0.0F, 1.0F);
			case FADING_OUT -> Mth.clamp(1.0F - elapsed / FADE_OUT_TICKS, 0.0F, 1.0F);
		};
	}

	/** Client only: just the eyes, no body, no shadow. */
	public boolean eyesOnly() {
		return mode == Mode.EYES;
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

	/**
	 * Leaves where the player can see it: fades out over {@link #FADE_OUT_TICKS} with a breath of
	 * smoke, then is removed. For when vanishing outright would look like a glitch.
	 */
	public void fadeAway() {
		if (isLeaving() || isRemoved()) {
			return;
		}
		leaveAt = ageInMode + FADE_OUT_TICKS;
		entityData.set(DATA_FADE, (byte) Fade.FADING_OUT.ordinal());
		getNavigation().stop();
		if (level() instanceof ServerLevel serverLevel && mode != Mode.EYES) {
			serverLevel.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 1.0, getZ(), 12, 0.25, 0.6, 0.25, 0.01);
		}
	}

	// --- Lifecycle ---------------------------------------------------------------------------

	@Override
	public void tick() {
		super.tick();
		ageInMode++;
		if (leaveAt >= 0 && ageInMode >= leaveAt && !level().isClientSide()) {
			discard();
		}
	}

	@Override
	public void aiStep() {
		if (mode != Mode.BOSS || level().isClientSide()) {
			super.aiStep();
			return;
		}
		// Goals, navigation and movement: what one boss costs the server per tick.
		long start = System.nanoTime();
		super.aiStep();
		Perf.MIRROR_AI.sample(System.nanoTime() - start);
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
		if (isLeaving()) {
			return false;
		}
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
	protected void blockUsingItem(ServerLevel level, LivingEntity attacker, DamageSource source, float damage, boolean fullyBlocked) {
		super.blockUsingItem(level, attacker, source, damage, fullyBlocked);
		// Same rule as for players: an axe hit on a raised shield knocks it down for a while.
		ItemStack blocking = getItemBlockingWith();
		BlocksAttacks blocksAttacks = blocking != null ? blocking.get(DataComponents.BLOCKS_ATTACKS) : null;
		float seconds = attacker.getSecondsToDisableBlocking();
		if (seconds > 0.0F && blocksAttacks != null) {
			blocksAttacks.disable(level, this, seconds, blocking);
			MirrorBrain mirror = brain();
			if (mirror != null) {
				mirror.disableShield(level.getGameTime(), Math.round(seconds * 20.0F));
			}
		}
	}

	/**
	 * A player's reach (eyes to hitbox), not a mob's hug-range: he hits from where they would.
	 * The vanilla mob box would make him walk into them before every swing.
	 */
	@Override
	public boolean isWithinMeleeAttackRange(LivingEntity target) {
		return target.getBoundingBox().distanceToSqr(getEyePosition()) < PLAYER_REACH * PLAYER_REACH;
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
		setMode(Mode.byName(input.getStringOr("HerobrineMode", "idle")));
		watchedPlayerId = input.read("WatchedPlayer", UUIDUtil.CODEC).orElse(null);
		savedHealsUsed = input.getIntOr("BossHealsUsed", 0);
		savedPlacedBlocks = new ArrayList<>(input.read("PillarBlocks", BlockPos.CODEC.listOf()).orElse(List.of()));
		savedPillarBlock = input.read("PillarBlock", BlockState.CODEC).orElse(null);
		brain = null;
	}
}

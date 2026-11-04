package io.github.ammaarbakshi.herobrine.mirror;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import io.github.ammaarbakshi.herobrine.config.HerobrineConfig;
import io.github.ammaarbakshi.herobrine.net.DebugProfilePayload;
import io.github.ammaarbakshi.herobrine.registry.ModAttachments;

/**
 * Learns how each player fights. Always on, server-side, and only active while the player is
 * in combat (see {@link CombatTracker}).
 *
 * <p>Discrete actions (hits, heals, pearls, blocks) come from Fabric events. Movement is
 * sampled every 5 ticks, staggered across players. The only per-tick work is reading two
 * fields for bow draw and shield raise timing. Total cost is measured: see {@link #cost()}.
 */
public final class PlayerBehaviorRecorder {
	/** Low health that opens a "will they pearl away?" window. */
	private static final double LOW_HEALTH = 0.4;
	private static final double REARM_HEALTH = 0.5;
	private static final int PEARL_WINDOW = 5 * 20;
	private static final int MAX_SHIELD_REACTION = 2 * 20;
	/** Episodes shorter than this say nothing about pillaring. */
	private static final int MIN_EPISODE_FOR_PILLAR = 3 * 20;
	private static final double FALL_FOR_CLUTCH = 3.0;
	private static final int DEBUG_INTERVAL = 10;
	private static final double ARROW_COMBAT_RANGE = 32.0;

	private static @Nullable Stat<Identifier> jumpStat;

	// Cost accounting (server thread only).
	private static long nanos;
	private static long playerTicks;
	private static long worstTickNanos;

	private PlayerBehaviorRecorder() {
	}

	/** Measured recorder cost so far. */
	public record Cost(double averageMicrosPerPlayerTick, double worstMicros, long playerTicks) {
	}

	public static Cost cost() {
		return new Cost(playerTicks == 0 ? 0.0 : nanos / 1000.0 / playerTicks, worstTickNanos / 1000.0, playerTicks);
	}

	public static void resetCost() {
		nanos = 0;
		playerTicks = 0;
		worstTickNanos = 0;
	}

	public static void init() {
		AttackEntityCallback.EVENT.register(PlayerBehaviorRecorder::onAttack);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(PlayerBehaviorRecorder::onDamage);
		ServerEntityEvents.ENTITY_LOAD.register(PlayerBehaviorRecorder::onEntityLoad);
		UseItemCallback.EVENT.register(PlayerBehaviorRecorder::onUseItem);
		UseBlockCallback.EVENT.register(PlayerBehaviorRecorder::onUseBlock);
		ServerTickEvents.END_SERVER_TICK.register(PlayerBehaviorRecorder::tick);
	}

	private static CombatState state(ServerPlayer player) {
		return ModAttachments.combat(player);
	}

	private static long now(ServerPlayer player) {
		return player.level().getServer().getTickCount();
	}

	// --- Events -------------------------------------------------------------------------------

	private static InteractionResult onAttack(Player player, Level level, InteractionHand hand, Entity entity, @Nullable EntityHitResult hit) {
		if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer) || player.isSpectator() || !CombatTracker.isHostile(entity)) {
			return InteractionResult.PASS;
		}
		long start = System.nanoTime();
		CombatState state = state(serverPlayer);
		long now = now(serverPlayer);
		engage(serverPlayer, state, (LivingEntity) entity, now);

		PlayerProfile profile = ModAttachments.profile(serverPlayer);
		float charge = serverPlayer.getAttackStrengthScale(0.5F);
		boolean full = charge > 0.9F;
		// Vanilla's crit and knockback-hit rules, read before the attack resolves.
		boolean crit = full && serverPlayer.fallDistance > 0.0 && !serverPlayer.onGround() && !serverPlayer.onClimbable()
				&& !serverPlayer.isInWater() && !serverPlayer.isPassenger() && !serverPlayer.isSprinting();
		boolean sprintHit = full && serverPlayer.isSprinting();
		profile.observe(TraitId.AVG_ATTACK_CHARGE, charge);
		profile.observe(TraitId.CRIT_RATE, crit ? 1.0 : 0.0);
		profile.observe(TraitId.SPRINT_HIT_RATE, sprintHit ? 1.0 : 0.0);
		profile.observe(TraitId.RANGED_RATIO, 0.0);
		ItemStack weapon = serverPlayer.getMainHandItem();
		WeaponType type = weaponType(weapon);
		profile.observeWeapon(type);
		if (type != WeaponType.FIST && type == profile.topWeapon()) {
			profile.setGear(profile.gear().withWeapon(itemId(weapon)));
		}
		account(start);
		return InteractionResult.PASS;
	}

	private static void onDamage(LivingEntity entity, DamageSource source, float baseDamage, float damageTaken, boolean blocked) {
		if (entity instanceof ServerPlayer player) {
			onPlayerHurt(player, source, damageTaken, blocked);
		} else if (source.getEntity() instanceof ServerPlayer player && source.getDirectEntity() instanceof Projectile
				&& CombatTracker.isHostile(entity)) {
			// A projectile of theirs hit a hostile: a ranged attack.
			long start = System.nanoTime();
			engage(player, state(player), entity, now(player));
			ModAttachments.profile(player).observe(TraitId.RANGED_RATIO, 1.0);
			account(start);
		}
	}

	private static void onPlayerHurt(ServerPlayer player, DamageSource source, float damageTaken, boolean blocked) {
		if (player.isSpectator()) {
			return;
		}
		long start = System.nanoTime();
		CombatState state = state(player);
		long now = now(player);
		PlayerProfile profile = ModAttachments.profile(player);
		if (CombatTracker.isHostile(source.getEntity())) {
			engage(player, state, (LivingEntity) source.getEntity(), now);
			profile.observe(TraitId.SHIELD_USE_RATE, blocked ? 1.0 : 0.0);
			if (!blocked && hasShield(player)) {
				state.shieldReactionFrom = now;
			}
		} else if (source.is(DamageTypeTags.IS_FALL) && state.inCombat && damageTaken > 0 && hasInHotbar(player, Items.WATER_BUCKET)) {
			// Had a bucket, took the fall anyway.
			profile.observe(TraitId.WATER_CLUTCH_RATE, 0.0);
		}
		account(start);
	}

	private static void onEntityLoad(Entity entity, ServerLevel level) {
		if (!(entity instanceof AbstractArrow arrow) || entity instanceof ThrownTrident || entity.tickCount != 0
				|| !(arrow.getOwner() instanceof ServerPlayer player)) {
			return;
		}
		long start = System.nanoTime();
		CombatState state = state(player);
		long now = now(player);
		// Released this tick from a bow we saw being drawn (not a crossbow, not a dispenser).
		if (now - state.lastBowDrawTick <= 2 && (state.inCombat || hostileNearby(player))) {
			ModAttachments.profile(player).observe(TraitId.BOW_DRAW_TICKS, state.lastBowDrawTicks);
		}
		account(start);
	}

	private static InteractionResult onUseItem(Player player, Level level, InteractionHand hand) {
		if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.PASS;
		}
		CombatState state = state(serverPlayer);
		if (!state.inCombat) {
			return InteractionResult.PASS;
		}
		long start = System.nanoTime();
		PlayerProfile profile = ModAttachments.profile(serverPlayer);
		ItemStack stack = player.getItemInHand(hand);
		if (stack.is(Items.ENDER_PEARL)) {
			if (state.pearlWindowEnd >= now(serverPlayer)) {
				profile.observe(TraitId.PEARL_ESCAPE_RATE, 1.0);
				state.pearlWindowEnd = -1;
			}
		} else if (stack.is(Items.WATER_BUCKET)) {
			if (!player.onGround() && player.fallDistance >= FALL_FOR_CLUTCH) {
				profile.observe(TraitId.WATER_CLUTCH_RATE, 1.0);
			}
		} else {
			HealItem heal = healItem(serverPlayer, stack);
			if (heal != null) {
				double health = healthFraction(serverPlayer);
				Trait threshold = profile.trait(TraitId.HEAL_THRESHOLD);
				if (threshold.samples() > 0) {
					profile.observe(TraitId.HEAL_THRESHOLD_SPREAD, Math.abs(health - threshold.value()));
				}
				profile.observe(TraitId.HEAL_THRESHOLD, health);
				profile.observeHealItem(heal);
			}
		}
		account(start);
		return InteractionResult.PASS;
	}

	private static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.PASS;
		}
		ItemStack stack = player.getItemInHand(hand);
		CombatState state = state(serverPlayer);
		if (!state.inCombat || !(stack.getItem() instanceof BlockItem)) {
			return InteractionResult.PASS;
		}
		long start = System.nanoTime();
		ModAttachments.profile(serverPlayer).observeBlock(itemId(stack));
		BlockPos clicked = hit.getBlockPos();
		BlockPos placed = level.getBlockState(clicked).canBeReplaced() ? clicked : clicked.relative(hit.getDirection());
		// Pillaring: in the air, putting a block right under their own feet.
		if (!player.onGround() && placed.getX() == player.getBlockX() && placed.getZ() == player.getBlockZ()
				&& placed.getY() < player.getY() && placed.getY() >= player.getBlockY() - 2) {
			state.pillaredThisEpisode = true;
		}
		account(start);
		return InteractionResult.PASS;
	}

	// --- Ticking ------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		List<ServerPlayer> players = server.getPlayerList().getPlayers();
		if (players.isEmpty()) {
			return;
		}
		long now = server.getTickCount();
		HerobrineConfig config = HerobrineConfig.get();
		if (!config.enabled) {
			return;
		}
		for (int i = 0; i < players.size(); i++) {
			ServerPlayer player = players.get(i);
			long start = System.nanoTime();
			playerTicks++;
			if (!player.isSpectator() && player.isAlive()) {
				CombatState state = state(player);
				trackItemUse(player, state, now);
				if ((now + i) % CombatMotion.SAMPLE_TICKS == 0) {
					sample(player, state, now, config);
				}
			}
			long spent = System.nanoTime() - start;
			nanos += spent;
			worstTickNanos = Math.max(worstTickNanos, spent);
			// The debug HUD is an opt-in diagnostic, not part of the recorder's cost.
			CombatState state = player.getAttached(ModAttachments.COMBAT);
			if (state != null && state.debugPending) {
				state.debugPending = false;
				sendDebug(player, ModAttachments.profile(player), config, state.inCombat);
			}
		}
	}

	/** Bow draw length and shield reaction need tick precision; this is just a few field reads. */
	private static void trackItemUse(ServerPlayer player, CombatState state, long now) {
		boolean raising = false;
		if (player.isUsingItem()) {
			ItemStack using = player.getUseItem();
			if (using.getItem() instanceof BowItem) {
				state.lastBowDrawTicks = player.getTicksUsingItem();
				state.lastBowDrawTick = now;
			} else {
				raising = using.has(DataComponents.BLOCKS_ATTACKS);
			}
		}
		if (raising && !state.wasRaisingShield && state.shieldReactionFrom >= 0) {
			long reaction = now - state.shieldReactionFrom;
			if (reaction <= MAX_SHIELD_REACTION) {
				ModAttachments.profile(player).observe(TraitId.SHIELD_REACTION_TICKS, reaction);
			}
			state.shieldReactionFrom = -1;
		}
		state.wasRaisingShield = raising;
	}

	private static void sample(ServerPlayer player, CombatState state, long now, HerobrineConfig config) {
		CombatTracker.refresh(player, state, now);
		boolean inCombat = CombatTracker.isInCombat(state, now);
		if (inCombat && !state.inCombat) {
			startEpisode(player, state, now);
		} else if (!inCombat && state.inCombat) {
			endEpisode(player, state, now, config);
		}
		if (!inCombat) {
			return;
		}

		PlayerProfile profile = ModAttachments.profile(player);
		int jumps = player.getStats().getValue(jumpStat());
		LivingEntity target = state.target;
		double health = healthFraction(player);
		if (target != null && state.hasPrevious) {
			CombatMotion.Motion motion = CombatMotion.of(state.prevX, state.prevZ, player.getX(), player.getZ(), target.getX(), target.getZ());
			profile.observe(TraitId.STRAFE_INTENSITY, motion.strafeIntensity());
			if (motion.strafing()) {
				profile.observe(TraitId.STRAFE_BIAS, motion.strafeDirection());
			}
			profile.observe(TraitId.PREFERRED_RANGE, motion.distance());
			profile.observe(TraitId.JUMP_FREQUENCY, (jumps - state.prevJumps) * (20.0 / CombatMotion.SAMPLE_TICKS));
			if (motion.backingOff()) {
				// Half a second of backing off while hurt: that's a retreat, at this much health.
				if (++state.backingOffSamples == 2 && health < CombatMotion.RETREAT_MAX_HEALTH) {
					profile.observe(TraitId.RETREAT_THRESHOLD, health);
				}
			} else {
				state.backingOffSamples = 0;
			}
		}
		state.prevX = player.getX();
		state.prevZ = player.getZ();
		state.prevJumps = jumps;
		state.hasPrevious = true;

		if (health >= REARM_HEALTH) {
			state.lowHealthArmed = true;
		} else if (state.lowHealthArmed && health < LOW_HEALTH && hasInInventory(player, Items.ENDER_PEARL)) {
			state.lowHealthArmed = false;
			state.pearlWindowEnd = now + PEARL_WINDOW;
		}
		if (state.pearlWindowEnd >= 0 && now > state.pearlWindowEnd) {
			profile.observe(TraitId.PEARL_ESCAPE_RATE, 0.0);
			state.pearlWindowEnd = -1;
		}

		if (config.debugOverlay && now % DEBUG_INTERVAL < CombatMotion.SAMPLE_TICKS) {
			state.debugPending = true;
		}
	}

	private static void engage(ServerPlayer player, CombatState state, LivingEntity enemy, long now) {
		CombatTracker.engage(state, enemy, now);
		if (!state.inCombat) {
			startEpisode(player, state, now);
		}
	}

	private static void startEpisode(ServerPlayer player, CombatState state, long now) {
		state.inCombat = true;
		state.episodeStart = now;
		state.pillaredThisEpisode = false;
		state.hasPrevious = false;
		state.backingOffSamples = 0;
		PlayerProfile profile = ModAttachments.profile(player);
		profile.setGear(profile.gear()
				.withArmor(itemId(player.getItemBySlot(EquipmentSlot.HEAD)), itemId(player.getItemBySlot(EquipmentSlot.CHEST)),
						itemId(player.getItemBySlot(EquipmentSlot.LEGS)), itemId(player.getItemBySlot(EquipmentSlot.FEET)))
				.withOffhand(itemId(player.getOffhandItem())));
	}

	private static void endEpisode(ServerPlayer player, CombatState state, long now, HerobrineConfig config) {
		PlayerProfile profile = ModAttachments.profile(player);
		if (now - state.episodeStart >= MIN_EPISODE_FOR_PILLAR && hasBlocksInHotbar(player)) {
			profile.observe(TraitId.PILLAR_RATE, state.pillaredThisEpisode ? 1.0 : 0.0);
		}
		if (state.pearlWindowEnd >= 0) {
			profile.observe(TraitId.PEARL_ESCAPE_RATE, 0.0);
			state.pearlWindowEnd = -1;
		}
		state.inCombat = false;
		state.target = null;
		state.hasPrevious = false;
		state.shieldReactionFrom = -1;
		state.debugPending = config.debugOverlay;
	}

	private static void sendDebug(ServerPlayer player, PlayerProfile profile, HerobrineConfig config, boolean inCombat) {
		if (ServerPlayNetworking.canSend(player, DebugProfilePayload.TYPE)) {
			ServerPlayNetworking.send(player, new DebugProfilePayload(ProfileView.of(profile, config.minTraitSamples, 0, inCombat)));
		}
	}

	// --- Helpers ------------------------------------------------------------------------------

	private static void account(long start) {
		nanos += System.nanoTime() - start;
	}

	private static Stat<Identifier> jumpStat() {
		Stat<Identifier> stat = jumpStat;
		if (stat == null) {
			stat = Stats.CUSTOM.get(Stats.JUMP);
			jumpStat = stat;
		}
		return stat;
	}

	public static WeaponType weaponType(ItemStack stack) {
		if (stack.is(ItemTags.SWORDS) || stack.is(ItemTags.SPEARS)) {
			return WeaponType.SWORD;
		}
		if (stack.is(ItemTags.AXES)) {
			return WeaponType.AXE;
		}
		if (stack.is(Items.TRIDENT)) {
			return WeaponType.TRIDENT;
		}
		if (stack.is(Items.MACE)) {
			return WeaponType.MACE;
		}
		return WeaponType.FIST;
	}

	private static @Nullable HealItem healItem(ServerPlayer player, ItemStack stack) {
		if (stack.is(Items.GOLDEN_APPLE) || stack.is(Items.ENCHANTED_GOLDEN_APPLE)) {
			return HealItem.GOLDEN_APPLE;
		}
		PotionContents potion = stack.get(DataComponents.POTION_CONTENTS);
		if (potion != null) {
			for (MobEffectInstance effect : potion.getAllEffects()) {
				if (effect.is(MobEffects.INSTANT_HEALTH) || effect.is(MobEffects.REGENERATION)) {
					return HealItem.POTION;
				}
			}
			return null;
		}
		FoodProperties food = stack.get(DataComponents.FOOD);
		return food != null && player.canEat(food.canAlwaysEat()) ? HealItem.FOOD : null;
	}

	private static double healthFraction(ServerPlayer player) {
		return player.getMaxHealth() <= 0 ? 0 : player.getHealth() / player.getMaxHealth();
	}

	private static boolean hasShield(ServerPlayer player) {
		return player.getMainHandItem().has(DataComponents.BLOCKS_ATTACKS) || player.getOffhandItem().has(DataComponents.BLOCKS_ATTACKS);
	}

	private static boolean hasInHotbar(ServerPlayer player, Item item) {
		for (int slot = 0; slot < 9; slot++) {
			if (player.getInventory().getItem(slot).is(item)) {
				return true;
			}
		}
		return player.getOffhandItem().is(item);
	}

	private static boolean hasBlocksInHotbar(ServerPlayer player) {
		for (int slot = 0; slot < 9; slot++) {
			if (player.getInventory().getItem(slot).getItem() instanceof BlockItem) {
				return true;
			}
		}
		return player.getOffhandItem().getItem() instanceof BlockItem;
	}

	private static boolean hasInInventory(ServerPlayer player, Item item) {
		return player.getInventory().contains(stack -> stack.is(item));
	}

	private static boolean hostileNearby(ServerPlayer player) {
		return !player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(ARROW_COMBAT_RANGE),
				CombatTracker::isHostile).isEmpty();
	}

	private static String itemId(ItemStack stack) {
		return stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
	}
}

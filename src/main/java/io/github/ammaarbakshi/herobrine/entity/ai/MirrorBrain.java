package io.github.ammaarbakshi.herobrine.entity.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import io.github.ammaarbakshi.herobrine.config.HerobrineConfig;
import io.github.ammaarbakshi.herobrine.entity.HerobrineEntity;
import io.github.ammaarbakshi.herobrine.mirror.CounterPlan;
import io.github.ammaarbakshi.herobrine.mirror.GearSnapshot;
import io.github.ammaarbakshi.herobrine.mirror.HealItem;
import io.github.ammaarbakshi.herobrine.mirror.PlayerBehaviorRecorder;
import io.github.ammaarbakshi.herobrine.mirror.PlayerProfile;
import io.github.ammaarbakshi.herobrine.mirror.ProfileAnalyzer;
import io.github.ammaarbakshi.herobrine.mirror.TacticPlanner;
import io.github.ammaarbakshi.herobrine.mirror.TacticPlanner.Tactic;
import io.github.ammaarbakshi.herobrine.mirror.TraitId;
import io.github.ammaarbakshi.herobrine.mirror.WeaponType;

/**
 * Everything the Mirror needs for one fight: a frozen copy of the player's profile (so what he
 * learns mid-fight doesn't shift his behaviour under him), the resolved habits, his items, and
 * the fight's running state. Owned by one {@link HerobrineEntity} in boss mode.
 */
public final class MirrorBrain {
	private static final int HEAL_COOLDOWN = 10 * 20;
	private static final int RETREAT_COOLDOWN = 10 * 20;

	private final HerobrineEntity herobrine;
	private final PlayerProfile profile;
	private final int minSamples;
	private final int rareSamples;
	private final int healCap;
	private final CounterPlan plan;
	private final TacticPlanner.Habits habits;

	private final ItemStack weapon;
	private final @Nullable ItemStack bow;
	private final ItemStack healItem;
	private final HealItem healKind;
	private final BlockState pillarBlock;

	private Tactic tactic = Tactic.MELEE;
	/** Starts "committed long enough" so the very first decision is a real one. */
	private int ticksInTactic = TacticPlanner.MIN_COMMIT_TICKS;
	private long healCooldownUntil;
	private long retreatCooldownUntil;
	private boolean pillaring;
	private int healsUsed;
	private final List<BlockPos> placedBlocks = new ArrayList<>();

	// Counted for tests and the end-of-fight summary.
	public int swings;
	public int shots;
	public int shieldRaises;

	private MirrorBrain(HerobrineEntity herobrine, PlayerProfile profile, HerobrineConfig config, CounterPlan plan, int healsUsed) {
		this.herobrine = herobrine;
		this.profile = profile;
		this.minSamples = config.minTraitSamples;
		this.rareSamples = ProfileAnalyzer.rareMinSamples(minSamples);
		this.healCap = config.healItemCap;
		this.plan = plan;
		this.healsUsed = healsUsed;
		this.habits = new TacticPlanner.Habits(value(TraitId.RANGED_RATIO), value(TraitId.PREFERRED_RANGE),
				value(TraitId.HEAL_THRESHOLD), value(TraitId.RETREAT_THRESHOLD), value(TraitId.PILLAR_RATE),
				value(TraitId.PEARL_ESCAPE_RATE));
		WeaponType type = profile.weaponPref().known(minSamples) ? profile.topWeapon() : WeaponType.SWORD;
		this.weapon = weaponFor(type, profile.gear());
		this.bow = habits.rangedRatio() > 0.05 ? new ItemStack(Items.BOW) : null;
		this.healKind = profile.healItemPref().known(rareSamples) ? profile.topHealItem() : HealItem.FOOD;
		this.healItem = healStack(healKind);
		this.pillarBlock = pillarBlock(profile);
	}

	/** Snapshot of the player's profile taken as the fight begins. */
	public static MirrorBrain create(HerobrineEntity herobrine, PlayerProfile profile, HerobrineConfig config, CounterPlan plan, int healsUsed) {
		return new MirrorBrain(herobrine, profile.copy(), config, plan, healsUsed);
	}

	// --- Habits -------------------------------------------------------------------------------

	/** The player's habit, or a sensible default while it isn't known yet. */
	public double value(TraitId id) {
		return profile.trait(id).valueOr(id.core() ? minSamples : rareSamples, id.fallback());
	}

	public TacticPlanner.Habits habits() {
		return habits;
	}

	public PlayerProfile profile() {
		return profile;
	}

	public CounterPlan plan() {
		return plan;
	}

	// --- Fight state --------------------------------------------------------------------------

	public @Nullable ServerPlayer target() {
		ServerPlayer player = herobrine.watchedPlayer();
		return player != null && player.isAlive() && !player.isSpectator() && !player.isCreative() ? player : null;
	}

	public Tactic tactic() {
		return tactic;
	}

	public int ticksInTactic() {
		return ticksInTactic;
	}

	public void setTactic(Tactic next) {
		if (next != tactic) {
			tactic = next;
			ticksInTactic = 0;
		}
	}

	void tickTactic() {
		ticksInTactic++;
	}

	public int healsLeft() {
		return Math.max(0, healCap - healsUsed);
	}

	public int healsUsed() {
		return healsUsed;
	}

	void onHealed(long now) {
		healsUsed++;
		healCooldownUntil = now + HEAL_COOLDOWN;
	}

	boolean healOnCooldown(long now) {
		return now < healCooldownUntil;
	}

	void onRetreatEnded(long now) {
		retreatCooldownUntil = now + RETREAT_COOLDOWN;
	}

	boolean retreatOnCooldown(long now) {
		return now < retreatCooldownUntil;
	}

	public boolean pillaring() {
		return pillaring;
	}

	void setPillaring(boolean pillaring) {
		this.pillaring = pillaring;
	}

	// --- Items --------------------------------------------------------------------------------

	/** Puts on fresh, unenchanted copies of the player's gear. Nothing he holds ever drops. */
	public void equip(GearSnapshot gear) {
		herobrine.setItemSlot(EquipmentSlot.HEAD, stackOf(gear.head()));
		herobrine.setItemSlot(EquipmentSlot.CHEST, stackOf(gear.chest()));
		herobrine.setItemSlot(EquipmentSlot.LEGS, stackOf(gear.legs()));
		herobrine.setItemSlot(EquipmentSlot.FEET, stackOf(gear.feet()));
		boolean shield = gear.offhand().equals("minecraft:shield") || value(TraitId.SHIELD_USE_RATE) > 0.1;
		herobrine.setItemSlot(EquipmentSlot.OFFHAND, shield ? new ItemStack(Items.SHIELD) : ItemStack.EMPTY);
		holdWeapon();
	}

	public void holdWeapon() {
		hold(weapon);
	}

	public boolean hasBow() {
		return bow != null;
	}

	public void holdBow() {
		if (bow != null) {
			hold(bow);
		}
	}

	public void holdHealItem() {
		hold(healItem);
	}

	public HealItem healKind() {
		return healKind;
	}

	public boolean hasShield() {
		return herobrine.getOffhandItem().is(Items.SHIELD);
	}

	/** What he is swinging right now (counters may have swapped it). */
	public WeaponType weaponType() {
		return typeOf(herobrine.getMainHandItem());
	}

	/** Swaps the main hand (counters use this to switch to an axe against shields). */
	public void swapWeapon(ItemStack stack) {
		hold(stack);
	}

	private void hold(ItemStack stack) {
		if (!ItemStack.isSameItem(herobrine.getMainHandItem(), stack) || herobrine.getMainHandItem().isEmpty() != stack.isEmpty()) {
			herobrine.setItemSlot(EquipmentSlot.MAINHAND, stack.copy());
		}
	}

	public BlockState pillarBlock() {
		return pillarBlock;
	}

	void rememberPlaced(BlockPos pos) {
		placedBlocks.add(pos.immutable());
	}

	/** Blocks he has placed and not yet removed, for saving mid-fight. */
	public List<BlockPos> placedBlocks() {
		return List.copyOf(placedBlocks);
	}

	/** Restores the placed-block list after a reload so cleanup still finds them. */
	public void restorePlaced(List<BlockPos> blocks) {
		blocks.forEach(this::rememberPlaced);
	}

	/** Takes his pillar down again so the fight leaves no trace. */
	public void cleanUp(ServerLevel level) {
		for (int i = placedBlocks.size() - 1; i >= 0; i--) {
			BlockPos pos = placedBlocks.get(i);
			if (level.isLoaded(pos) && level.getBlockState(pos) == pillarBlock) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			}
		}
		placedBlocks.clear();
	}

	// --- Item helpers -------------------------------------------------------------------------

	private static ItemStack stackOf(String id) {
		if (id.isEmpty()) {
			return ItemStack.EMPTY;
		}
		Identifier key = Identifier.tryParse(id);
		Optional<Item> item = key == null ? Optional.empty() : BuiltInRegistries.ITEM.getOptional(key);
		return item.map(ItemStack::new).orElse(ItemStack.EMPTY);
	}

	/** The player's own weapon if it matches their favourite type, else that type at iron tier. */
	static ItemStack weaponFor(WeaponType type, GearSnapshot gear) {
		ItemStack theirs = stackOf(gear.weapon());
		if (!theirs.isEmpty() && typeOf(theirs) == type) {
			return theirs;
		}
		return switch (type) {
			case SWORD -> new ItemStack(Items.IRON_SWORD);
			case AXE -> new ItemStack(Items.IRON_AXE);
			case TRIDENT -> new ItemStack(Items.TRIDENT);
			case MACE -> new ItemStack(Items.MACE);
			case FIST -> ItemStack.EMPTY;
		};
	}

	static WeaponType typeOf(ItemStack stack) {
		return PlayerBehaviorRecorder.weaponType(stack);
	}

	private static ItemStack healStack(HealItem kind) {
		return switch (kind) {
			case GOLDEN_APPLE -> new ItemStack(Items.GOLDEN_APPLE);
			case POTION -> PotionContents.createItemStack(Items.POTION, Potions.HEALING);
			case FOOD -> new ItemStack(Items.COOKED_BEEF);
		};
	}

	private static BlockState pillarBlock(PlayerProfile profile) {
		for (String id : profile.favoriteBlocksRanked()) {
			if (stackOf(id).getItem() instanceof BlockItem blockItem) {
				BlockState state = blockItem.getBlock().defaultBlockState();
				if (state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) {
					return state;
				}
			}
		}
		return Blocks.COBBLESTONE.defaultBlockState();
	}
}

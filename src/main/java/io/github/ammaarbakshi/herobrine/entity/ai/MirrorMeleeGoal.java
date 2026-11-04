package io.github.ammaarbakshi.herobrine.entity.ai;

import java.util.EnumSet;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import io.github.ammaarbakshi.herobrine.HerobrineMod;
import io.github.ammaarbakshi.herobrine.entity.HerobrineEntity;
import io.github.ammaarbakshi.herobrine.mirror.TacticPlanner;
import io.github.ammaarbakshi.herobrine.mirror.TacticPlanner.Tactic;
import io.github.ammaarbakshi.herobrine.mirror.TraitId;
import io.github.ammaarbakshi.herobrine.mirror.WeaponType;

/**
 * Hits the way they hit: spams if they spam, waits for the full cooldown if they do, jump-crits
 * and sprint-hits as often as they do. Damage follows vanilla's own rules: the weapon's
 * attributes, scaled by charge, x1.5 on a crit, extra knockback on a sprint-hit.
 */
public final class MirrorMeleeGoal extends MirrorGoal {
	private static final AttributeModifier.Operation MULTIPLY = AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
	private static final Identifier CHARGE_ID = HerobrineMod.id("swing_charge");
	private static final Identifier SPRINT_ID = HerobrineMod.id("sprint_hit");
	/** How long he waits in the air for a jump to turn into a falling crit. */
	private static final int CRIT_WINDOW = 14;

	private int cooldown;
	private int critTimer;

	public MirrorMeleeGoal(HerobrineEntity herobrine) {
		super(herobrine, EnumSet.of(Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		MirrorBrain brain = brain();
		return brain != null && !brain.pillaring() && active(Tactic.MELEE);
	}

	@Override
	public void start() {
		MirrorBrain brain = brain();
		if (brain != null) {
			brain.holdWeapon();
		}
	}

	@Override
	public void tick() {
		MirrorBrain brain = brain();
		ServerPlayer target = target();
		if (brain == null || target == null || !(herobrine.level() instanceof ServerLevel level)) {
			return;
		}
		herobrine.getLookControl().setLookAt(target, 30.0F, 30.0F);
		cooldown--;
		boolean inReach = herobrine.isWithinMeleeAttackRange(target) && herobrine.getSensing().hasLineOfSight(target);

		if (critTimer > 0) {
			critTimer--;
			if (inReach && !herobrine.onGround() && herobrine.getDeltaMovement().y < 0) {
				critTimer = 0;
				attack(level, brain, target, true);
			}
			return;
		}
		if (cooldown > 0 || !inReach || isBlocking()) {
			return;
		}
		if (herobrine.onGround() && herobrine.getRandom().nextDouble() < brain.value(TraitId.CRIT_RATE)) {
			herobrine.getJumpControl().jump();
			critTimer = CRIT_WINDOW;
			return;
		}
		attack(level, brain, target, false);
	}

	private void attack(ServerLevel level, MirrorBrain brain, ServerPlayer target, boolean crit) {
		double charge = crit ? 1.0 : Mth.clamp(brain.value(TraitId.AVG_ATTACK_CHARGE) + herobrine.getRandom().nextGaussian() * 0.08, 0.05, 1.0);
		boolean sprintHit = !crit && charge > 0.9 && herobrine.getRandom().nextDouble() < brain.value(TraitId.SPRINT_HIT_RATE);
		AttributeInstance damage = herobrine.getAttribute(Attributes.ATTACK_DAMAGE);
		AttributeInstance knockback = herobrine.getAttribute(Attributes.ATTACK_KNOCKBACK);
		double scale = TacticPlanner.chargeDamageScale(charge) * (crit ? 1.5 : 1.0);
		damage.addTransientModifier(new AttributeModifier(CHARGE_ID, scale - 1.0, MULTIPLY));
		if (sprintHit) {
			knockback.addTransientModifier(new AttributeModifier(SPRINT_ID, 1.0, AttributeModifier.Operation.ADD_VALUE));
		}
		herobrine.swingForAttack(InteractionHand.MAIN_HAND);
		boolean hit = herobrine.doHurtTarget(level, target);
		damage.removeModifier(CHARGE_ID);
		knockback.removeModifier(SPRINT_ID);
		brain.swings++;
		if (hit && crit) {
			level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.5), target.getZ(), 12, 0.3, 0.4, 0.3, 0.2);
			level.playSound(null, herobrine.getX(), herobrine.getY(), herobrine.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, herobrine.getSoundSource(), 1.0F, 1.0F);
		}
		cooldown = TacticPlanner.swingInterval(charge, fullCooldownTicks(brain.weaponType()));
	}

	private boolean isBlocking() {
		return herobrine.isUsingItem() && herobrine.getUsedItemHand() == InteractionHand.OFF_HAND;
	}

	/** 20 / attack speed of each weapon type (vanilla values for the common tiers). */
	static double fullCooldownTicks(WeaponType type) {
		return switch (type) {
			case SWORD -> 12.5;
			case AXE -> 20.0;
			case TRIDENT -> 18.2;
			case MACE -> 33.3;
			case FIST -> 5.0;
		};
	}
}

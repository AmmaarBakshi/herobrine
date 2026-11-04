package io.github.ammaarbakshi.herobrine.mirror;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;

import io.github.ammaarbakshi.herobrine.entity.HerobrineEntity;

/**
 * Decides when a player is "in combat": they hit or were hit by a hostile mob in the last
 * 5 seconds, or a hostile mob within 8 blocks is targeting them. Also keeps track of who
 * they are fighting, since movement is measured relative to that mob.
 */
public final class CombatTracker {
	public static final int COMBAT_TIMEOUT = 5 * 20;
	private static final double THREAT_RANGE = 8.0;
	/** Movement relative to a mob farther than this says nothing about how they fight it. */
	private static final double TARGET_RANGE = 24.0;

	private CombatTracker() {
	}

	/** Hostile and worth learning from. Apparitions don't count; Herobrine as the boss does. */
	public static boolean isHostile(@Nullable Entity entity) {
		if (!(entity instanceof LivingEntity living) || !(entity instanceof Enemy) || !living.isAlive()) {
			return false;
		}
		return !(entity instanceof HerobrineEntity herobrine) || herobrine.mode() == HerobrineEntity.Mode.BOSS;
	}

	/** A hit given or taken: they're fighting {@code enemy} right now. */
	static void engage(CombatState state, LivingEntity enemy, long now) {
		state.lastCombatTick = now;
		state.target = enemy;
	}

	static boolean isInCombat(CombatState state, long now) {
		return now - state.lastCombatTick <= COMBAT_TIMEOUT;
	}

	/** Drops a target that died or wandered off, and picks up mobs that are hunting the player. */
	static void refresh(ServerPlayer player, CombatState state, long now) {
		LivingEntity target = state.target;
		if (target != null && (!target.isAlive() || target.level() != player.level()
				|| target.distanceToSqr(player) > TARGET_RANGE * TARGET_RANGE)) {
			state.target = null;
		}
		if (state.target != null && isInCombat(state, now)) {
			return;
		}
		Mob threat = nearestThreat(player);
		if (threat != null) {
			engage(state, threat, now);
		}
	}

	private static @Nullable Mob nearestThreat(ServerPlayer player) {
		AABB box = player.getBoundingBox().inflate(THREAT_RANGE);
		List<Mob> mobs = player.level().getEntitiesOfClass(Mob.class, box, mob -> mob.getTarget() == player && isHostile(mob));
		Mob nearest = null;
		double best = THREAT_RANGE * THREAT_RANGE;
		for (Mob mob : mobs) {
			double d = mob.distanceToSqr(player);
			if (d <= best) {
				best = d;
				nearest = mob;
			}
		}
		return nearest;
	}
}

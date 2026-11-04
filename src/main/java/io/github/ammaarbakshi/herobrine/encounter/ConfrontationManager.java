package io.github.ammaarbakshi.herobrine.encounter;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.Vec3;

import io.github.ammaarbakshi.herobrine.entity.HerobrineEntity;
import io.github.ammaarbakshi.herobrine.mirror.CounterPlan;
import io.github.ammaarbakshi.herobrine.registry.ModEntities;
import io.github.ammaarbakshi.herobrine.sighting.SightingController;
import io.github.ammaarbakshi.herobrine.sighting.SpawnFinder;

/** Starts and tracks Mirror fights: at most one per player. */
public final class ConfrontationManager {
	/** How far from the player he appears. */
	public static final double ARRIVAL_DISTANCE = 10.0;

	private static final Map<UUID, HerobrineEntity> ACTIVE = new HashMap<>();

	private ConfrontationManager() {
	}

	/** The player's live boss, if a fight is on. */
	public static @Nullable HerobrineEntity activeBoss(ServerPlayer player) {
		HerobrineEntity boss = ACTIVE.get(player.getUUID());
		if (boss != null && (boss.isRemoved() || boss.mode() != HerobrineEntity.Mode.BOSS)) {
			ACTIVE.remove(player.getUUID());
			return null;
		}
		return boss;
	}

	/** Spawns the Mirror in front of {@code player}. Returns null if there's nowhere to stand. */
	public static @Nullable HerobrineEntity summon(ServerPlayer player, CounterPlan plan) {
		HerobrineEntity existing = activeBoss(player);
		if (existing != null) {
			existing.discard();
		}
		SightingController.end(player.getUUID());
		BlockPos feet = findArrivalSpot(player);
		if (feet == null) {
			return null;
		}
		ServerLevel level = player.level();
		HerobrineEntity boss = ModEntities.HEROBRINE.create(level, EntitySpawnReason.EVENT);
		if (boss == null) {
			return null;
		}
		Vec3 at = Vec3.atBottomCenterOf(feet);
		float yaw = SightingController.yawToward(at, player.position());
		boss.snapTo(at.x, at.y, at.z, yaw, 0.0F);
		boss.setYHeadRot(yaw);
		boss.setYBodyRot(yaw);
		boss.startBoss(player, plan);
		if (!level.addFreshEntity(boss)) {
			return null;
		}
		ACTIVE.put(player.getUUID(), boss);
		return boss;
	}

	/** In front of the player first, then fanning out to the sides and behind. */
	private static @Nullable BlockPos findArrivalSpot(ServerPlayer player) {
		int[] offsets = {0, 30, -30, 60, -60, 90, -90, 135, -135, 180};
		for (double distance : new double[] {ARRIVAL_DISTANCE, ARRIVAL_DISTANCE * 0.6}) {
			for (int offset : offsets) {
				double angle = Math.toRadians(player.getYRot() + offset);
				int x = Mth.floor(player.getX() - Math.sin(angle) * distance);
				int z = Mth.floor(player.getZ() + Math.cos(angle) * distance);
				BlockPos feet = SpawnFinder.findStandable(player.level(), x, z, player.getBlockY(), 4, false);
				if (feet != null) {
					return feet;
				}
			}
		}
		return null;
	}
}

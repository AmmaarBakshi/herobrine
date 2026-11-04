package io.github.ammaarbakshi.herobrine.entity.ai;

import java.util.EnumSet;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

import io.github.ammaarbakshi.herobrine.config.HerobrineConfig.Griefing;
import io.github.ammaarbakshi.herobrine.entity.HerobrineEntity;
import io.github.ammaarbakshi.herobrine.haunt.WorldGuard;
import io.github.ammaarbakshi.herobrine.mirror.TacticPlanner;
import io.github.ammaarbakshi.herobrine.mirror.TacticPlanner.Escape;
import io.github.ammaarbakshi.herobrine.mirror.TacticPlanner.Tactic;
import io.github.ammaarbakshi.herobrine.sighting.SpawnFinder;

/**
 * Backs off at the player's own retreat point, the way they escape: running, pillaring up
 * (handed to {@link PillarGoal}) or pearling away. Pearls are simulated (a throw, a short
 * flight, a teleport) so no projectile can land somewhere unexpected.
 */
public final class MirrorRetreatGoal extends MirrorGoal {
	private static final int RUN_TICKS = 70;
	private static final double SAFE_DISTANCE = 14.0;
	private static final int PEARL_FLIGHT_TICKS = 12;

	private Escape escape = Escape.RUN;
	private int ticks;
	private @Nullable Vec3 pearlDestination;

	public MirrorRetreatGoal(HerobrineEntity herobrine) {
		super(herobrine, EnumSet.of(Flag.MOVE, Flag.JUMP));
	}

	@Override
	public boolean canUse() {
		MirrorBrain brain = brain();
		return brain != null && !brain.pillaring() && active(Tactic.RETREAT);
	}

	@Override
	public void start() {
		MirrorBrain brain = brain();
		ServerPlayer target = target();
		ticks = 0;
		pearlDestination = null;
		if (brain == null || target == null) {
			return;
		}
		if (herobrine.isUsingItem()) {
			herobrine.stopUsingItem();
		}
		boolean canPillar = WorldGuard.allows(Griefing.NATURAL_ONLY) && PillarGoal.hasRoom(herobrine);
		escape = TacticPlanner.escape(brain.habits(), canPillar, rng());
		switch (escape) {
			case PILLAR -> brain.setPillaring(true);
			case PEARL -> {
				pearlDestination = findPearlSpot(target);
				if (pearlDestination == null) {
					escape = Escape.RUN;
					run(target);
				} else {
					throwPearl();
				}
			}
			case RUN -> run(target);
		}
	}

	@Override
	public void stop() {
		herobrine.setSprinting(false);
		herobrine.getNavigation().stop();
		MirrorBrain brain = brain();
		if (brain != null && !brain.pillaring() && brain.tactic() == Tactic.RETREAT) {
			finish(brain);
		}
	}

	@Override
	public void tick() {
		MirrorBrain brain = brain();
		ServerPlayer target = target();
		if (brain == null || target == null || brain.pillaring()) {
			return;
		}
		ticks++;
		if (escape == Escape.PEARL) {
			herobrine.getNavigation().stop();
			if (ticks >= PEARL_FLIGHT_TICKS && pearlDestination != null) {
				land(pearlDestination);
				finish(brain);
			}
			return;
		}
		if (herobrine.getNavigation().isDone() && herobrine.distanceTo(target) < SAFE_DISTANCE) {
			run(target);
		}
		if (ticks >= RUN_TICKS || herobrine.distanceTo(target) >= SAFE_DISTANCE) {
			finish(brain);
		}
	}

	private void finish(MirrorBrain brain) {
		brain.onRetreatEnded(now());
		brain.setTactic(Tactic.MELEE);
	}

	private void run(ServerPlayer target) {
		Vec3 away = DefaultRandomPos.getPosAway(herobrine, 16, 7, target.position());
		if (away != null) {
			herobrine.setSprinting(true);
			herobrine.getNavigation().moveTo(away.x, away.y, away.z, 1.35);
		}
	}

	private @Nullable Vec3 findPearlSpot(ServerPlayer target) {
		if (!(herobrine.level() instanceof ServerLevel level)) {
			return null;
		}
		Vec3 away = herobrine.position().subtract(target.position()).multiply(1.0, 0.0, 1.0);
		if (away.lengthSqr() < 1.0E-4) {
			away = new Vec3(1.0, 0.0, 0.0);
		}
		away = away.normalize();
		for (int attempt = 0; attempt < 6; attempt++) {
			double angle = (herobrine.getRandom().nextDouble() - 0.5) * Math.PI * 0.6;
			Vec3 dir = away.yRot((float) angle);
			double reach = 12.0 + herobrine.getRandom().nextDouble() * 8.0;
			int x = Mth.floor(herobrine.getX() + dir.x * reach);
			int z = Mth.floor(herobrine.getZ() + dir.z * reach);
			BlockPos feet = SpawnFinder.findStandable(level, x, z, herobrine.getBlockY(), 6, false);
			if (feet != null) {
				return Vec3.atBottomCenterOf(feet);
			}
		}
		return null;
	}

	private void throwPearl() {
		herobrine.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
		herobrine.level().playSound(null, herobrine.getX(), herobrine.getY(), herobrine.getZ(), SoundEvents.ENDER_PEARL_THROW,
				herobrine.getSoundSource(), 0.5F, 0.4F / (herobrine.getRandom().nextFloat() * 0.4F + 0.8F));
	}

	private void land(Vec3 at) {
		if (!(herobrine.level() instanceof ServerLevel level)) {
			return;
		}
		level.sendParticles(ParticleTypes.PORTAL, herobrine.getX(), herobrine.getY(0.5), herobrine.getZ(), 24, 0.3, 0.6, 0.3, 0.4);
		herobrine.teleportTo(at.x, at.y, at.z);
		herobrine.resetFallDistance();
		level.sendParticles(ParticleTypes.PORTAL, at.x, at.y + 1.0, at.z, 32, 0.3, 0.6, 0.3, 0.4);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_TELEPORT, herobrine.getSoundSource(), 1.0F, 1.0F);
	}
}

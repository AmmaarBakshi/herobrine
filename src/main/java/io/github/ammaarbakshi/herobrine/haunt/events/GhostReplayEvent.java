package io.github.ammaarbakshi.herobrine.haunt.events;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;

import io.github.ammaarbakshi.herobrine.entity.HerobrineEntity;
import io.github.ammaarbakshi.herobrine.haunt.HauntPhase;
import io.github.ammaarbakshi.herobrine.haunt.PlayerTrail;
import io.github.ammaarbakshi.herobrine.sighting.LookDetector;
import io.github.ammaarbakshi.herobrine.sighting.SightingController;

/**
 * Herobrine appears about 30 blocks away and silently walks the player's own last 10 seconds:
 * same turns, same jumps, same crouches, shifted to where he stands. Then he is gone.
 *
 * <p>The movement is played back from a snapshot of {@link PlayerTrail}, interpolated between
 * the 2-tick samples so it looks smooth. He has no AI and no collision while doing it, so the
 * replay can't get stuck on terrain that differs from the player's.
 */
final class GhostReplayEvent implements HauntEvent {
	private static final double MIN_DISTANCE = 26;
	private static final double MAX_DISTANCE = 34;
	private static final double VANISH_RANGE = 12;
	/** About one second of looking straight at him. */
	private static final int STARE_TICKS = 20;

	@Override
	public String id() {
		return "ghost_replay";
	}

	@Override
	public HauntPhase minPhase() {
		return HauntPhase.SIGHTINGS;
	}

	@Override
	public int weight(EventContext ctx) {
		return ctx.trail().hasFullReplay() && !SightingController.isActive(ctx.player()) ? 6 : 0;
	}

	@Override
	public boolean fire(EventContext ctx) {
		ServerPlayer player = ctx.player();
		if (!ctx.trail().hasFullReplay() || SightingController.isActive(player)) {
			return false;
		}
		BlockPos anchor = SightingController.findHiddenSpot(player, MIN_DISTANCE, MAX_DISTANCE);
		if (anchor == null) {
			return false;
		}
		PlayerTrail.Sample[] samples = ctx.trail().replay();
		PlayerTrail.Sample first = samples[0];
		Vec3 offset = Vec3.atBottomCenterOf(anchor).subtract(first.x(), first.y(), first.z());
		int duration = (samples.length - 1) * PlayerTrail.SAMPLE_INTERVAL;
		HerobrineEntity ghost = SightingController.spawnScripted(player, Vec3.atBottomCenterOf(anchor), first.yaw(),
				duration + 1, new Replay(samples, offset));
		if (ghost == null) {
			return false;
		}
		ghost.setNoAi(true);
		ghost.setNoGravity(true);
		ghost.noPhysics = true;
		return true;
	}

	private static final class Replay implements SightingController.Script {
		private final PlayerTrail.Sample[] samples;
		private final Vec3 offset;
		private int stareTicks;

		Replay(PlayerTrail.Sample[] samples, Vec3 offset) {
			this.samples = samples;
			this.offset = offset;
		}

		@Override
		public boolean tick(HerobrineEntity ghost, ServerPlayer player) {
			if (ghost.distanceToSqr(player) < VANISH_RANGE * VANISH_RANGE) {
				return false;
			}
			stareTicks = LookDetector.isStaringAt(player, ghost) ? stareTicks + 1 : 0;
			if (stareTicks >= STARE_TICKS) {
				return false;
			}
			double t = (double) ghost.ageInMode() / PlayerTrail.SAMPLE_INTERVAL;
			int i = Mth.floor(t);
			if (i >= samples.length - 1) {
				return false;
			}
			float f = (float) (t - i);
			PlayerTrail.Sample a = samples[i];
			PlayerTrail.Sample b = samples[i + 1];
			ghost.setPos(Mth.lerp(f, a.x(), b.x()) + offset.x, Mth.lerp(f, a.y(), b.y()) + offset.y,
					Mth.lerp(f, a.z(), b.z()) + offset.z);
			ghost.setDeltaMovement(Vec3.ZERO);
			float yaw = Mth.rotLerp(f, a.yaw(), b.yaw());
			ghost.setYRot(yaw);
			ghost.setYHeadRot(yaw);
			ghost.setYBodyRot(yaw);
			ghost.setXRot(Mth.lerp(f, a.pitch(), b.pitch()));
			ghost.setPose(a.crouching() ? Pose.CROUCHING : Pose.STANDING);
			return true;
		}
	}
}

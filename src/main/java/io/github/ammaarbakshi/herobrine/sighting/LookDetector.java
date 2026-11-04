package io.github.ammaarbakshi.herobrine.sighting;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * "Can the player see this?" checks, ordered cheapest first: a dot product against the view
 * vector rejects almost everything, so the (comparatively expensive) raycast only runs for
 * points the player is actually facing.
 */
public final class LookDetector {
	/** cos(~14 degrees): "looking straight at him". */
	public static final double STARE_DOT = 0.97;
	/**
	 * cos(~63 degrees). Wider than the vertical FOV and close to half the horizontal FOV on a
	 * 16:9 screen at default settings: anything beyond this is safely off-screen.
	 */
	public static final double FOV_DOT = 0.45;

	private LookDetector() {
	}

	/** Cosine of the angle between the player's view and the direction to {@code point}. */
	public static double viewDot(ServerPlayer player, Vec3 point) {
		Vec3 eye = player.getEyePosition();
		Vec3 to = point.subtract(eye);
		double length = to.length();
		if (length < 1.0E-6) {
			return 1.0;
		}
		return player.getViewVector(1.0F).dot(to.scale(1.0 / length));
	}

	/** True if no block blocks the straight line from the player's eyes to {@code point}. */
	public static boolean hasLineOfSight(ServerPlayer player, Vec3 point) {
		Vec3 eye = player.getEyePosition();
		HitResult hit = player.level().clip(new ClipContext(eye, point, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player));
		return hit.getType() == HitResult.Type.MISS;
	}

	public static boolean inFieldOfView(ServerPlayer player, Vec3 point) {
		return viewDot(player, point) > FOV_DOT;
	}

	/** On screen and unobstructed. */
	public static boolean canSee(ServerPlayer player, Vec3 point) {
		return inFieldOfView(player, point) && hasLineOfSight(player, point);
	}

	/** Looking directly at the entity's head or body, with nothing in between. */
	public static boolean isStaringAt(ServerPlayer player, Entity entity) {
		Vec3 head = entity.getEyePosition();
		Vec3 body = entity.position().add(0, entity.getBbHeight() * 0.5, 0);
		if (viewDot(player, head) > STARE_DOT) {
			return hasLineOfSight(player, head);
		}
		if (viewDot(player, body) > STARE_DOT) {
			return hasLineOfSight(player, body);
		}
		return false;
	}

	/** Visible anywhere on screen: head, body or feet in view and unobstructed. */
	public static boolean canSeeEntity(ServerPlayer player, Entity entity) {
		Vec3 head = entity.getEyePosition();
		Vec3 body = entity.position().add(0, entity.getBbHeight() * 0.5, 0);
		Vec3 feet = entity.position().add(0, 0.1, 0);
		return canSee(player, head) || canSee(player, body) || canSee(player, feet);
	}
}

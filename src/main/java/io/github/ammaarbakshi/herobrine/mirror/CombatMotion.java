package io.github.ammaarbakshi.herobrine.mirror;

/**
 * Turns a movement sample into habit observations. Pure maths on plain numbers (horizontal
 * plane only), so the recorder stays thin and this stays testable.
 *
 * <p>Minecraft's horizontal axes: yaw 0 faces +Z, and facing +Z your right hand points to -X.
 * So for a facing direction {@code (fx, fz)} the right-hand direction is {@code (-fz, fx)}.
 */
public final class CombatMotion {
	/** Ticks between movement samples. */
	public static final int SAMPLE_TICKS = 5;
	/** Sideways speed (blocks/tick) that counts as deliberately strafing. */
	public static final double STRAFE_MIN_SPEED = 0.05;
	/** Roughly walking speed: strafing this fast or faster is full intensity. */
	public static final double STRAFE_FULL_SPEED = 0.2;
	/** Speed away from the target (blocks/tick) that counts as backing off. */
	public static final double RETREAT_SPEED = 0.1;
	/** Healthy players keeping their distance aren't retreating; only count it below this. */
	public static final double RETREAT_MAX_HEALTH = 0.8;

	/** One sample's movement relative to the target. */
	public record Motion(double lateral, double radial, double distance) {
		/** Sideways speed in blocks/tick; positive = to the player's right. */
		public double lateralSpeed() {
			return lateral / SAMPLE_TICKS;
		}

		/** Speed along the line to the target in blocks/tick; positive = closing in. */
		public double radialSpeed() {
			return radial / SAMPLE_TICKS;
		}

		public boolean strafing() {
			return Math.abs(lateralSpeed()) >= STRAFE_MIN_SPEED;
		}

		/** +1 strafing right, -1 strafing left, 0 not strafing. */
		public double strafeDirection() {
			return strafing() ? Math.signum(lateral) : 0.0;
		}

		/** 0 (standing still sideways) to 1 (strafing at walking speed or faster). */
		public double strafeIntensity() {
			return Math.min(1.0, Math.abs(lateralSpeed()) / STRAFE_FULL_SPEED);
		}

		public boolean backingOff() {
			return radialSpeed() <= -RETREAT_SPEED;
		}
	}

	private CombatMotion() {
	}

	/**
	 * @param fromX,fromZ player position at the previous sample
	 * @param toX,toZ     player position now
	 * @param targetX,targetZ target position now
	 */
	public static Motion of(double fromX, double fromZ, double toX, double toZ, double targetX, double targetZ) {
		double ax = targetX - toX;
		double az = targetZ - toZ;
		double distance = Math.sqrt(ax * ax + az * az);
		double dx = toX - fromX;
		double dz = toZ - fromZ;
		if (distance < 1.0E-6) {
			return new Motion(0.0, 0.0, 0.0);
		}
		double fx = ax / distance;
		double fz = az / distance;
		double lateral = dx * -fz + dz * fx;
		double radial = dx * fx + dz * fz;
		return new Motion(lateral, radial, distance);
	}
}

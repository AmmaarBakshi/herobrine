package io.github.ammaarbakshi.herobrine.entity.ai;

import net.minecraft.world.entity.ai.control.MoveControl;

import io.github.ammaarbakshi.herobrine.entity.HerobrineEntity;

/**
 * Vanilla strafing is hard-wired to a quarter of walking speed (skeletons shuffle). Players
 * strafe at nearly full speed, and so must their mirror.
 */
public final class HerobrineMoveControl extends MoveControl<HerobrineEntity> {
	public HerobrineMoveControl(HerobrineEntity herobrine) {
		super(herobrine);
	}

	/** Like {@link #strafe} but at {@code speed} times the movement speed attribute. */
	public void strafeAt(float forwards, float right, double speed) {
		strafe(forwards, right);
		this.speedModifier = speed;
	}
}

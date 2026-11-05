package io.github.ammaarbakshi.herobrine.haunt.events;

import io.github.ammaarbakshi.herobrine.behaviour.AppearanceController;
import io.github.ammaarbakshi.herobrine.haunt.HauntPhase;
import io.github.ammaarbakshi.herobrine.sighting.SightingController;

/**
 * Lets the director pick sightings the same way as signs. The work is in {@link SightingController}.
 * They share the observer's appearance cooldown, so the haunt never makes him common.
 */
final class SightingEvents {
	private SightingEvents() {
	}

	private static boolean free(EventContext ctx) {
		return !SightingController.isActive(ctx.player()) && AppearanceController.offCooldown(ctx.player());
	}

	static final class Distant implements HauntEvent {
		@Override
		public String id() {
			return "sighting";
		}

		@Override
		public Category category() {
			return Category.SIGHTING;
		}

		@Override
		public HauntPhase minPhase() {
			return HauntPhase.SIGHTINGS;
		}

		@Override
		public int weight(EventContext ctx) {
			return free(ctx) ? 16 : 0;
		}

		@Override
		public boolean fire(EventContext ctx) {
			return SightingController.spawnDistant(ctx.player());
		}
	}

	static final class Stalker implements HauntEvent {
		@Override
		public String id() {
			return "stalk";
		}

		@Override
		public Category category() {
			return Category.SIGHTING;
		}

		@Override
		public HauntPhase minPhase() {
			return HauntPhase.STALKING;
		}

		@Override
		public int weight(EventContext ctx) {
			return free(ctx) ? 10 : 0;
		}

		@Override
		public boolean fire(EventContext ctx) {
			return SightingController.spawnStalker(ctx.player());
		}
	}

	static final class Jumpscare implements HauntEvent {
		@Override
		public String id() {
			return "jumpscare";
		}

		@Override
		public Category category() {
			return Category.SIGHTING;
		}

		@Override
		public HauntPhase minPhase() {
			return HauntPhase.STALKING;
		}

		@Override
		public int weight(EventContext ctx) {
			boolean allowed = ctx.config().jumpscares && ctx.haunt().canJumpscare(ctx.day(), ctx.config().maxJumpscaresPerDay);
			return allowed && free(ctx) ? 3 : 0;
		}

		@Override
		public boolean fire(EventContext ctx) {
			if (!ctx.config().jumpscares || !SightingController.spawnJumpscare(ctx.player())) {
				return false;
			}
			ctx.haunt().recordJumpscare(ctx.day());
			return true;
		}
	}
}

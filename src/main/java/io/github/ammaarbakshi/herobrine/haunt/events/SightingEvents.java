package io.github.ammaarbakshi.herobrine.haunt.events;

import io.github.ammaarbakshi.herobrine.haunt.HauntPhase;
import io.github.ammaarbakshi.herobrine.sighting.SightingController;

/** Lets the director pick sightings the same way as signs. The work is in {@link SightingController}. */
final class SightingEvents {
	private SightingEvents() {
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
			return SightingController.isActive(ctx.player()) ? 0 : 16;
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
			return SightingController.isActive(ctx.player()) ? 0 : 10;
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
			return allowed && !SightingController.isActive(ctx.player()) ? 3 : 0;
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

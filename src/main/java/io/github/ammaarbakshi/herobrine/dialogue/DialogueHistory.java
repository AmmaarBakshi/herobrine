package io.github.ammaarbakshi.herobrine.dialogue;

/**
 * What has already been said to one player, as the line picker needs it. Times are overworld game
 * time in ticks, which is saved with the world, so nothing repeats just because the world was
 * reloaded. {@code -1} means never.
 */
public interface DialogueHistory {
	long lastSaid(String lineId);

	/** Among the last few lines spoken. */
	boolean recentlySaid(String lineId);

	long categoryLastSaid(String category);

	long triggerLastFired(String trigger);

	/** Last time he said anything at all. */
	long lastSpoke();

	DialogueHistory NONE = new DialogueHistory() {
		@Override
		public long lastSaid(String lineId) {
			return -1;
		}

		@Override
		public boolean recentlySaid(String lineId) {
			return false;
		}

		@Override
		public long categoryLastSaid(String category) {
			return -1;
		}

		@Override
		public long triggerLastFired(String trigger) {
			return -1;
		}

		@Override
		public long lastSpoke() {
			return -1;
		}
	};
}

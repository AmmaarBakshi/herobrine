package io.github.ammaarbakshi.herobrine.registry;

/** Entity types (Herobrine himself). */
public final class ModEntities {
	private ModEntities() {
	}

	/** Forces class loading so static registrations run during mod init, not lazily mid-game. */
	public static void init() {
	}
}

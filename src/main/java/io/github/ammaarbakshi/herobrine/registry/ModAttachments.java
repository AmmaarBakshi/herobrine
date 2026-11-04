package io.github.ammaarbakshi.herobrine.registry;

/** Per-player persistent data (haunt level, behaviour profile, encounter history) via the Fabric Data Attachment API. */
public final class ModAttachments {
	private ModAttachments() {
	}

	/** Forces class loading so static registrations run during mod init, not lazily mid-game. */
	public static void init() {
	}
}

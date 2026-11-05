package io.github.ammaarbakshi.herobrine.memory;

import java.util.Locale;

/** The fights he comments on but never joins. Ids match the vanilla entity ids. */
public enum BossKind {
	WITHER,
	ENDER_DRAGON,
	WARDEN;

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}
}

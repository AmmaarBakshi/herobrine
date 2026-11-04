package io.github.ammaarbakshi.herobrine.registry;

/** Single place that fixes registration order: sounds before the items/entities that use them. */
public final class ModRegistries {
	private ModRegistries() {
	}

	public static void init() {
		ModSounds.init();
		ModEntities.init();
		ModItems.init();
		ModAttachments.init();
	}
}

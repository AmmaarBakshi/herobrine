package io.github.ammaarbakshi.herobrine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.resources.Identifier;

import net.fabricmc.api.ModInitializer;

/**
 * Common (both-sides) entrypoint. Everything here must be safe on a dedicated server,
 * so nothing in this package may reference classes from {@code src/client}.
 */
public final class HerobrineMod implements ModInitializer {
	public static final String MOD_ID = "herobrine";
	public static final Logger LOGGER = LoggerFactory.getLogger("Herobrine");

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		LOGGER.info("He is watching.");
	}
}

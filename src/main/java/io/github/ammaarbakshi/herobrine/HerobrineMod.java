package io.github.ammaarbakshi.herobrine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.resources.Identifier;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import io.github.ammaarbakshi.herobrine.behaviour.AppearanceController;
import io.github.ammaarbakshi.herobrine.behaviour.DialogueController;
import io.github.ammaarbakshi.herobrine.behaviour.ObservationController;
import io.github.ammaarbakshi.herobrine.command.HerobrineCommand;
import io.github.ammaarbakshi.herobrine.config.HerobrineConfig;
import io.github.ammaarbakshi.herobrine.encounter.ConfrontationManager;
import io.github.ammaarbakshi.herobrine.encounter.ShrineDetector;
import io.github.ammaarbakshi.herobrine.haunt.HauntDirector;
import io.github.ammaarbakshi.herobrine.haunt.HauntScheduler;
import io.github.ammaarbakshi.herobrine.haunt.events.HauntEvents;
import io.github.ammaarbakshi.herobrine.mirror.PlayerBehaviorRecorder;
import io.github.ammaarbakshi.herobrine.net.ModNetworking;
import io.github.ammaarbakshi.herobrine.perf.Perf;
import io.github.ammaarbakshi.herobrine.registry.ModRegistries;
import io.github.ammaarbakshi.herobrine.sighting.SightingController;

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
		HerobrineConfig.load();
		ModRegistries.init();
		ModNetworking.init();
		SightingController.init();
		HauntScheduler.init();
		HauntEvents.init();
		HauntDirector.init();
		PlayerBehaviorRecorder.init();
		ConfrontationManager.init();
		ShrineDetector.init();
		DialogueController.init();
		AppearanceController.init();
		ObservationController.init();
		Perf.init();
		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) ->
				HerobrineCommand.register(dispatcher));
		LOGGER.info("He is watching.");
	}
}

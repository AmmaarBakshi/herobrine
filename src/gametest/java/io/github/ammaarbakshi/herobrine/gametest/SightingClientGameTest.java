package io.github.ammaarbakshi.herobrine.gametest;

import net.minecraft.server.level.ServerPlayer;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import io.github.ammaarbakshi.herobrine.entity.HerobrineEntity;
import io.github.ammaarbakshi.herobrine.haunt.HauntLevel;
import io.github.ammaarbakshi.herobrine.sighting.LookDetector;

/**
 * M3: a forced sighting appears far away and never in view; staring at him for about a second
 * makes him vanish (and raises the haunt level); walking up to him or waiting it out also does.
 * The stalker vanishes when the player turns around; the jumpscare lasts ten ticks.
 */
public final class SightingClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		if (!GameTests.selected(this)) {
			return;
		}
		try (TestSingleplayerContext world = GameTests.createWorld(context)) {
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("tp @p 0.5 -60 0.5 180 0");
			context.waitTicks(5);

			// --- Distant sighting: spawns out of view, at 24-60 blocks.
			world.getServer().runCommand("herobrine sighting @p");
			context.waitTick();
			world.getServer().runOnServer(server -> {
				GameTests.check(GameTests.herobrines(server).size() == 1, "sighting did not spawn");
				HerobrineEntity h = GameTests.herobrines(server).getFirst();
				ServerPlayer p = GameTests.player(server);
				double d = Math.sqrt(h.distanceToSqr(p));
				GameTests.check(d >= 23 && d <= 61, "sighting at wrong distance: " + d);
				GameTests.check(!LookDetector.inFieldOfView(p, h.getEyePosition()), "sighting spawned inside the field of view");
			});
			GameTests.screenshot(context, "m3-before-turning");

			// Turn to face him: visible for a moment, then gone within 1.5 s.
			GameTests.asPlayer(world, "tp @s ~ ~ ~ facing entity @e[type=herobrine:herobrine,limit=1] feet");
			context.waitTicks(4);
			GameTests.screenshot(context, "m3-spotted");
			context.waitTicks(40);
			world.getServer().runOnServer(server -> {
				GameTests.check(GameTests.herobrines(server).isEmpty(), "sighting did not vanish after being stared at");
				double haunt = HauntLevel.get(GameTests.player(server));
				GameTests.check(haunt >= 5, "staring did not raise the haunt level: " + haunt);
			});

			// --- Walking within 16 blocks makes him vanish, even without looking.
			world.getServer().runCommand("tp @p 0.5 -60 0.5 180 0");
			world.getServer().runCommand("herobrine sighting @p");
			context.waitTick();
			world.getServer().runCommand("execute as @p at @e[type=herobrine:herobrine,limit=1] run tp @s ~3 ~ ~3");
			context.waitTicks(3);
			world.getServer().runOnServer(server ->
					GameTests.check(GameTests.herobrines(server).isEmpty(), "sighting did not vanish when approached"));

			// --- Ignored, he leaves after 20 seconds.
			world.getServer().runCommand("tp @p 0.5 -60 0.5 180 0");
			world.getServer().runCommand("herobrine sighting @p");
			context.waitTicks(380);
			world.getServer().runOnServer(server ->
					GameTests.check(GameTests.herobrines(server).size() == 1, "sighting left too early"));
			context.waitTicks(30);
			world.getServer().runOnServer(server ->
					GameTests.check(GameTests.herobrines(server).isEmpty(), "sighting did not time out"));

			// --- Hitting him (e.g. with an arrow) makes him vanish.
			world.getServer().runCommand("herobrine sighting @p");
			context.waitTick();
			world.getServer().runCommand("damage @e[type=herobrine:herobrine,limit=1] 1 minecraft:arrow");
			context.waitTick();
			world.getServer().runOnServer(server ->
					GameTests.check(GameTests.herobrines(server).isEmpty(), "sighting did not vanish when hit"));

			// --- Stalker: right behind; gone the moment the player turns around.
			world.getServer().runCommand("tp @p 0.5 -60 0.5 180 0");
			world.getServer().runCommand("herobrine sighting @p stalk");
			context.waitTicks(5);
			world.getServer().runOnServer(server -> {
				GameTests.check(GameTests.herobrines(server).size() == 1, "stalker did not spawn");
				HerobrineEntity h = GameTests.herobrines(server).getFirst();
				ServerPlayer p = GameTests.player(server);
				double d = Math.sqrt(h.distanceToSqr(p));
				GameTests.check(d >= 5 && d <= 11, "stalker at wrong distance: " + d);
				GameTests.check(LookDetector.viewDot(p, h.getEyePosition()) < 0, "stalker is not behind the player");
			});
			world.getServer().runCommand("tp @p 0.5 -60 0.5 0 0");
			context.waitTicks(3);
			world.getServer().runOnServer(server ->
					GameTests.check(GameTests.herobrines(server).isEmpty(), "stalker did not vanish when the player turned"));

			// --- Jumpscare: in your face for ten ticks.
			world.getServer().runCommand("herobrine sighting @p jumpscare");
			context.waitTicks(2);
			world.getServer().runOnServer(server -> {
				GameTests.check(GameTests.herobrines(server).size() == 1, "jumpscare did not spawn");
				double d = Math.sqrt(GameTests.herobrines(server).getFirst().distanceToSqr(GameTests.player(server)));
				GameTests.check(d < 3.5, "jumpscare is not in the player's face: " + d);
			});
			GameTests.screenshot(context, "m3-jumpscare");
			context.waitTicks(12);
			world.getServer().runOnServer(server ->
					GameTests.check(GameTests.herobrines(server).isEmpty(), "jumpscare did not end"));
		}
	}
}

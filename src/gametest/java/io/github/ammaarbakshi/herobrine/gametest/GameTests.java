package io.github.ammaarbakshi.herobrine.gametest;

import java.util.List;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import io.github.ammaarbakshi.herobrine.entity.HerobrineEntity;
import io.github.ammaarbakshi.herobrine.registry.ModEntities;

/** Small helpers shared by the client game tests. */
final class GameTests {
	private GameTests() {
	}

	/**
	 * True unless {@code -Dherobrine.gametest=A,B} is set and names none of the test's class
	 * name fragments (e.g. "Sighting" selects SightingClientGameTest).
	 */
	static boolean selected(Object test) {
		String filter = System.getProperty("herobrine.gametest", "");
		if (filter.isBlank()) {
			return true;
		}
		String name = test.getClass().getSimpleName();
		for (String part : filter.split(",")) {
			if (!part.isBlank() && name.contains(part.trim())) {
				return true;
			}
		}
		return false;
	}

	static TestSingleplayerContext createWorld(ClientGameTestContext context) {
		TestSingleplayerContext world = context.worldBuilder()
				.adjustSettings(settings -> settings.setAllowCommands(true))
				.create();
		world.getConnection().waitForChunksRender();
		return world;
	}

	/** Runs a command as the (single) player, at their position, with op permissions. */
	static void asPlayer(TestSingleplayerContext world, String command) {
		world.getServer().runCommand("execute as @p at @s run " + command);
	}

	static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	static List<? extends HerobrineEntity> herobrines(MinecraftServer server) {
		return player(server).level().getEntities(ModEntities.HEROBRINE, e -> true);
	}

	/**
	 * The player kills him with one blow. His shield goes down first: a raised shield blocks
	 * the whole hit, however big, and a shield user's mirror raises his often.
	 */
	static void killBoss(ServerPlayer player, HerobrineEntity boss) {
		boss.stopUsingItem();
		boss.hurtServer(player.level(), player.damageSources().playerAttack(player), 1000.0F);
	}

	/** Angle in degrees between where his head points and the direction to {@code point}. */
	static double headAngleTo(HerobrineEntity herobrine, Vec3 point) {
		double yaw = Math.toRadians(herobrine.getYHeadRot());
		Vec3 facing = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
		Vec3 to = point.subtract(herobrine.position());
		to = new Vec3(to.x, 0, to.z).normalize();
		return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, facing.dot(to)))));
	}

	/** Screenshot with the chat cleared so command feedback doesn't cover the scene. */
	static void screenshot(ClientGameTestContext context, String name) {
		context.runOnClient(client -> client.gui.hud.getChat().clearMessages(false));
		context.waitTick();
		context.takeScreenshot(name);
	}

	static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}

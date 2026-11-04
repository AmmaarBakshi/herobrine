package io.github.ammaarbakshi.herobrine.gametest;

import net.minecraft.world.item.ItemStack;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import io.github.ammaarbakshi.herobrine.entity.HerobrineEntity;
import io.github.ammaarbakshi.herobrine.registry.ModItems;

/**
 * M2: the spawn egg works through real client input, he turns his head to stare at the player,
 * and his eyes are visible in a sealed, pitch-black room (screenshots for eyeballing).
 */
public final class EntityClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = GameTests.createWorld(context)) {
			GameTests.asPlayer(world, "time set noon");
			GameTests.asPlayer(world, "tp @s ~ ~ ~ 0 55");
			world.getServer().runOnServer(server ->
					GameTests.player(server).getInventory().setItem(0, new ItemStack(ModItems.HEROBRINE_SPAWN_EGG)));
			context.runOnClient(client -> client.player.getInventory().setSelectedSlot(0));
			world.getConnection().waitForClientboundPackets();
			context.waitTicks(2);

			context.getInput().pressKey(options -> options.keyUse);
			context.waitTicks(10);

			world.getServer().runOnServer(server ->
					GameTests.check(GameTests.herobrines(server).size() == 1, "spawn egg did not spawn exactly one Herobrine"));

			// Walk the player to the side; he should keep tracking them with his head.
			GameTests.asPlayer(world, "tp @s ~4 ~ ~-3");
			context.waitTicks(60);
			world.getServer().runOnServer(server -> {
				HerobrineEntity herobrine = GameTests.herobrines(server).getFirst();
				double angle = GameTests.headAngleTo(herobrine, GameTests.player(server).position());
				GameTests.check(angle < 25, "Herobrine is not staring at the player (head off by " + angle + " degrees)");
			});

			// "facing" aims from the feet, so aim at his feet to keep the camera level.
			GameTests.asPlayer(world, "tp @s ~ ~ ~ facing entity @e[type=herobrine:herobrine,limit=1] feet");
			world.getConnection().waitForChunksRender();
			context.waitTicks(20);
			GameTests.screenshot(context, "m2-daylight");

			// Seal the player in a black box at midnight with Herobrine 3 blocks ahead, eye to eye:
			// only his eyes should be visible.
			world.getServer().runCommand("time set midnight");
			world.getServer().runCommand("kill @e[type=herobrine:herobrine]");
			world.getServer().runOnServer(server -> {
				var p = GameTests.player(server).blockPosition();
				server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), String.format(
						"fill %d %d %d %d %d %d minecraft:black_concrete hollow",
						p.getX() - 6, p.getY() - 1, p.getZ() - 6, p.getX() + 6, p.getY() + 5, p.getZ() + 6));
				server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), String.format(
						"summon herobrine:herobrine %d.5 %d %d.5", p.getX(), p.getY(), p.getZ() + 3));
				server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), String.format(
						"tp @p %d.5 %d %d.5 facing %d.5 %d %d.5", p.getX(), p.getY(), p.getZ(),
						p.getX(), p.getY(), p.getZ() + 3));
			});
			world.getConnection().waitForChunksRender();
			context.waitTicks(60);
			GameTests.screenshot(context, "m2-darkness");
		}
	}
}

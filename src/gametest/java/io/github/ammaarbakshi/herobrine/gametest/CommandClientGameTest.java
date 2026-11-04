package io.github.ammaarbakshi.herobrine.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/** Boots a singleplayer world and checks that {@code /herobrine} is registered and runs for an op. */
public final class CommandClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder()
				.adjustSettings(settings -> settings.setAllowCommands(true))
				.create()) {
			world.getConnection().waitForChunksRender();

			world.getServer().runOnServer(server -> {
				if (server.getCommands().getDispatcher().getRoot().getChild("herobrine") == null) {
					throw new AssertionError("/herobrine was not registered");
				}
			});

			context.runOnClient(client -> client.player.connection.sendCommand("herobrine status"));
			world.getConnection().waitForServerboundPackets();
			world.getConnection().waitForClientboundPackets();
			context.waitTicks(5);
			context.takeScreenshot("m1-herobrine-status");
		}
	}
}

package io.github.ammaarbakshi.herobrine.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/** M1: {@code /herobrine} is registered and {@code status} runs for an op. */
public final class CommandClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = GameTests.createWorld(context)) {
			world.getServer().runOnServer(server -> GameTests.check(
					server.getCommands().getDispatcher().getRoot().getChild("herobrine") != null,
					"/herobrine was not registered"));

			context.runOnClient(client -> client.player.connection.sendCommand("herobrine status"));
			world.getConnection().waitForServerboundPackets();
			world.getConnection().waitForClientboundPackets();
			context.waitTicks(5);
			context.takeScreenshot("m1-herobrine-status");
		}
	}
}

package io.github.ammaarbakshi.herobrine.gametest;

import java.util.Objects;
import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignTextSlot;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import io.github.ammaarbakshi.herobrine.config.HerobrineConfig;
import io.github.ammaarbakshi.herobrine.config.HerobrineConfig.Griefing;
import io.github.ammaarbakshi.herobrine.entity.HerobrineEntity;
import io.github.ammaarbakshi.herobrine.haunt.HauntDirector;
import io.github.ammaarbakshi.herobrine.haunt.HauntDirector.ForceResult;
import io.github.ammaarbakshi.herobrine.haunt.PlayerTrail;
import io.github.ammaarbakshi.herobrine.haunt.WorldEditLog;
import io.github.ammaarbakshi.herobrine.haunt.events.HauntEvents;
import io.github.ammaarbakshi.herobrine.registry.ModAttachments;

/**
 * M4: every sign event can be forced; nothing changes near a bed or chest; nothing changes in
 * view; {@code /herobrine undo} restores the blocks. Scenes are built behind the player (who
 * faces south, +Z) so they're out of view, then checked by counting blocks.
 */
public final class HauntClientGameTest implements FabricClientGameTest {
	private static final int Y = -60; // flat world surface (feet level)

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!GameTests.selected(this)) {
			return;
		}
		try (TestSingleplayerContext world = GameTests.createWorld(context)) {
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("gamerule max_block_modifications 1000000");
			home(world);
			context.waitTicks(5);
			// No random events while we test: only forced ones.
			world.getServer().runOnServer(server -> trail(server).nextEventTick = Long.MAX_VALUE);

			torchThief(context, world);
			tunnel(context, world);
			pyramid(context, world);
			leaflessTree(context, world);
			sign(context, world);
			soundsAndChat(context, world);
			ghost(context, world);
		}
	}

	private static void home(TestSingleplayerContext world) {
		world.getServer().runCommand("tp @p 0.5 " + Y + " 0.5 0 0");
	}

	// --- Torch thief ------------------------------------------------------------------------

	private static void torchThief(ClientGameTestContext context, TestSingleplayerContext world) {
		world.getServer().runCommand("fill -2 " + Y + " -20 2 " + Y + " -20 minecraft:torch");
		world.getServer().runOnServer(server -> trail(server).rememberCave(new BlockPos(0, Y, -20)));

		// A chest within 16 blocks protects them.
		world.getServer().runCommand("setblock 0 " + Y + " -26 minecraft:chest");
		expect(world, "torch_thief", ForceResult.NOTHING_FOUND, "torch thief ignored the chest");
		check(world, server -> count(server, -2, Y, -20, 2, Y, -20, Blocks.TORCH) == 5, "torches next to a chest were taken");
		world.getServer().runCommand("setblock 0 " + Y + " -26 minecraft:air");

		// Griefing NONE blocks it entirely.
		check(world, server -> withGriefing(Griefing.NONE, () -> force(server, "torch_thief")) == ForceResult.DISABLED,
				"torch thief ran with griefing NONE");

		// Through the real command this time.
		GameTests.asPlayer(world, "herobrine sign torch_thief");
		check(world, server -> count(server, -2, Y, -20, 2, Y, -20, Blocks.TORCH) == 2, "torch thief did not take 3 torches");
		world.getServer().runCommand("herobrine undo");
		check(world, server -> count(server, -2, Y, -20, 2, Y, -20, Blocks.TORCH) == 5, "undo did not bring the torches back");
		world.getServer().runCommand("fill -2 " + Y + " -20 2 " + Y + " -20 minecraft:air");
	}

	// --- Tunnel -----------------------------------------------------------------------------

	private static void tunnel(ClientGameTestContext context, TestSingleplayerContext world) {
		// A solid block of stone behind the player, with a remembered mining spot in the middle.
		world.getServer().runCommand("fill -30 -60 -55 30 -50 -10 minecraft:stone");
		world.getServer().runOnServer(server -> trail(server).rememberMined(new BlockPos(0, -55, -32)));

		// A whole bed (head, then foot): a lone half would break itself on the next block update.
		world.getServer().runCommand("setblock 0 -49 -33 minecraft:red_bed[facing=north,part=head]");
		world.getServer().runCommand("setblock 0 -49 -32 minecraft:red_bed[facing=north,part=foot]");
		check(world, server -> server.overworld().getBlockState(new BlockPos(0, -49, -32)).is(BlockTags.BEDS), "test bed was not placed");
		expect(world, "tunnel", ForceResult.NOTHING_FOUND, "tunnel ignored the bed");
		check(world, server -> count(server, -30, -59, -55, 30, -50, -10, Blocks.AIR) == 0, "a tunnel was dug next to a bed");
		world.getServer().runCommand("fill 0 -49 -33 0 -49 -32 minecraft:air");

		expect(world, "tunnel", ForceResult.FIRED, "tunnel did not fire");
		int carved = world.getServer().computeOnServer(server -> count(server, -30, -59, -55, 30, -50, -10, Blocks.AIR));
		GameTests.check(carved >= 40 && carved <= 120 && carved % 4 == 0, "tunnel is not a 2x2, 10-30 long: " + carved + " blocks");

		// Stand in it, looking down its length, for the record.
		BlockPos inside = world.getServer().computeOnServer(server -> tunnelFloor(server));
		world.getServer().runCommand("tp @p " + inside.getX() + ".5 " + inside.getY() + " " + inside.getZ() + ".5");
		context.waitTicks(10);
		GameTests.screenshot(context, "m4-tunnel");
		home(world);

		world.getServer().runCommand("herobrine undo");
		check(world, server -> count(server, -30, -59, -55, 30, -50, -10, Blocks.AIR) == 0, "undo did not fill the tunnel");
		world.getServer().runCommand("fill -30 -60 -55 30 -50 -10 minecraft:air");
	}

	// --- Sand pyramid -----------------------------------------------------------------------

	private static void pyramid(ClientGameTestContext context, TestSingleplayerContext world) {
		// Pyramids go 40-100 blocks out (+16 protected radius), past the test's 5-chunk view distance.
		world.getServer().runCommand("forceload add -116 -116 116 116");
		world.getServer().waitFor(server -> server.overworld().getChunkSource().getChunkNow(-8, -8) != null
				&& server.overworld().getChunkSource().getChunkNow(7, 7) != null
				&& server.overworld().getChunkSource().getChunkNow(-8, 7) != null
				&& server.overworld().getChunkSource().getChunkNow(7, -8) != null, 600);
		for (int z = -100; z <= 100; z += 25) {
			world.getServer().runCommand("fillbiome -100 -64 " + z + " 100 -49 " + Math.min(100, z + 24) + " minecraft:desert");
		}
		context.waitTicks(5);
		check(world, server -> server.overworld().getBiome(new BlockPos(50, Y, 0)).is(Biomes.DESERT), "fillbiome did not make a desert");
		expect(world, "sand_pyramid", ForceResult.FIRED, "sand pyramid did not fire");
		int sand = world.getServer().computeOnServer(server -> count(server, -110, Y, -110, 110, Y + 4, 110, Blocks.SAND));
		GameTests.check(sand == 49 + 25 + 9 + 1, "pyramid is not 7x7 stepped: " + sand + " sand");
		GameTests.asPlayer(world, "tp @s ~ ~ ~ facing " + sandCenter(world));
		context.waitTicks(10);
		GameTests.screenshot(context, "m4-pyramid");
		home(world);
		world.getServer().runCommand("herobrine undo");
		check(world, server -> count(server, -110, Y, -110, 110, Y + 4, 110, Blocks.SAND) == 0, "undo did not remove the pyramid");
		world.getServer().runCommand("forceload remove all");
	}

	private static String sandCenter(TestSingleplayerContext world) {
		BlockPos top = world.getServer().computeOnServer(server -> firstOf(server, -110, Y + 3, -110, 110, Y + 3, 110, Blocks.SAND));
		return top.getX() + ".5 " + Y + " " + top.getZ() + ".5";
	}

	// --- Leafless tree ----------------------------------------------------------------------

	private static void leaflessTree(ClientGameTestContext context, TestSingleplayerContext world) {
		for (int x = -12; x <= 12; x += 6) {
			for (int z = -30; z <= -12; z += 6) {
				world.getServer().runCommand("place feature minecraft:oak " + x + " " + Y + " " + z);
			}
		}
		int before = world.getServer().computeOnServer(HauntClientGameTest::leaves);
		GameTests.check(before > 100, "test trees did not grow: " + before + " leaves");
		expect(world, "leafless_tree", ForceResult.FIRED, "leafless tree did not fire");
		int after = world.getServer().computeOnServer(HauntClientGameTest::leaves);
		GameTests.check(after < before - 20, "no tree lost its leaves: " + before + " -> " + after);

		world.getServer().runCommand("tp @p 0.5 " + Y + " 0.5 180 -15");
		context.waitTicks(10);
		GameTests.screenshot(context, "m4-leafless-tree");
		home(world);
		world.getServer().runCommand("herobrine undo");
		check(world, server -> leaves(server) == before, "undo did not restore the leaves");
		world.getServer().runCommand("fill -16 " + Y + " -34 16 " + (Y + 10) + " -8 minecraft:air");
	}

	private static int leaves(MinecraftServer server) {
		int n = 0;
		ServerLevel level = server.overworld();
		for (BlockPos pos : BlockPos.betweenClosed(-16, Y, -34, 16, Y + 10, -8)) {
			if (level.getBlockState(pos).is(BlockTags.LEAVES)) {
				n++;
			}
		}
		return n;
	}

	// --- Sign -------------------------------------------------------------------------------

	private static void sign(ClientGameTestContext context, TestSingleplayerContext world) {
		// Places they walked through earlier, behind them; straight ahead is in view, so not there.
		world.getServer().runOnServer(server -> {
			PlayerTrail trail = trail(server);
			trail.rememberPath(new BlockPos(0, Y, -40));
			trail.rememberPath(new BlockPos(0, Y, -30));
			trail.rememberPath(new BlockPos(0, Y, -20));
		});
		expect(world, "sign", ForceResult.FIRED, "sign did not fire");
		BlockPos at = world.getServer().computeOnServer(server -> firstSign(server));
		GameTests.check(at != null && at.getZ() < -10, "sign missing or placed in view: " + at);
		world.getServer().runOnServer(server -> {
			SignBlockEntity sign = (SignBlockEntity) server.overworld().getBlockEntity(at);
			GameTests.check(sign != null && sign.isWaxed(), "sign is not waxed");
			Component line = sign.getText(SignTextSlot.FRONT).getMessages(false).get(1);
			GameTests.check(line.getContents() instanceof TranslatableContents t && t.getKey().startsWith("herobrine.sign."),
					"sign text is not from the lang file: " + line);
		});
		world.getServer().runCommand("tp @p " + at.getX() + ".5 " + Y + " " + (at.getZ() + 3) + ".5 180 20");
		context.waitTicks(10);
		GameTests.screenshot(context, "m4-sign");
		home(world);
		world.getServer().runCommand("herobrine undo");
		check(world, server -> firstSign(server) == null, "undo did not remove the sign");
	}

	private static BlockPos firstSign(MinecraftServer server) {
		return firstOf(server, -40, Y - 2, -60, 40, Y + 4, 20, Blocks.OAK_SIGN);
	}

	// --- Sounds, fake chat ------------------------------------------------------------------

	private static void soundsAndChat(ClientGameTestContext context, TestSingleplayerContext world) {
		int edits = world.getServer().computeOnServer(server -> WorldEditLog.get(server).size());
		expect(world, "ambient_sound", ForceResult.FIRED, "ambient sound did not fire");
		context.waitTicks(80); // let the scheduled footsteps / door close play out
		expect(world, "fake_chat", ForceResult.FIRED, "fake chat did not fire");
		check(world, server -> !ModAttachments.haunt(GameTests.player(server)).canFakeChat(
				server.overworld().getOverworldClockTime() / 24000L), "fake chat is not limited to once a day");
		check(world, server -> WorldEditLog.get(server).size() == edits, "sounds or chat changed blocks");
		context.waitTicks(2);
		context.takeScreenshot("m4-fake-chat"); // chat kept on purpose: the grey join line
	}

	// --- Ghost replay -----------------------------------------------------------------------

	private static void ghost(ClientGameTestContext context, TestSingleplayerContext world) {
		// Walk and turn for a bit over the 10 s replay window, so there is movement to re-enact.
		for (int i = 0; i < 20; i++) {
			GameTests.asPlayer(world, "tp @s ~0.4 ~ ~0.2 ~9 ~");
			context.waitTicks(11);
		}
		expect(world, "ghost_replay", ForceResult.FIRED, "ghost replay did not fire");
		world.getServer().runOnServer(server -> {
			GameTests.check(GameTests.herobrines(server).size() == 1, "ghost did not appear");
			HerobrineEntity ghost = GameTests.herobrines(server).getFirst();
			double d = Math.sqrt(ghost.distanceToSqr(GameTests.player(server)));
			GameTests.check(ghost.mode() == HerobrineEntity.Mode.GHOST && d >= 24 && d <= 36, "ghost at " + d + " in " + ghost.mode());
		});
		double start = world.getServer().computeOnServer(server -> GameTests.herobrines(server).getFirst().getX());
		context.waitTicks(60);
		double later = world.getServer().computeOnServer(server -> GameTests.herobrines(server).isEmpty()
				? Double.NaN : GameTests.herobrines(server).getFirst().getX());
		GameTests.check(Math.abs(later - start) > 0.5, "ghost did not re-enact the movement: " + start + " -> " + later);
		GameTests.asPlayer(world, "tp @s ~ ~ ~ facing entity @e[type=herobrine:herobrine,limit=1] feet");
		context.waitTicks(3);
		GameTests.screenshot(context, "m4-ghost");
		context.waitTicks(PlayerTrail.REPLAY_SAMPLES * PlayerTrail.SAMPLE_INTERVAL);
		world.getServer().runOnServer(server -> GameTests.check(GameTests.herobrines(server).isEmpty(), "ghost did not vanish"));
	}

	// --- Helpers ----------------------------------------------------------------------------

	@FunctionalInterface
	private interface ServerCheck {
		boolean test(MinecraftServer server);
	}

	private static void check(TestSingleplayerContext world, ServerCheck check, String message) {
		boolean ok = world.getServer().computeOnServer(check::test);
		GameTests.check(ok, message);
	}

	private static void expect(TestSingleplayerContext world, String event, ForceResult expected, String message) {
		ForceResult result = world.getServer().computeOnServer(server -> force(server, event));
		GameTests.check(result == expected, message + " (got " + result + ")");
	}

	private static PlayerTrail trail(MinecraftServer server) {
		return ModAttachments.trail(GameTests.player(server));
	}

	private static ForceResult force(MinecraftServer server, String id) {
		ServerPlayer player = GameTests.player(server);
		return HauntDirector.force(player, Objects.requireNonNull(HauntEvents.byId(id), id));
	}

	private static <T> T withGriefing(Griefing griefing, Supplier<T> action) {
		HerobrineConfig config = HerobrineConfig.get();
		Griefing previous = config.griefing;
		config.griefing = griefing;
		try {
			return action.get();
		} finally {
			config.griefing = previous;
		}
	}

	private static int count(MinecraftServer server, int x1, int y1, int z1, int x2, int y2, int z2, Block block) {
		int n = 0;
		ServerLevel level = server.overworld();
		for (BlockPos pos : BlockPos.betweenClosed(x1, y1, z1, x2, y2, z2)) {
			if (level.getBlockState(pos).is(block)) {
				n++;
			}
		}
		return n;
	}

	private static BlockPos firstOf(MinecraftServer server, int x1, int y1, int z1, int x2, int y2, int z2, Block block) {
		ServerLevel level = server.overworld();
		for (BlockPos pos : BlockPos.betweenClosed(x1, y1, z1, x2, y2, z2)) {
			if (level.getBlockState(pos).is(block)) {
				return pos.immutable();
			}
		}
		return null;
	}

	/** An air block with air above and stone below: somewhere to stand inside the tunnel. */
	private static BlockPos tunnelFloor(MinecraftServer server) {
		ServerLevel level = server.overworld();
		for (BlockPos pos : BlockPos.betweenClosed(-30, -60, -55, 30, -50, -10)) {
			if (level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir() && !level.getBlockState(pos.below()).isAir()) {
				return pos.immutable();
			}
		}
		throw new AssertionError("no tunnel floor");
	}
}

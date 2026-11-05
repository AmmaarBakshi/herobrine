package io.github.ammaarbakshi.herobrine.gametest;

import java.util.Comparator;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;

import io.github.ammaarbakshi.herobrine.client.screen.MirrorProfileScreen;
import io.github.ammaarbakshi.herobrine.config.HerobrineConfig;
import io.github.ammaarbakshi.herobrine.encounter.ConfrontationManager;
import io.github.ammaarbakshi.herobrine.encounter.ConfrontationManager.StartResult;
import io.github.ammaarbakshi.herobrine.encounter.ConfrontationManager.Trigger;
import io.github.ammaarbakshi.herobrine.encounter.EncounterHistory;
import io.github.ammaarbakshi.herobrine.entity.HerobrineEntity;
import io.github.ammaarbakshi.herobrine.entity.HerobrineEntity.Mode;
import io.github.ammaarbakshi.herobrine.haunt.HauntLevel;
import io.github.ammaarbakshi.herobrine.registry.ModAttachments;
import io.github.ammaarbakshi.herobrine.registry.ModItems;
import io.github.ammaarbakshi.herobrine.sighting.SightingController;

/**
 * M7: the whole confrontation loop. A lit shrine summons him with the arrival sequence; killing
 * him drops the Mirror Shard, grants "Know Thyself" and records a win; dying to him brings him
 * to the respawn point and records a loss. The natural trigger fires at high haunt with a
 * confident profile, and nothing fires on Peaceful. History survives a restart.
 */
public final class ConfrontationClientGameTest implements FabricClientGameTest {
	private static final BlockPos NETHERRACK = new BlockPos(2, -59, 2);

	private interface ServerCheck {
		boolean test(MinecraftServer server);
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!GameTests.selected(this)) {
			return;
		}
		TestWorldSave save;
		try (TestSingleplayerContext world = GameTests.createWorld(context)) {
			save = world.getWorldSave();
			setUp(world);
			context.waitTicks(5);

			shrineStartsTheFight(context, world);
			winningDropsTheShard(context, world);
			theShardShowsTheProfile(context, world);
			losingBringsTheAftermath(context, world);
			naturalTriggerFires(context, world);
			peacefulMeansNoConfrontation(context, world);
		}

		try (TestSingleplayerContext world = save.open()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runOnServer(server -> {
				EncounterHistory history = ModAttachments.encounters(GameTests.player(server));
				GameTests.check(history.total() == 3 && history.wins() == 2,
						"encounter history didn't survive the restart: " + history.total() + " fights, " + history.wins() + " won");
			});
		}
	}

	private static void setUp(TestSingleplayerContext world) {
		world.getServer().runCommand("time set midnight");
		world.getServer().runCommand("difficulty normal");
		world.getServer().runCommand("tp @p 0.5 -60 0.5 0 0");
		world.getServer().runCommand("effect give @p minecraft:resistance infinite 4 true");
		world.getServer().runCommand("effect give @p minecraft:saturation infinite 0 true");
		world.getServer().runCommand("herobrine profile @p preset sword_spammer");
		world.getServer().runOnServer(server -> ModAttachments.trail(GameTests.player(server)).nextEventTick = Long.MAX_VALUE);
	}

	// --- Shrine -------------------------------------------------------------------------------

	private static void shrineStartsTheFight(ClientGameTestContext context, TestSingleplayerContext world) {
		world.getServer().runCommand("fill 1 -60 1 3 -60 3 minecraft:gold_block");
		world.getServer().runCommand("setblock 2 -59 2 minecraft:netherrack");
		world.getServer().runCommand("setblock 3 -59 2 minecraft:redstone_torch");
		world.getServer().runCommand("setblock 1 -59 2 minecraft:redstone_torch");
		world.getServer().runCommand("setblock 2 -59 3 minecraft:redstone_torch");
		world.getServer().runCommand("setblock 2 -59 1 minecraft:redstone_torch");
		world.getServer().runCommand("item replace entity @p weapon.mainhand with minecraft:flint_and_steel");
		context.waitTicks(2);

		// Light it the way a player does: through the real use-item-on-block path.
		world.getServer().runOnServer(server -> {
			ServerPlayer player = GameTests.player(server);
			BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(NETHERRACK).add(0, 0.5, 0), Direction.UP, NETHERRACK, false);
			player.gameMode.useItemOn(player, player.level(), player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
		});
		GameTests.check(waitUntil(context, world, 5, server -> ConfrontationManager.isFighting(GameTests.player(server))),
				"lighting the shrine didn't start a confrontation");
		GameTests.check(world.getServer().computeOnServer(server -> GameTests.player(server).hasEffect(MobEffects.DARKNESS)),
				"no darkness as he arrives");
		GameTests.check(waitUntil(context, world, 60, server -> ConfrontationManager.activeBoss(GameTests.player(server)) != null),
				"he never arrived after the shrine was lit");
		world.getServer().runOnServer(server -> {
			HerobrineEntity boss = ConfrontationManager.activeBoss(GameTests.player(server));
			double distance = boss.distanceTo(GameTests.player(server));
			GameTests.check(distance > 4.0 && distance < 14.0, "he arrived " + distance + " blocks away, not ~10");
			GameTests.check(boss.getMainHandItem().is(Items.DIAMOND_SWORD),
					"he isn't holding the player's weapon: " + boss.getMainHandItem());
		});
		context.waitTicks(30);
		context.runOnClient(ConfrontationClientGameTest::lookAtHerobrine);
		GameTests.screenshot(context, "m7-boss-bar");
	}

	// --- Win ----------------------------------------------------------------------------------

	private static void winningDropsTheShard(ClientGameTestContext context, TestSingleplayerContext world) {
		world.getServer().runOnServer(server -> {
			ServerPlayer player = GameTests.player(server);
			GameTests.killBoss(player, ConfrontationManager.activeBoss(player));
		});
		context.waitTicks(5);
		world.getServer().runOnServer(server -> {
			ServerPlayer player = GameTests.player(server);
			GameTests.check(!ConfrontationManager.isFighting(player), "fight still on after he died");
			boolean shard = !player.level().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(24),
					item -> item.getItem().is(ModItems.MIRROR_SHARD)).isEmpty();
			GameTests.check(shard, "no Mirror Shard dropped");
			AdvancementHolder advancement = server.getAdvancements().get(ConfrontationManager.KNOW_THYSELF);
			GameTests.check(advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone(),
					"Know Thyself not granted");
			EncounterHistory history = ModAttachments.encounters(player);
			GameTests.check(history.total() == 1 && history.wins() == 1, "win not recorded");
			// Set exactly on the win, but the haunt director may have run since: on this flat world
			// the player is "underground" at midnight, which adds 0.025 a second while alone.
			double haunt = HauntLevel.get(player);
			GameTests.check(haunt >= ConfrontationManager.WIN_HAUNT_LEVEL && haunt < ConfrontationManager.WIN_HAUNT_LEVEL + 0.5,
					"haunt level after a win: " + haunt);
		});
		context.waitTicks(25); // let the death animation finish
	}

	private static void theShardShowsTheProfile(ClientGameTestContext context, TestSingleplayerContext world) {
		world.getServer().runCommand("kill @e[type=minecraft:item]");
		world.getServer().runCommand("item replace entity @p weapon.mainhand with herobrine:mirror_shard");
		world.getServer().runCommand("tp @p 0.5 -60 -6.5 180 -30");
		context.waitTicks(5);
		context.getInput().pressKey(options -> options.keyUse);
		context.waitFor(client -> client.gui.screen() instanceof MirrorProfileScreen, 40);
		context.takeScreenshot("m7-mirror-shard");
		context.runOnClient(client -> client.gui.setScreen(null));
		context.waitTicks(2);
	}

	// --- Loss ---------------------------------------------------------------------------------

	private static void losingBringsTheAftermath(ClientGameTestContext context, TestSingleplayerContext world) {
		world.getServer().runCommand("tp @p 0.5 -60 -6.5 180 0");
		world.getServer().runOnServer(server -> GameTests.check(
				ConfrontationManager.begin(GameTests.player(server), Trigger.COMMAND) == StartResult.STARTED, "summon failed"));
		GameTests.check(waitUntil(context, world, 60, server -> ConfrontationManager.activeBoss(GameTests.player(server)) != null),
				"he never arrived for the second fight");
		world.getServer().runCommand("kill @p");
		context.waitTicks(5);
		world.getServer().runOnServer(server -> {
			ServerPlayer player = GameTests.player(server);
			GameTests.check(!ConfrontationManager.isFighting(player), "fight still on after the player died");
			GameTests.check(GameTests.herobrines(server).stream().noneMatch(h -> h.mode() == Mode.BOSS && !h.isRemoved()),
					"the boss stayed after winning");
			EncounterHistory history = ModAttachments.encounters(player);
			GameTests.check(history.total() == 2 && history.losses() == 1, "loss not recorded");
			GameTests.check(HauntLevel.get(player) == ConfrontationManager.LOSS_HAUNT_LEVEL, "haunt level after a loss: " + HauntLevel.get(player));
		});
		context.waitTicks(15);
		context.clickScreenButton("deathScreen.respawn");
		GameTests.check(waitUntil(context, world, 40, server -> GameTests.herobrines(server).stream()
				.anyMatch(h -> h.mode() == Mode.AFTERMATH && h.distanceTo(GameTests.player(server)) < 5.0)),
				"he didn't wait at the respawn point");
		context.waitTicks(25);
		context.runOnClient(ConfrontationClientGameTest::lookAtHerobrine);
		context.takeScreenshot("m7-aftermath"); // chat kept: his one line
		GameTests.check(waitUntil(context, world, 120, server -> GameTests.herobrines(server).isEmpty()),
				"he never left the respawn point");
	}

	// --- Natural trigger and Peaceful ---------------------------------------------------------

	private static void naturalTriggerFires(ClientGameTestContext context, TestSingleplayerContext world) {
		world.getServer().runCommand("effect give @p minecraft:resistance infinite 4 true");
		world.getServer().runOnServer(server -> {
			ServerPlayer player = GameTests.player(server);
			HerobrineConfig.get().hauntSpeedMultiplier = 50.0;
			// Same in-game day as the fights above: lift the once-a-day limit for the test.
			EncounterHistory history = ModAttachments.encounters(player);
			GameTests.check(!history.naturalAllowed(0), "natural cooldown not in effect after a fight today");
			HauntLevel.set(player, 95.0);
			ModAttachments.trail(player).nextEventTick = Long.MAX_VALUE;
		});
		world.getServer().runCommand("time set midnight");
		// The cooldown is per day: move to the next night.
		world.getServer().runCommand("time add 24000");
		GameTests.check(waitUntil(context, world, 100, server -> ConfrontationManager.isFighting(GameTests.player(server))),
				"no natural confrontation at haunt 95, full confidence, alone at night");
		GameTests.check(waitUntil(context, world, 60, server -> ConfrontationManager.activeBoss(GameTests.player(server)) != null),
				"natural confrontation never arrived");
		world.getServer().runOnServer(server -> {
			ServerPlayer player = GameTests.player(server);
			GameTests.killBoss(player, ConfrontationManager.activeBoss(player));
		});
		context.waitTicks(30);
		world.getServer().runCommand("kill @e[type=minecraft:item]");
	}

	private static void peacefulMeansNoConfrontation(ClientGameTestContext context, TestSingleplayerContext world) {
		world.getServer().runCommand("difficulty peaceful");
		world.getServer().runCommand("time add 24000");
		world.getServer().runOnServer(server -> {
			ServerPlayer player = GameTests.player(server);
			HauntLevel.set(player, 95.0);
			GameTests.check(ConfrontationManager.begin(player, Trigger.SHRINE) == StartResult.PEACEFUL, "a fight started on Peaceful");
		});
		GameTests.check(!waitUntil(context, world, 60, server -> ConfrontationManager.isFighting(GameTests.player(server))),
				"natural confrontation on Peaceful");
		// Signs and sightings still happen on Peaceful.
		world.getServer().runOnServer(server -> GameTests.check(SightingController.spawnDistant(GameTests.player(server)),
				"no sighting on Peaceful"));
		context.waitTicks(10);
		world.getServer().runOnServer(server -> {
			GameTests.check(!GameTests.herobrines(server).isEmpty(), "sighting despawned on Peaceful");
			SightingController.end(GameTests.player(server).getUUID());
			// Leave nothing armed for the restart check.
			HerobrineConfig.get().hauntSpeedMultiplier = 1.0;
			HauntLevel.set(GameTests.player(server), 0.0);
		});
		world.getServer().runCommand("difficulty normal");
	}

	// --- Helpers ------------------------------------------------------------------------------

	private static void lookAtHerobrine(Minecraft client) {
		LocalPlayer player = client.player;
		client.level.getEntitiesOfClass(HerobrineEntity.class, player.getBoundingBox().inflate(48)).stream()
				.min(Comparator.comparingDouble(h -> h.distanceToSqr(player)))
				.ifPresent(h -> {
					Vec3 d = h.position().add(0, h.getBbHeight() * 0.6, 0).subtract(player.getEyePosition());
					float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0F;
					float pitch = (float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
					player.setYRot(yaw);
					player.setXRot(pitch);
					player.setYHeadRot(yaw);
				});
	}

	private static boolean waitUntil(ClientGameTestContext context, TestSingleplayerContext world, int maxTicks, ServerCheck check) {
		for (int tick = 0; tick < maxTicks; tick++) {
			if (world.getServer().computeOnServer(check::test)) {
				return true;
			}
			context.waitTick();
		}
		return false;
	}
}

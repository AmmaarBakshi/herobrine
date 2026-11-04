package io.github.ammaarbakshi.herobrine.gametest;

import java.util.Locale;
import java.util.Properties;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import io.github.ammaarbakshi.herobrine.client.hud.DebugOverlay;
import io.github.ammaarbakshi.herobrine.client.screen.MirrorProfileScreen;
import io.github.ammaarbakshi.herobrine.config.HerobrineConfig;
import io.github.ammaarbakshi.herobrine.encounter.ConfrontationManager;
import io.github.ammaarbakshi.herobrine.encounter.EncounterHistory;
import io.github.ammaarbakshi.herobrine.entity.HerobrineEntity;
import io.github.ammaarbakshi.herobrine.mirror.ProfileView;
import io.github.ammaarbakshi.herobrine.net.ConfrontationCuePayload;
import io.github.ammaarbakshi.herobrine.net.DebugProfilePayload;
import io.github.ammaarbakshi.herobrine.net.MirrorProfilePayload;
import io.github.ammaarbakshi.herobrine.perf.Perf;
import io.github.ammaarbakshi.herobrine.perf.PerfMeter;
import io.github.ammaarbakshi.herobrine.registry.ModAttachments;
import io.github.ammaarbakshi.herobrine.registry.ModEntities;
import io.github.ammaarbakshi.herobrine.registry.ModItems;

/**
 * M9: the mod over a real network. A dedicated server runs in the same JVM and the client
 * connects to it, so every payload, entity, effect and boss bar goes through the protocol:
 * the mod handshake opens all channels, the debug HUD gets live values, the Mirror Shard
 * opens its screen, and a full confrontation (arrival to win) shows up on the client.
 * Leaving mid-fight abandons it, and rejoining finds the history intact and no fight.
 */
public final class DedicatedServerClientGameTest implements FabricClientGameTest {
	private static final int WARM_UP_ZOMBIES = 24;
	private static final int WARM_UP_TICKS = 300;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!GameTests.selected(this)) {
			return;
		}
		Properties properties = new Properties();
		properties.setProperty("difficulty", "normal");
		try (TestDedicatedServerContext server = context.worldBuilder().createServer(properties)) {
			UUID playerId;
			try (TestDedicatedServerConnection connection = server.connect()) {
				connection.waitForChunksRender();
				playerId = server.computeOnServer(s -> GameTests.player(s).getUUID());
				setUp(server);
				context.waitTicks(5);

				channelsAreOpen(server);
				theShardOpensItsScreen(context, server);
				aConfrontationReachesTheClient(context, server, connection);
				startAFightAndLeave(context, server);
			}

			// The player left mid-fight: it's abandoned and he's gone.
			server.waitFor(s -> !ConfrontationManager.isFighting(playerId), 20);
			server.runOnServer(s -> GameTests.check(
					s.overworld().getEntities(ModEntities.HEROBRINE, HerobrineEntity::isAlive).isEmpty(),
					"Herobrine stayed behind after the player left"));

			try (TestDedicatedServerConnection connection = server.connect()) {
				connection.waitForChunksRender();
				context.waitTicks(5);
				server.runOnServer(s -> {
					ServerPlayer player = GameTests.player(s);
					GameTests.check(!ConfrontationManager.isFighting(player), "a fight resumed on rejoin");
					EncounterHistory history = ModAttachments.encounters(player);
					GameTests.check(history.total() == 1 && history.wins() == 1,
							"history after rejoining: " + history.total() + " fights, " + history.wins() + " won");
				});
			}
		} finally {
			HerobrineConfig.get().debugOverlay = false;
		}
	}

	private static void setUp(TestDedicatedServerContext server) {
		server.runCommand("time set midnight");
		server.runCommand("difficulty normal");
		server.runCommand("gamemode survival @a");
		server.runCommand("tp @a 0.5 -60 0.5 0 0");
		server.runCommand("effect give @a minecraft:resistance infinite 4 true");
		server.runCommand("effect give @a minecraft:saturation infinite 0 true");
		server.runCommand("herobrine profile @p preset sword_spammer");
		server.runOnServer(s -> ModAttachments.trail(GameTests.player(s)).nextEventTick = Long.MAX_VALUE);
	}

	/** The mod handshake: the server knows the client can take every payload. */
	private static void channelsAreOpen(TestDedicatedServerContext server) {
		server.runOnServer(s -> {
			ServerPlayer player = GameTests.player(s);
			GameTests.check(ServerPlayNetworking.canSend(player, DebugProfilePayload.TYPE), "debug channel closed");
			GameTests.check(ServerPlayNetworking.canSend(player, MirrorProfilePayload.TYPE), "mirror profile channel closed");
			GameTests.check(ServerPlayNetworking.canSend(player, ConfrontationCuePayload.TYPE), "cue channel closed");
		});
	}

	/** Use the shard on the client; the server answers with the profile and the screen opens. */
	private static void theShardOpensItsScreen(ClientGameTestContext context, TestDedicatedServerContext server) {
		server.runCommand("item replace entity @a weapon.mainhand with herobrine:mirror_shard");
		server.runCommand("tp @a 0.5 -60 0.5 0 -30");
		context.waitTicks(5);
		context.getInput().pressKey(options -> options.keyUse);
		context.waitFor(client -> client.gui.screen() instanceof MirrorProfileScreen, 40);
		GameTests.screenshot(context, "m9-dedicated-shard");
		context.runOnClient(client -> client.gui.setScreen(null));
		server.runCommand("item replace entity @a weapon.mainhand with minecraft:air");
		context.waitTicks(2);
	}

	private static void aConfrontationReachesTheClient(ClientGameTestContext context, TestDedicatedServerContext server,
			TestDedicatedServerConnection connection) {
		server.runOnServer(s -> HerobrineConfig.get().debugOverlay = true);
		server.runCommand("tp @a 0.5 -60 0.5 0 0");
		server.runCommand("herobrine summon @p");
		server.waitFor(s -> ConfrontationManager.activeBoss(GameTests.player(s)) != null, 80);
		connection.waitForClientboundEntityUpdates(ModEntities.HEROBRINE);
		context.waitTicks(10);

		context.runOnClient(client -> {
			var player = client.player;
			GameTests.check(player.hasEffect(MobEffects.DARKNESS), "the client never got the darkness");
			HerobrineEntity seen = client.level.getEntitiesOfClass(HerobrineEntity.class, player.getBoundingBox().inflate(32))
					.stream().findFirst().orElse(null);
			GameTests.check(seen != null, "the client can't see him");
			GameTests.check(seen.getMainHandItem().is(Items.DIAMOND_SWORD), "his weapon didn't sync: " + seen.getMainHandItem());
			GameTests.check(client.gui.hud.getBossOverlay().shouldDarkenScreen(), "no boss bar on the client");
		});

		// He's hostile and after them, so the recorder counts it as combat and the HUD updates.
		context.waitFor(client -> {
			ProfileView view = DebugOverlay.latest();
			return view != null && view.inCombat();
		}, 100);
		GameTests.screenshot(context, "m9-dedicated-fight");
		warmUpMobCode(context, server);
		server.runOnServer(s -> Perf.resetAll());
		context.waitTicks(200);

		server.runOnServer(s -> {
			PerfMeter.Snapshot mirror = Perf.MIRROR_AI.snapshot();
			PerfMeter.Snapshot world = Perf.WORLD.snapshot();
			PerfMeter.Snapshot recorder = Perf.RECORDER.snapshot();
			System.out.printf("[herobrine-perf] mirror AI %.2f us per boss tick (worst %.1f us) over %d ticks%n",
					mirror.averageMicros(), mirror.worstMicros(), mirror.units());
			System.out.printf("[herobrine-perf] world %.2f us per server tick (worst %.1f us) over %d ticks%n",
					world.averageMicros(), world.worstMicros(), world.units());
			System.out.printf("[herobrine-perf] recorder in a boss fight %.2f us per player-tick (worst %.1f us)%n",
					recorder.averageMicros(), recorder.worstMicros());
			GameTests.check(mirror.units() > 0, "the boss AI was never measured");
			// Warm, he measures about 250 us (twice that with the mob code cold); one boss
			// must stay well under a millisecond.
			GameTests.check(mirror.averageMicros() < 500.0, "boss AI over 0.5 ms per tick: " + mirror);
		});

		server.runOnServer(s -> {
			ServerPlayer player = GameTests.player(s);
			GameTests.killBoss(player, ConfrontationManager.activeBoss(player));
		});
		// The shard reaches the client on the ground or, if he fell within reach, already in
		// their inventory: a drop can be picked up after 10 ticks, item updates go out every 20.
		context.waitFor(client -> client.player.getInventory().contains(stack -> stack.is(ModItems.MIRROR_SHARD))
				|| !client.level.getEntitiesOfClass(ItemEntity.class, client.player.getBoundingBox().inflate(24),
						item -> item.getItem().is(ModItems.MIRROR_SHARD)).isEmpty(), 60);
		server.runOnServer(s -> {
			ServerPlayer player = GameTests.player(s);
			GameTests.check(!ConfrontationManager.isFighting(player), "fight still on after he died");
			var advancement = s.getAdvancements().get(ConfrontationManager.KNOW_THYSELF);
			GameTests.check(advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone(),
					"Know Thyself not granted on the dedicated server");
		});
		context.waitTicks(25);
		server.runCommand("kill @e[type=minecraft:item]");
		server.runCommand("clear @a herobrine:mirror_shard");
		server.runOnServer(s -> HerobrineConfig.get().debugOverlay = false);
	}

	/**
	 * The test JVM is a minute old and he is its only mob, so vanilla's mob code (goal
	 * selector, paths, movement, collisions) runs 20 times a second and is still largely
	 * interpreted: a profile of this test showed a third of his frames in the interpreter.
	 * A real server always has mobs keeping that code compiled, so let a crowd of zombies
	 * chase the player for a while first, then measure him alone.
	 */
	private static void warmUpMobCode(ClientGameTestContext context, TestDedicatedServerContext server) {
		for (int i = 0; i < WARM_UP_ZOMBIES; i++) {
			double angle = 2 * Math.PI * i / WARM_UP_ZOMBIES;
			server.runCommand(String.format(Locale.ROOT, "summon minecraft:zombie %.1f -60 %.1f",
					0.5 + 12 * Math.cos(angle), 0.5 + 12 * Math.sin(angle)));
		}
		context.waitTicks(WARM_UP_TICKS);
		// Discarded, not killed: no drops for the player to pick up.
		server.runOnServer(s -> s.overworld().getEntities(EntityTypes.ZOMBIE, zombie -> true).forEach(Entity::discard));
		context.waitTicks(20);
	}

	private static void startAFightAndLeave(ClientGameTestContext context, TestDedicatedServerContext server) {
		server.runOnServer(s -> GameTests.check(ConfrontationManager.summon(GameTests.player(s),
				ConfrontationManager.nextPlan(GameTests.player(s))) != null, "summon found no room"));
		context.waitTicks(5);
		GameTests.check(server.computeOnServer((MinecraftServer s) -> ConfrontationManager.isFighting(GameTests.player(s))),
				"the second fight didn't start");
	}
}

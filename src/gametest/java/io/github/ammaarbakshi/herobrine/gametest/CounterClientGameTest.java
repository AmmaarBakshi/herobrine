package io.github.ammaarbakshi.herobrine.gametest;

import java.util.Comparator;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import io.github.ammaarbakshi.herobrine.encounter.ConfrontationManager;
import io.github.ammaarbakshi.herobrine.entity.HerobrineEntity;
import io.github.ammaarbakshi.herobrine.entity.ai.MirrorBrain;
import io.github.ammaarbakshi.herobrine.mirror.CounterPlan;
import io.github.ammaarbakshi.herobrine.mirror.CounterStrategy;
import io.github.ammaarbakshi.herobrine.mirror.Habit;
import io.github.ammaarbakshi.herobrine.registry.ModAttachments;

/**
 * M8: he adapts. A shield user's first fight has no axe; after it, the next plan counters
 * shields, and in the second fight he brings an axe and disables the raised shield (vanilla
 * mechanic). Taunts quote real habits. Each other counter is then forced (weight 1) and must
 * visibly happen: strafe lead, gap closer, heal burst, pearl follow and anti-pillar.
 */
public final class CounterClientGameTest implements FabricClientGameTest {
	private static final int FIRST_FIGHT_TICKS = 200;
	private static final int SECOND_FIGHT_MAX_TICKS = 800;

	private interface ServerCheck {
		boolean test(MinecraftServer server);
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!GameTests.selected(this)) {
			return;
		}
		try (TestSingleplayerContext world = GameTests.createWorld(context)) {
			setUp(world);
			context.waitTicks(5);

			shieldBreakerOnTheSecondFight(context, world);
			forcedCounters(context, world);
		}
	}

	private static void setUp(TestSingleplayerContext world) {
		world.getServer().runCommand("time set midnight");
		world.getServer().runCommand("difficulty normal");
		world.getServer().runCommand("tp @p 0.5 -60 0.5 0 0");
		world.getServer().runCommand("effect give @p minecraft:resistance infinite 4 true");
		world.getServer().runCommand("herobrine profile @p preset shield_turtle");
		world.getServer().runOnServer(server -> ModAttachments.trail(GameTests.player(server)).nextEventTick = Long.MAX_VALUE);
	}

	// --- Natural adaptation -------------------------------------------------------------------

	private static void shieldBreakerOnTheSecondFight(ClientGameTestContext context, TestSingleplayerContext world) {
		world.getServer().runCommand("item replace entity @p weapon.mainhand with minecraft:air");
		world.getServer().runCommand("item replace entity @p weapon.offhand with minecraft:shield");

		// Fight 1: no history yet, so no counters. He must never pull out an axe.
		CounterPlan first = world.getServer().computeOnServer(server -> ConfrontationManager.nextPlan(GameTests.player(server)));
		GameTests.check(first.active().isEmpty(), "counters before the first fight: " + first);
		summon(world, first);
		context.getInput().holdKey(options -> options.keyUse);
		for (int tick = 0; tick < FIRST_FIGHT_TICKS; tick++) {
			context.runOnClient(CounterClientGameTest::lookAtHerobrine);
			context.waitTick();
			boolean axe = world.getServer().computeOnServer(server -> boss(server).getMainHandItem().is(Items.IRON_AXE));
			GameTests.check(!axe, "axe in the first fight, before he could know about the shield");
		}
		int swings = world.getServer().computeOnServer(server -> boss(server).brain().swings);
		GameTests.check(swings > 0, "he never swung at the shield in the first fight");
		killBoss(context, world);

		// What he learned: the shield is their habit, so the next plan counters it.
		world.getServer().runOnServer(server -> {
			ServerPlayer player = GameTests.player(server);
			List<Habit> focus = ModAttachments.encounters(player).lastFocus();
			GameTests.check(focus.contains(Habit.SHIELD), "fight 1 didn't single out the shield: " + focus);
			CounterPlan next = ConfrontationManager.nextPlan(player);
			GameTests.check(next.has(CounterStrategy.SHIELD_BREAKER), "no shield breaker planned: " + next);
			GameTests.check(Math.abs(next.counterWeight() - CounterPlan.WEIGHT_PER_ENCOUNTER) < 1e-9, "weight after one fight: " + next);
		});

		// Fight 2: same player, same shield, and now he answers it with an axe.
		CounterPlan second = world.getServer().computeOnServer(server -> ConfrontationManager.nextPlan(GameTests.player(server)));
		summon(world, second);
		boolean axeSeen = false;
		boolean shieldDisabled = false;
		boolean screenshot = false;
		for (int tick = 0; tick < SECOND_FIGHT_MAX_TICKS && !(axeSeen && shieldDisabled && screenshot); tick++) {
			context.runOnClient(CounterClientGameTest::lookAtHerobrine);
			context.waitTick();
			axeSeen |= world.getServer().computeOnServer(server -> boss(server).getMainHandItem().is(Items.IRON_AXE));
			shieldDisabled |= world.getServer().computeOnServer(server -> {
				ServerPlayer player = GameTests.player(server);
				return player.getCooldowns().isOnCooldown(player.getOffhandItem());
			});
			if (shieldDisabled && !screenshot) {
				GameTests.screenshot(context, "m8-shield-broken");
				screenshot = true;
			}
		}
		context.getInput().releaseKey(options -> options.keyUse);
		GameTests.check(axeSeen, "no axe in the second fight against a shield user");
		GameTests.check(shieldDisabled, "the axe never disabled the shield");
		int taunts = world.getServer().computeOnServer(server -> ConfrontationManager.tauntsSent(GameTests.player(server)));
		GameTests.check(taunts >= 1, "no taunts during the fight");
		world.getServer().runOnServer(server -> GameTests.check(
				boss(server).brain().counterUses(CounterStrategy.SHIELD_BREAKER) >= 1, "shield breaker not counted"));
		killBoss(context, world);
		world.getServer().runCommand("item replace entity @p weapon.offhand with minecraft:air");
	}

	// --- Each counter, forced -----------------------------------------------------------------

	private static void forcedCounters(ClientGameTestContext context, TestSingleplayerContext world) {
		// Strafe lead: a strong left-strafer; he must cut them off at least once.
		world.getServer().runCommand("tp @p 0.5 -60 0.5 0 0");
		world.getServer().runCommand("herobrine profile @p preset pillar_coward");
		summon(world, forced(CounterStrategy.STRAFE_LEAD));
		GameTests.check(waitUntil(context, world, 300, server -> uses(server, CounterStrategy.STRAFE_LEAD) >= 1),
				"strafe lead never used");
		vanish(context, world);

		// Gap closer: they're 20 blocks off; he sprints in zig-zagging instead of strolling.
		world.getServer().runCommand("herobrine profile @p preset sword_spammer");
		summon(world, forced(CounterStrategy.GAP_CLOSER));
		world.getServer().runCommand("tp @p 20.5 -60 0.5");
		GameTests.check(waitUntil(context, world, 200, server -> uses(server, CounterStrategy.GAP_CLOSER) >= 1),
				"gap closer never used");
		vanish(context, world);

		// Heal burst: they sit just above their heal point; he goes all in.
		world.getServer().runCommand("tp @p 0.5 -60 0.5 0 0");
		world.getServer().runCommand("herobrine profile @p preset sword_spammer");
		summon(world, forced(CounterStrategy.HEAL_BURST));
		GameTests.check(waitUntil(context, world, 300, server -> {
			ServerPlayer player = GameTests.player(server);
			player.setHealth(player.getMaxHealth() * 0.4F); // sword spammer heals at 0.25
			return uses(server, CounterStrategy.HEAL_BURST) >= 1;
		}), "heal burst never used");
		world.getServer().runOnServer(server -> GameTests.player(server).setHealth(20.0F));
		vanish(context, world);

		// Pearl follow: they teleport 15 blocks; he pearls after them.
		summon(world, forced(CounterStrategy.PEARL_FOLLOW));
		context.waitTicks(20);
		world.getServer().runCommand("tp @p 15.5 -60 0.5");
		GameTests.check(waitUntil(context, world, 40, server -> uses(server, CounterStrategy.PEARL_FOLLOW) >= 1),
				"pearl follow never used");
		double after = world.getServer().computeOnServer(server -> boss(server).distanceTo(GameTests.player(server)));
		GameTests.check(after < 5.0, "he didn't land near them: " + after);
		vanish(context, world);

		// Anti-pillar: they stand on their own 3-block pillar; he knocks it out.
		world.getServer().runCommand("tp @p 0.5 -60 0.5 0 0");
		world.getServer().runCommand("herobrine profile @p preset pillar_coward");
		world.getServer().runCommand("fill 0 -60 0 0 -58 0 minecraft:cobblestone");
		world.getServer().runCommand("tp @p 0.5 -57 0.5");
		summon(world, forced(CounterStrategy.ANTI_PILLAR));
		GameTests.check(waitUntil(context, world, 300, server -> {
			// As if the recorder had seen them build it (a new combat episode starts clean).
			var combat = ModAttachments.combat(GameTests.player(server));
			if (combat.pillarBlocks().isEmpty()) {
				for (int y = -60; y <= -58; y++) {
					combat.rememberPillarBlock(new BlockPos(0, y, 0));
				}
			}
			return GameTests.player(server).level().getBlockState(new BlockPos(0, -58, 0)).isAir();
		}), "he never knocked out the block under them");
		context.runOnClient(CounterClientGameTest::lookAtHerobrine);
		GameTests.screenshot(context, "m8-anti-pillar");
		vanish(context, world);
		world.getServer().runCommand("fill 0 -60 0 0 -58 0 minecraft:air");
	}

	// --- Helpers ------------------------------------------------------------------------------

	private static CounterPlan forced(CounterStrategy strategy) {
		return new CounterPlan(1.0, List.of(strategy));
	}

	private static void summon(TestSingleplayerContext world, CounterPlan plan) {
		world.getServer().runOnServer(server -> GameTests.check(
				ConfrontationManager.summon(GameTests.player(server), plan) != null, "summon found no room"));
	}

	private static HerobrineEntity boss(MinecraftServer server) {
		HerobrineEntity boss = ConfrontationManager.activeBoss(GameTests.player(server));
		GameTests.check(boss != null, "no active boss");
		return boss;
	}

	private static int uses(MinecraftServer server, CounterStrategy strategy) {
		MirrorBrain brain = boss(server).brain();
		return brain == null ? 0 : brain.counterUses(strategy);
	}

	private static void killBoss(ClientGameTestContext context, TestSingleplayerContext world) {
		world.getServer().runOnServer(server -> {
			GameTests.killBoss(GameTests.player(server), boss(server));
		});
		context.waitTicks(30);
		world.getServer().runCommand("kill @e[type=minecraft:item]");
	}

	private static void vanish(ClientGameTestContext context, TestSingleplayerContext world) {
		world.getServer().runOnServer(server -> boss(server).vanish());
		context.waitTicks(2);
	}

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

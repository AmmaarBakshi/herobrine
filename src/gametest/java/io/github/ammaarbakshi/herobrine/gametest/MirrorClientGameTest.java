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
import io.github.ammaarbakshi.herobrine.mirror.Trait;
import io.github.ammaarbakshi.herobrine.mirror.TraitId;
import io.github.ammaarbakshi.herobrine.registry.ModAttachments;

/**
 * M6: the same Herobrine fights in two clearly different styles depending only on the
 * profile he mirrors. A bow kiter keeps his distance and shoots; a sword spammer stays in your
 * face and never draws a bow. Also checks the heal and pillar escapes, and pillar cleanup.
 */
public final class MirrorClientGameTest implements FabricClientGameTest {
	private static final int FIGHT_TICKS = 400;
	/** The first seconds are spent walking in from the arrival point; don't count them. */
	private static final int WARM_UP_TICKS = 80;

	private record FightStats(double averageDistance, int shots, int swings, int shieldRaises) {
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!GameTests.selected(this)) {
			return;
		}
		try (TestSingleplayerContext world = GameTests.createWorld(context)) {
			setUp(world);
			context.waitTicks(5);

			FightStats kiter = fight(context, world, "bow_kiter", "m6-bow-kiter");
			System.out.println("[herobrine-m6] bow_kiter " + kiter);
			GameTests.check(kiter.shots() >= 3, "bow kiter fired fewer than 3 arrows: " + kiter);
			GameTests.check(kiter.averageDistance() > 7.0, "bow kiter didn't keep his distance: " + kiter);

			FightStats spammer = fight(context, world, "sword_spammer", "m6-sword-spammer");
			System.out.println("[herobrine-m6] sword_spammer " + spammer);
			GameTests.check(spammer.shots() == 0, "sword spammer shot arrows: " + spammer);
			GameTests.check(spammer.swings() >= 8, "sword spammer swung fewer than 8 times: " + spammer);
			GameTests.check(spammer.averageDistance() < 4.0, "sword spammer didn't stay close: " + spammer);

			checkHeal(context, world);
			checkPillar(context, world);
		}
	}

	private static void setUp(TestSingleplayerContext world) {
		world.getServer().runCommand("time set midnight");
		world.getServer().runCommand("difficulty normal");
		world.getServer().runCommand("tp @p 0.5 -60 0.5 0 0");
		// The player is a punching bag here: only Herobrine's behaviour is under test.
		world.getServer().runCommand("effect give @p minecraft:resistance infinite 4 true");
		world.getServer().runCommand("effect give @p minecraft:saturation infinite 0 true");
		world.getServer().runCommand("effect give @p minecraft:regeneration infinite 4 true");
		world.getServer().runOnServer(server -> ModAttachments.trail(GameTests.player(server)).nextEventTick = Long.MAX_VALUE);
	}

	private static FightStats fight(ClientGameTestContext context, TestSingleplayerContext world, String preset, String screenshot) {
		world.getServer().runCommand("tp @p 0.5 -60 0.5 0 0");
		world.getServer().runCommand("herobrine profile @p preset " + preset);
		summon(world);

		double distanceSum = 0;
		int samples = 0;
		for (int tick = 0; tick < FIGHT_TICKS; tick++) {
			context.waitTick();
			if (tick < WARM_UP_TICKS) {
				continue;
			}
			distanceSum += world.getServer().computeOnServer(server -> boss(server).distanceTo(GameTests.player(server)));
			samples++;
			if (tick == FIGHT_TICKS / 2) {
				context.runOnClient(MirrorClientGameTest::lookAtHerobrine);
				GameTests.screenshot(context, screenshot);
			}
		}
		double average = distanceSum / samples;
		FightStats stats = world.getServer().computeOnServer(server -> {
			MirrorBrain brain = boss(server).brain();
			GameTests.check(brain != null, "boss has no brain");
			return new FightStats(average, brain.shots, brain.swings, brain.shieldRaises);
		});
		world.getServer().runOnServer(server -> boss(server).vanish());
		context.waitTicks(2);
		return stats;
	}

	/** Below his heal point with heals in hand, he eats and gets health back. */
	private static void checkHeal(ClientGameTestContext context, TestSingleplayerContext world) {
		world.getServer().runCommand("tp @p 0.5 -60 0.5 0 0");
		world.getServer().runCommand("herobrine profile @p preset sword_spammer");
		summon(world);
		context.waitTicks(20);
		world.getServer().runOnServer(server -> boss(server).setHealth(5.0F));
		boolean healed = waitUntil(context, world, 120, server -> {
			MirrorBrain brain = boss(server).brain();
			return brain != null && brain.healsUsed() == 1;
		});
		GameTests.check(healed, "boss at 5 HP never healed");
		context.waitTicks(5); // the heal goal hands back to melee on the next goal update
		float health = world.getServer().computeOnServer(server -> boss(server).getHealth());
		GameTests.check(health > 5.0F, "heal did not restore health: " + health);
		GameTests.check(world.getServer().computeOnServer(server -> boss(server).getMainHandItem().is(Items.DIAMOND_SWORD)),
				"boss didn't switch back to his sword after eating");
		world.getServer().runOnServer(server -> boss(server).vanish());
		context.waitTicks(2);
	}

	/** At his retreat point, a habitual pillarer towers up; the tower is gone when he is. */
	private static void checkPillar(ClientGameTestContext context, TestSingleplayerContext world) {
		world.getServer().runCommand("tp @p 0.5 -60 0.5 0 0");
		world.getServer().runCommand("herobrine profile @p preset pillar_coward");
		world.getServer().runOnServer(server -> ModAttachments.profile(GameTests.player(server))
				.setTrait(TraitId.PILLAR_RATE, new Trait(1.0, 1000)));
		summon(world);
		context.waitTicks(20);
		world.getServer().runOnServer(server -> boss(server).setHealth(20.0F));
		boolean pillared = waitUntil(context, world, 160, server -> {
			MirrorBrain brain = boss(server).brain();
			return brain != null && brain.placedBlocks().size() >= 3 && !brain.pillaring();
		});
		GameTests.check(pillared, "pillar coward at half health never built a pillar");
		List<BlockPos> placed = world.getServer().computeOnServer(server -> boss(server).brain().placedBlocks());
		context.runOnClient(MirrorClientGameTest::lookAtHerobrine);
		GameTests.screenshot(context, "m6-pillar");
		world.getServer().runOnServer(server -> {
			for (BlockPos pos : placed) {
				GameTests.check(!GameTests.player(server).level().getBlockState(pos).isAir(), "pillar block missing at " + pos);
			}
			boss(server).vanish();
		});
		context.waitTicks(2);
		world.getServer().runOnServer(server -> {
			for (BlockPos pos : placed) {
				GameTests.check(GameTests.player(server).level().getBlockState(pos).isAir(), "pillar block left behind at " + pos);
			}
		});
	}

	/** Points the camera at him so the screenshot shows the fight. */
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

	private static void summon(TestSingleplayerContext world) {
		world.getServer().runOnServer(server -> {
			ServerPlayer player = GameTests.player(server);
			GameTests.check(ConfrontationManager.summon(player, CounterPlan.NONE) != null, "summon found no room");
		});
	}

	private static HerobrineEntity boss(MinecraftServer server) {
		HerobrineEntity boss = ConfrontationManager.activeBoss(GameTests.player(server));
		GameTests.check(boss != null, "no active boss");
		return boss;
	}

	private interface ServerCheck {
		boolean test(MinecraftServer server);
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

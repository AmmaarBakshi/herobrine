package io.github.ammaarbakshi.herobrine.gametest;

import java.util.Comparator;

import com.mojang.serialization.JsonOps;
import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;

import io.github.ammaarbakshi.herobrine.config.HerobrineConfig;
import io.github.ammaarbakshi.herobrine.mirror.PlayerBehaviorRecorder;
import io.github.ammaarbakshi.herobrine.mirror.PlayerProfile;
import io.github.ammaarbakshi.herobrine.mirror.ProfileCodecs;
import io.github.ammaarbakshi.herobrine.mirror.TraitId;
import io.github.ammaarbakshi.herobrine.mirror.WeaponType;
import io.github.ammaarbakshi.herobrine.registry.ModAttachments;

/**
 * M5: fight 10 zombies with a sword while strafing right, using real keyboard input. The
 * profile must show {@code strafeBias > 0.3} and sword as top weapon, and survive a death and
 * a full world restart (which is also a relog).
 */
public final class RecorderClientGameTest implements FabricClientGameTest {
	private static final int ZOMBIES = 10;
	private static final int MAX_TICKS_PER_ZOMBIE = 600;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!GameTests.selected(this)) {
			return;
		}
		String before;
		TestWorldSave save;
		try (TestSingleplayerContext world = GameTests.createWorld(context)) {
			save = world.getWorldSave();
			setUp(world);
			context.waitTicks(5);
			world.getServer().runOnServer(server -> {
				PlayerBehaviorRecorder.resetCost();
				// Only the recorder under test: no random haunt events.
				ModAttachments.trail(GameTests.player(server)).nextEventTick = Long.MAX_VALUE;
			});

			for (int i = 0; i < ZOMBIES; i++) {
				fightOneZombie(context, world, i);
				if (i == 0) {
					// First fight warms up the JIT and class loading; measure the steady state.
					world.getServer().runOnServer(server -> PlayerBehaviorRecorder.resetCost());
				}
			}
			context.getInput().releaseKey(options -> options.keyRight);
			context.getInput().releaseKey(options -> options.keyUp);
			context.waitTicks(5);

			before = world.getServer().computeOnServer(server -> {
				PlayerProfile profile = ModAttachments.profile(GameTests.player(server));
				int min = HerobrineConfig.get().minTraitSamples;
				double bias = profile.trait(TraitId.STRAFE_BIAS).value();
				GameTests.check(profile.trait(TraitId.STRAFE_BIAS).known(min), "strafe bias not known: " + profile.trait(TraitId.STRAFE_BIAS));
				GameTests.check(bias > 0.3, "strafe bias not > 0.3 after strafing right: " + bias);
				GameTests.check(profile.weaponPref().known(min) && profile.topWeapon() == WeaponType.SWORD,
						"sword is not the known top weapon: " + profile.weaponPref());
				GameTests.check(profile.gear().weapon().equals("minecraft:iron_sword"), "gear did not record the sword: " + profile.gear());
				PlayerBehaviorRecorder.Cost cost = PlayerBehaviorRecorder.cost();
				System.out.printf("[herobrine-perf] recorder %.2f us per player-tick (worst %.1f us) over %d player-ticks%n",
						cost.averageMicrosPerPlayerTick(), cost.worstMicros(), cost.playerTicks());
				GameTests.check(cost.averageMicrosPerPlayerTick() < 100.0, "recorder over 0.1 ms per player-tick: " + cost);
				return encode(profile);
			});

			// Death: the respawned player keeps the profile (attachment is copied on death).
			world.getServer().runCommand("kill @p");
			context.waitTicks(20);
			context.clickScreenButton("deathScreen.respawn");
			context.waitTicks(20);
			String afterDeath = world.getServer().computeOnServer(server -> encode(ModAttachments.profile(GameTests.player(server))));
			GameTests.check(before.equals(afterDeath), "profile changed across death:\n" + before + "\n" + afterDeath);
		}

		// Restart: closing and reopening shuts the integrated server down and logs back in.
		try (TestSingleplayerContext world = save.open()) {
			world.getConnection().waitForChunksRender();
			String afterRestart = world.getServer().computeOnServer(server -> encode(ModAttachments.profile(GameTests.player(server))));
			GameTests.check(before.equals(afterRestart), "profile changed across restart:\n" + before + "\n" + afterRestart);
			world.getServer().runOnServer(server -> HerobrineConfig.get().debugOverlay = false);
		}
	}

	private static void setUp(TestSingleplayerContext world) {
		world.getServer().runCommand("time set midnight");
		world.getServer().runCommand("difficulty easy");
		world.getServer().runCommand("tp @p 0.5 -60 0.5 0 0");
		world.getServer().runCommand("effect give @p minecraft:resistance infinite 4 true");
		world.getServer().runCommand("effect give @p minecraft:saturation infinite 0 true");
		world.getServer().runCommand("item replace entity @p hotbar.0 with minecraft:iron_sword");
		world.getServer().runCommand("herobrine profile @p reset");
		// Also exercises the HUD: it should show live values while fighting.
		world.getServer().runOnServer(server -> HerobrineConfig.get().debugOverlay = true);
	}

	/** Circle-strafe right around one zombie, hitting it whenever the sword is fully charged. */
	private static void fightOneZombie(ClientGameTestContext context, TestSingleplayerContext world, int index) {
		// Level rotation: the camera may still point down at the last zombie, which would bury this one.
		world.getServer().runCommand("execute as @p at @s rotated ~ 0 run summon minecraft:zombie ^ ^ ^4 {PersistenceRequired:1b}");
		context.waitTicks(2);
		context.getInput().holdKey(options -> options.keyRight);
		for (int tick = 0; tick < MAX_TICKS_PER_ZOMBIE; tick++) {
			boolean alive = context.computeOnClient(RecorderClientGameTest::aimAtNearestZombie);
			if (!alive) {
				return;
			}
			double distance = context.computeOnClient(RecorderClientGameTest::distanceToNearestZombie);
			if (distance > 2.6) {
				context.getInput().holdKey(options -> options.keyUp);
			} else {
				context.getInput().releaseKey(options -> options.keyUp);
			}
			boolean charged = context.computeOnClient(client -> client.player.getAttackStrengthScale(0.0F) >= 1.0F);
			if (charged && distance < 3.0) {
				context.getInput().pressKey(options -> options.keyAttack);
			}
			context.waitTick();
			if (index == 4 && tick == 60) {
				context.takeScreenshot("m5-fighting");
			}
		}
		throw new AssertionError("zombie " + index + " survived " + MAX_TICKS_PER_ZOMBIE + " ticks");
	}

	private static boolean aimAtNearestZombie(Minecraft client) {
		LocalPlayer player = client.player;
		Zombie zombie = nearest(client);
		if (zombie == null) {
			return false;
		}
		Vec3 d = zombie.position().add(0, zombie.getBbHeight() * 0.6, 0).subtract(player.getEyePosition());
		float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0F;
		float pitch = (float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
		player.setYRot(yaw);
		player.setXRot(pitch);
		player.setYHeadRot(yaw);
		return true;
	}

	private static double distanceToNearestZombie(Minecraft client) {
		Zombie zombie = nearest(client);
		return zombie == null ? Double.MAX_VALUE : Math.sqrt(zombie.distanceToSqr(client.player));
	}

	private static @Nullable Zombie nearest(Minecraft client) {
		return client.level.getEntitiesOfClass(Zombie.class, client.player.getBoundingBox().inflate(32), Zombie::isAlive).stream()
				.min(Comparator.comparingDouble(z -> z.distanceToSqr(client.player)))
				.orElse(null);
	}

	private static String encode(PlayerProfile profile) {
		return ProfileCodecs.PROFILE.encodeStart(JsonOps.INSTANCE, profile).getOrThrow().toString();
	}
}

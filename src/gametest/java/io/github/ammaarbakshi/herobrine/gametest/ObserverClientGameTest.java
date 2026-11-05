package io.github.ammaarbakshi.herobrine.gametest;

import java.util.List;
import java.util.Locale;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;

import io.github.ammaarbakshi.herobrine.HerobrineMod;
import io.github.ammaarbakshi.herobrine.behaviour.DialogueController;
import io.github.ammaarbakshi.herobrine.behaviour.ObservationController;
import io.github.ammaarbakshi.herobrine.client.voice.VoicePlayer;
import io.github.ammaarbakshi.herobrine.entity.HerobrineEntity;
import io.github.ammaarbakshi.herobrine.entity.HerobrineEntity.Mode;
import io.github.ammaarbakshi.herobrine.memory.Counters;
import io.github.ammaarbakshi.herobrine.memory.GearTier;
import io.github.ammaarbakshi.herobrine.memory.Milestone;
import io.github.ammaarbakshi.herobrine.memory.ObserverMemory;
import io.github.ammaarbakshi.herobrine.perf.Perf;
import io.github.ammaarbakshi.herobrine.perf.PerfMeter;
import io.github.ammaarbakshi.herobrine.registry.ModAttachments;
import io.github.ammaarbakshi.herobrine.sighting.LookDetector;

/**
 * The Living Observer: he watches from a distance and leaves when you look away or walk up to
 * him, shows only his eyes in the dark, speaks (as a caption while no recording exists), notices
 * an iron sword, recognises the BERSERK memorial, and remembers all of it after a restart.
 */
public final class ObserverClientGameTest implements FabricClientGameTest {
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
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("tp @p 0.5 -60 0.5 180 0");
			world.getServer().runCommand("effect give @p minecraft:resistance infinite 4 true");
			GameTests.check(waitUntil(context, world, 10, server -> memory(server).baselined()),
					"the observer never took its first look at the player");

			heSpeaks(context, world);
			observerLeavesWhenYouLookAway(context, world);
			observerFadesWhenApproached(context, world);
			eyesInTheDark(context, world);
			heNoticesProgression(context, world);
			theMemorialIsRecognised(context, world);
			measureCost(context, world);
		}

		try (TestSingleplayerContext world = save.open()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runOnServer(server -> {
				ObserverMemory memory = memory(server);
				GameTests.check(memory.counter(Counters.ENCOUNTERS) >= 2, "encounters were forgotten: " + memory.counters());
				GameTests.check(memory.counter(Counters.GEAR_TIER) == GearTier.IRON.ordinal(), "gear tier was forgotten");
				GameTests.check(memory.hasMilestone(Milestone.FOUND_BERSERK), "the memorial was forgotten");
				GameTests.check(memory.lastSaid("random.first") >= 0, "the dialogue history was forgotten");
			});
		}
	}

	private static void heSpeaks(ClientGameTestContext context, TestSingleplayerContext world) {
		world.getServer().runCommand("herobrine voice say @p random.first");
		context.waitFor(client -> "random.first".equals(VoicePlayer.lastLineId()), 20);
		context.waitTicks(10);
		GameTests.screenshot(context, "m10-caption");
		world.getServer().runOnServer(server -> GameTests.check(memory(server).recentlySaid("random.first"),
				"the line wasn't recorded in his memory"));
		GameTests.check(DialogueController.catalog().size() >= 100, "the dialogue didn't load");
	}

	private static void observerLeavesWhenYouLookAway(ClientGameTestContext context, TestSingleplayerContext world) {
		world.getServer().runCommand("tp @p 0.5 -60 0.5 180 0");
		world.getServer().runCommand("herobrine appear @p observer");
		context.waitTick();
		world.getServer().runOnServer(server -> {
			HerobrineEntity h = only(server);
			GameTests.check(h.mode() == Mode.OBSERVER, "expected an observer, got " + h.mode());
			double d = Math.sqrt(h.distanceToSqr(GameTests.player(server)));
			GameTests.check(d >= 10 && d <= 43, "observer at a strange distance: " + d);
			GameTests.check(LookDetector.hasLineOfSight(GameTests.player(server), h.getEyePosition()), "observer is out of sight");
		});
		// Face him: he stays while watched, and the meeting is remembered.
		GameTests.asPlayer(world, "tp @s ~ ~ ~ facing entity @e[type=herobrine:herobrine,limit=1] eyes");
		GameTests.check(waitUntil(context, world, 3, server -> memory(server).counter(Counters.ENCOUNTERS) >= 1),
				"looking at the observer didn't count as an encounter");
		context.waitTicks(40);
		world.getServer().runOnServer(server -> GameTests.check(herobrines(server).size() == 1, "the observer left while being watched"));
		GameTests.screenshot(context, "m10-observer");
		// Look away: he is gone within a second.
		GameTests.asPlayer(world, "tp @s ~ ~ ~ ~180 0");
		GameTests.check(waitUntil(context, world, 2, server -> herobrines(server).isEmpty()),
				"the observer stayed after the player looked away");
		world.getServer().runOnServer(server -> GameTests.check(memory(server).counter(Counters.LOOKED_AWAY) == 1,
				"looking away wasn't remembered"));
	}

	private static void observerFadesWhenApproached(ClientGameTestContext context, TestSingleplayerContext world) {
		world.getServer().runCommand("tp @p 0.5 -60 0.5 180 0");
		world.getServer().runCommand("herobrine appear @p observer");
		context.waitTick();
		world.getServer().runCommand("execute as @p at @e[type=herobrine:herobrine,limit=1] run tp @s ~3 ~ ~3 facing entity @e[type=herobrine:herobrine,limit=1] eyes");
		context.waitTicks(3);
		world.getServer().runOnServer(server -> {
			List<? extends HerobrineEntity> left = herobrines(server);
			GameTests.check(left.isEmpty() || left.getFirst().isLeaving(), "the observer didn't start to leave when approached");
		});
		context.waitTicks(4);
		GameTests.screenshot(context, "m10-fading");
		GameTests.check(waitUntil(context, world, 2, server -> herobrines(server).isEmpty()), "the observer never finished fading");
		world.getServer().runOnServer(server -> GameTests.check(memory(server).counter(Counters.APPROACHED) == 1,
				"walking up to him wasn't remembered"));
	}

	private static void eyesInTheDark(ClientGameTestContext context, TestSingleplayerContext world) {
		world.getServer().runCommand("time set midnight");
		world.getServer().runCommand("tp @p 0.5 -60 0.5 180 0");
		context.waitTicks(5);
		world.getServer().runCommand("herobrine appear @p eyes");
		context.waitTick();
		world.getServer().runOnServer(server -> GameTests.check(only(server).mode() == Mode.EYES, "expected only his eyes"));
		GameTests.asPlayer(world, "tp @s ~ ~ ~ facing entity @e[type=herobrine:herobrine,limit=1] eyes");
		context.waitTicks(10);
		GameTests.screenshot(context, "m10-eyes");
		world.getServer().runCommand("kill @e[type=herobrine:herobrine]");
		world.getServer().runCommand("time set noon");
		context.waitTicks(5);
	}

	private static void heNoticesProgression(ClientGameTestContext context, TestSingleplayerContext world) {
		world.getServer().runCommand("item replace entity @p weapon.mainhand with minecraft:iron_sword");
		GameTests.check(waitUntil(context, world, 2, server -> memory(server).counter(Counters.GEAR_TIER) == GearTier.IRON.ordinal()),
				"holding an iron sword didn't raise his idea of the player's gear");
		world.getServer().runOnServer(server -> GameTests.check(
				memory(server).events().stream().anyMatch(e -> e.id().equals("tier_iron")), "the iron tier wasn't noted as an event"));
		// A weaker tool afterwards is not news.
		world.getServer().runCommand("item replace entity @p weapon.mainhand with minecraft:wooden_pickaxe");
		context.waitTicks(3);
		world.getServer().runOnServer(server -> GameTests.check(memory(server).counter(Counters.GEAR_TIER) == GearTier.IRON.ordinal(),
				"a wooden pickaxe lowered the remembered tier"));
	}

	private static void theMemorialIsRecognised(ClientGameTestContext context, TestSingleplayerContext world) {
		// The test world is flat, where structures don't generate: build one as world generation would.
		world.getServer().runCommand("forceload add 32 32 64 64");
		context.waitTicks(5);
		BoundingBox box = world.getServer().computeOnServer(server -> generateMemorial(server.overworld(), new ChunkPos(3, 3)));
		BlockPos center = box.getCenter();
		world.getServer().runCommand(String.format(Locale.ROOT, "tp @p %d.5 %d %d.5", center.getX(), box.minY() + 4, center.getZ()));
		GameTests.check(waitUntil(context, world, 25, server -> memory(server).hasMilestone(Milestone.FOUND_BERSERK)),
				"standing in the memorial wasn't recognised");
		// A view of it from a few steps outside one corner, a little above.
		world.getServer().runCommand(String.format(Locale.ROOT, "tp @p %d.5 %d %d.5 facing %d %d %d",
				box.maxX() + 4, box.minY() + 8, box.maxZ() + 4, center.getX(), box.minY() + 4, center.getZ()));
		context.waitTicks(20);
		world.getConnection().waitForChunksRender();
		GameTests.screenshot(context, "m10-berserk");
	}

	/** What the observer costs per server tick with one player, on its own meter. */
	private static void measureCost(ClientGameTestContext context, TestSingleplayerContext world) {
		world.getServer().runCommand("tp @p 0.5 -60 0.5 0 0");
		context.waitTicks(20);
		world.getServer().runOnServer(server -> Perf.resetAll());
		context.waitTicks(400);
		PerfMeter.Snapshot observer = world.getServer().computeOnServer(server -> Perf.OBSERVER.snapshot());
		PerfMeter.Snapshot rest = world.getServer().computeOnServer(server -> Perf.WORLD.snapshot());
		HerobrineMod.LOGGER.info("Observer with one player: {} µs per tick on average, worst {} µs; haunt, sightings and fights: {} µs",
				String.format(Locale.ROOT, "%.2f", observer.averageMicros()), String.format(Locale.ROOT, "%.1f", observer.worstMicros()),
				String.format(Locale.ROOT, "%.2f", rest.averageMicros()));
		GameTests.check(observer.averageMicros() < 100.0, "the observer got expensive: " + observer.averageMicros() + " µs per tick");
	}

	/**
	 * Generates the memorial at {@code chunk} the way world generation does: the start is stored
	 * in its chunk and referenced from every chunk it covers. {@code /place structure} only places
	 * the blocks, so nothing could tell a structure is there.
	 */
	private static BoundingBox generateMemorial(ServerLevel level, ChunkPos chunk) {
		Holder.Reference<Structure> holder = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
				.getOrThrow(ObservationController.BERSERK);
		ChunkGenerator generator = level.getChunkSource().getGenerator();
		RandomState randomState = level.getChunkSource().randomState();
		StructureStart start = holder.value().generate(holder, level.dimension(), level.registryAccess(), generator,
				generator.getBiomeSource(), randomState.createClimateSampler(SamplerContext.EMPTY_UNCACHED), randomState,
				level.getStructureTemplateManager(), level.getSeed(), chunk, 0, level, biome -> true);
		GameTests.check(start.isValid(), "the memorial failed to generate");
		level.getChunk(chunk.x(), chunk.z()).setStartForStructure(holder.value(), start);
		BoundingBox box = start.getBoundingBox();
		ChunkPos min = new ChunkPos(SectionPos.blockToSectionCoord(box.minX()), SectionPos.blockToSectionCoord(box.minZ()));
		ChunkPos max = new ChunkPos(SectionPos.blockToSectionCoord(box.maxX()), SectionPos.blockToSectionCoord(box.maxZ()));
		ChunkPos.rangeClosed(min, max).forEach(c -> {
			level.getChunk(c.x(), c.z()).addReferenceForStructure(holder.value(), chunk.pack());
			start.placeInChunk(level, level.structureManager(), generator, level.getRandom(),
					new BoundingBox(c.getMinBlockX(), level.getMinY(), c.getMinBlockZ(), c.getMaxBlockX(), level.getMaxY() + 1, c.getMaxBlockZ()), c);
		});
		// The start's own box is inflated for terrain adaptation; the piece is the ruin itself.
		return start.getPieces().getFirst().getBoundingBox();
	}

	// --- Helpers ------------------------------------------------------------------------------

	private static ObserverMemory memory(MinecraftServer server) {
		ServerPlayer player = GameTests.player(server);
		return ModAttachments.memory(player);
	}

	private static List<? extends HerobrineEntity> herobrines(MinecraftServer server) {
		return GameTests.herobrines(server).stream().filter(h -> !h.isRemoved()).toList();
	}

	private static HerobrineEntity only(MinecraftServer server) {
		List<? extends HerobrineEntity> all = herobrines(server);
		GameTests.check(all.size() == 1, "expected one Herobrine, found " + all.size());
		return all.getFirst();
	}

	/** Polls once a tick for up to {@code seconds}. */
	private static boolean waitUntil(ClientGameTestContext context, TestSingleplayerContext world, int seconds, ServerCheck check) {
		for (int tick = 0; tick < seconds * 20; tick++) {
			if (world.getServer().computeOnServer(check::test)) {
				return true;
			}
			context.waitTick();
		}
		return false;
	}
}

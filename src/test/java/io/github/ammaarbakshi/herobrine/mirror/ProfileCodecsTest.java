package io.github.ammaarbakshi.herobrine.mirror;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import org.junit.jupiter.api.Test;

class ProfileCodecsTest {
	@Test
	void profileRoundTrips() {
		PlayerProfile original = ProfilePresets.PILLAR_COWARD.build();
		original.observeBlock("minecraft:oak_planks");

		JsonElement json = ProfileCodecs.PROFILE.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
		PlayerProfile decoded = ProfileCodecs.PROFILE.parse(JsonOps.INSTANCE, json).getOrThrow();

		assertEquals(original.traits(), decoded.traits());
		assertEquals(original.weaponPref(), decoded.weaponPref());
		assertEquals(original.healItemPref(), decoded.healItemPref());
		assertEquals(original.favoriteBlocks(), decoded.favoriteBlocks());
		assertEquals(original.gear(), decoded.gear());
	}

	@Test
	void traitsAreKeyedByStableNames() {
		JsonElement json = ProfileCodecs.PROFILE.encodeStart(JsonOps.INSTANCE, ProfilePresets.BOW_KITER.build()).getOrThrow();
		assertTrue(json.getAsJsonObject().getAsJsonObject("traits").has("strafeBias"));
	}

	@Test
	void unknownTraitsAndMissingFieldsAreTolerated() {
		JsonElement json = JsonParser.parseString("""
				{"traits": {"strafeBias": {"value": 0.4, "samples": 30}, "removedTrait": {"value": 1, "samples": 1}}}
				""");
		PlayerProfile p = ProfileCodecs.PROFILE.parse(JsonOps.INSTANCE, json).getOrThrow();
		assertEquals(new Trait(0.4, 30), p.trait(TraitId.STRAFE_BIAS));
		assertEquals(1, p.traits().size());
		assertEquals(GearSnapshot.EMPTY, p.gear());
	}

	@Test
	void favoriteBlocksAreBounded() {
		PlayerProfile p = new PlayerProfile();
		for (int i = 0; i < 20; i++) {
			for (int n = 0; n <= i; n++) {
				p.observeBlock("minecraft:block_" + i);
			}
		}
		assertEquals(PlayerProfile.MAX_FAVORITE_BLOCKS, p.favoriteBlocks().size());
		assertEquals("minecraft:block_19", p.favoriteBlocksRanked().getFirst());
		p.setFavoriteBlocks(Map.of("a", 3, "b", 9));
		assertEquals("b", p.favoriteBlocksRanked().getFirst());
	}
}

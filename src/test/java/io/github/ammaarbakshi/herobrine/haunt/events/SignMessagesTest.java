package io.github.ammaarbakshi.herobrine.haunt.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.SplittableRandom;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import io.github.ammaarbakshi.herobrine.mirror.PlayerProfile;
import io.github.ammaarbakshi.herobrine.mirror.Trait;
import io.github.ammaarbakshi.herobrine.mirror.TraitId;
import io.github.ammaarbakshi.herobrine.mirror.WeaponType;

class SignMessagesTest {
	private static final int MIN = 20;

	@Test
	void unknownProfileGetsOnlyGenericMessages() {
		PlayerProfile profile = new PlayerProfile();
		assertTrue(SignMessages.personal(profile, MIN, 20.0F).isEmpty());
		SplittableRandom random = new SplittableRandom(1);
		for (int i = 0; i < 50; i++) {
			assertTrue(SignMessages.GENERIC.contains(SignMessages.pick(profile, MIN, 20.0F, random).id()));
		}
	}

	@Test
	void strafingLeftIsCalledOut() {
		PlayerProfile profile = new PlayerProfile();
		profile.setTrait(TraitId.STRAFE_BIAS, new Trait(-0.6, MIN));
		assertEquals(List.of("always_left"), ids(profile));
		profile.setTrait(TraitId.STRAFE_BIAS, new Trait(0.1, MIN));
		assertTrue(ids(profile).isEmpty(), "a slight bias is not a habit");
	}

	@Test
	void healThresholdIsQuotedInHearts() {
		PlayerProfile profile = new PlayerProfile();
		// Heals are rare events: known from max(3, min/4) samples.
		profile.setTrait(TraitId.HEAL_THRESHOLD, new Trait(0.3, 5));
		List<SignMessages.Message> messages = SignMessages.personal(profile, MIN, 20.0F);
		assertEquals(1, messages.size());
		assertEquals("heal_at", messages.getFirst().id());
		assertEquals(List.of(3L), messages.getFirst().args());
	}

	@Test
	void favouriteWeaponIsNamed() {
		PlayerProfile profile = new PlayerProfile();
		for (int i = 0; i < MIN; i++) {
			profile.observeWeapon(WeaponType.AXE);
		}
		assertTrue(ids(profile).contains("weapon_axe"));
	}

	@Test
	void everyMessageHasAllLangLines() throws Exception {
		JsonObject lang;
		try (InputStream in = SignMessagesTest.class.getResourceAsStream("/assets/herobrine/lang/en_us.json")) {
			assertNotNull(in, "lang file not on the classpath");
			lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		}
		for (String id : SignMessages.allIds()) {
			SignMessages.Message message = new SignMessages.Message(id, List.of());
			for (int line = 1; line <= SignMessages.LINES; line++) {
				assertTrue(lang.has(message.lineKey(line)), "missing " + message.lineKey(line));
			}
		}
	}

	private static List<String> ids(PlayerProfile profile) {
		return SignMessages.personal(profile, MIN, 20.0F).stream().map(SignMessages.Message::id).toList();
	}
}

package io.github.ammaarbakshi.herobrine.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import net.fabricmc.loader.api.FabricLoader;

import io.github.ammaarbakshi.herobrine.HerobrineMod;

/**
 * Settings stored in {@code config/herobrine.json}.
 *
 * <p>Fields are public and mutable so Gson can bind them directly; they are only ever
 * read through the immutable-by-convention snapshot returned by {@link #get()}. A reload
 * builds a fresh instance and swaps the reference, so readers never see a half-applied file.
 */
public final class HerobrineConfig {
	public enum Griefing {
		/** Never touches blocks. */
		NONE,
		/** Only the torch thief may remove (and log) torches. */
		TORCHES_ONLY,
		/** All sign events, restricted to natural blocks. */
		NATURAL_ONLY
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final String FILE_NAME = "herobrine.json";

	private static volatile HerobrineConfig current = new HerobrineConfig();

	public boolean enabled = true;
	public double hauntSpeedMultiplier = 1.0;
	public Griefing griefing = Griefing.NATURAL_ONLY;
	public int protectedRadius = 16;
	public boolean jumpscares = true;
	public int maxJumpscaresPerDay = 1;
	public boolean fakeChatMessages = true;
	public int minTraitSamples = 20;
	public double requiredConfidence = 0.6;
	public double bossHealth = 40.0;
	public int healItemCap = 3;
	public boolean adaptiveCounters = true;
	public boolean debugOverlay = false;

	/** Haunt level at which each phase begins (Signs always start at 0). */
	public int sightingsThreshold = 25;
	public int stalkingThreshold = 60;
	public int confrontationThreshold = 90;

	public static HerobrineConfig get() {
		return current;
	}

	public static Path defaultPath() {
		return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
	}

	/** Loads from the default location, writing defaults if the file is missing. */
	public static HerobrineConfig load() {
		return load(defaultPath());
	}

	/**
	 * Loads and installs the config at {@code path}. On a malformed file the previous
	 * settings are kept and the file is left untouched, so a typo never wipes a user's edits.
	 */
	public static HerobrineConfig load(Path path) {
		HerobrineConfig loaded;
		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				loaded = GSON.fromJson(reader, HerobrineConfig.class);
				if (loaded == null) {
					loaded = new HerobrineConfig();
				}
			} catch (IOException | JsonParseException e) {
				HerobrineMod.LOGGER.error("Could not read {}, keeping previous settings", path, e);
				return current;
			}
		} else {
			loaded = new HerobrineConfig();
		}

		loaded.sanitize();
		// Re-save so new options appear in old files and clamped values are visible to the user.
		loaded.save(path);
		current = loaded;
		return loaded;
	}

	public void save(Path path) {
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException e) {
			HerobrineMod.LOGGER.error("Could not write {}", path, e);
		}
	}

	/** Clamps every value into a range the rest of the mod can rely on without re-checking. */
	void sanitize() {
		if (griefing == null) {
			griefing = Griefing.NATURAL_ONLY;
		}
		hauntSpeedMultiplier = clamp(hauntSpeedMultiplier, 0.0, 1000.0);
		protectedRadius = clamp(protectedRadius, 0, 128);
		maxJumpscaresPerDay = clamp(maxJumpscaresPerDay, 0, 100);
		minTraitSamples = clamp(minTraitSamples, 1, 10_000);
		requiredConfidence = clamp(requiredConfidence, 0.0, 1.0);
		bossHealth = clamp(bossHealth, 1.0, 1024.0);
		healItemCap = clamp(healItemCap, 0, 64);
		sightingsThreshold = clamp(sightingsThreshold, 0, 100);
		stalkingThreshold = clamp(stalkingThreshold, sightingsThreshold, 100);
		confrontationThreshold = clamp(confrontationThreshold, stalkingThreshold, 100);
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private static double clamp(double value, double min, double max) {
		if (Double.isNaN(value)) {
			return min;
		}
		return Math.max(min, Math.min(max, value));
	}
}

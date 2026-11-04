package io.github.ammaarbakshi.herobrine.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

import io.github.ammaarbakshi.herobrine.HerobrineMod;

/**
 * Data-driven block sets (data/herobrine/tags/block). Server owners and modpacks can override
 * them with a datapack to widen or narrow what the haunting is allowed to touch.
 */
public final class ModTags {
	/** Blocks the 2x2 tunnel may carve through. Anything else stops the tunnel. */
	public static final TagKey<Block> TUNNEL_CARVABLE = block("tunnel_carvable");
	/** Torches the torch thief may take. */
	public static final TagKey<Block> STEALABLE_TORCHES = block("stealable_torches");
	/** Natural ground a sand pyramid may be built on. */
	public static final TagKey<Block> PYRAMID_GROUND = block("pyramid_ground");

	private ModTags() {
	}

	private static TagKey<Block> block(String name) {
		return TagKey.create(Registries.BLOCK, HerobrineMod.id(name));
	}
}

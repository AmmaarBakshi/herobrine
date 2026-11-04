package io.github.ammaarbakshi.herobrine.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

import io.github.ammaarbakshi.herobrine.HerobrineMod;
import io.github.ammaarbakshi.herobrine.entity.HerobrineEntity;

/** Entity types. Herobrine is deliberately absent from every biome spawn table. */
public final class ModEntities {
	public static final ResourceKey<EntityType<?>> HEROBRINE_KEY =
			ResourceKey.create(Registries.ENTITY_TYPE, HerobrineMod.id("herobrine"));

	public static final EntityType<HerobrineEntity> HEROBRINE = Registry.register(BuiltInRegistries.ENTITY_TYPE, HEROBRINE_KEY,
			EntityType.Builder.<HerobrineEntity>of(HerobrineEntity::new, MobCategory.MONSTER)
					// Player-sized hitbox and eye height, so he lines up exactly with the player he copies.
					.sized(0.6F, 1.8F)
					.eyeHeight(1.62F)
					// Sightings happen up to 60 blocks away: track him at player range (10 chunks).
					.clientTrackingRange(10)
					.build(HEROBRINE_KEY));

	private ModEntities() {
	}

	public static void init() {
		FabricDefaultAttributeRegistry.register(HEROBRINE, HerobrineEntity.createAttributes());
	}
}

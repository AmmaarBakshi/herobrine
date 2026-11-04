package io.github.ammaarbakshi.herobrine.registry;

import java.util.function.Function;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;

import io.github.ammaarbakshi.herobrine.HerobrineMod;
import io.github.ammaarbakshi.herobrine.item.MirrorShardItem;

/** Items and their creative-tab placement. */
public final class ModItems {
	public static final Item HEROBRINE_SPAWN_EGG = register("herobrine_spawn_egg", SpawnEggItem::new,
			new Item.Properties().spawnEgg(ModEntities.HEROBRINE));
	public static final Item MIRROR_SHARD = register("mirror_shard", MirrorShardItem::new,
			new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant());

	private ModItems() {
	}

	private static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, HerobrineMod.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	public static void init() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS).register(output -> output.accept(HEROBRINE_SPAWN_EGG));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> output.accept(MIRROR_SHARD));
	}
}

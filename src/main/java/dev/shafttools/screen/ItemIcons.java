package dev.shafttools.screen;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Item stacks for the journal's loot icons. Only used while a screen is open, never at class load. */
final class ItemIcons {
	private final IconRules rules = IconRules.load();
	private final Map<String, Item> cache = new HashMap<>();

	ItemStack stack(String name) {
		return new ItemStack(cache.computeIfAbsent(name, this::lookup));
	}

	private Item lookup(String name) {
		Identifier id = Identifier.tryParse(rules.itemId(name));
		Item item = id == null ? Items.AIR : BuiltInRegistries.ITEM.getValue(id);
		return item == Items.AIR ? Items.PAPER : item;
	}
}

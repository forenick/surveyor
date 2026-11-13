package dev.shaftjournal.screen;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.MissingItemModel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

// screen only
// skyblock id in custom_data like hypixel, so texture mods may find it??
final class ItemIcons {
	// per screen so pack changes get picked up
	private final Map<String, ItemStack> cache = new HashMap<>();
	private final IconRules standIns = IconRules.defaults();
	private final SkyblockItems items = SkyblockItems.defaults();

	ItemStack stack(String name) {
		return cache.computeIfAbsent(name, this::create);
	}

	private ItemStack create(String name) {
		ItemStack stack = new ItemStack(standIn(name));
		SkyblockItems.Info info = items.get(name);
		if (info == null) return stack;

		CompoundTag data = new CompoundTag();
		data.putString("id", info.id());
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
		Identifier model = info.model() == null ? null : Identifier.tryParse(info.model());
		if (model != null && isLoaded(model)) stack.set(DataComponents.ITEM_MODEL, model);
		if (info.glint()) stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		return stack;
	}

	private static boolean isLoaded(Identifier model) {
		return !(Minecraft.getInstance().getModelManager().getItemModel(model) instanceof MissingItemModel);
	}

	private Item standIn(String name) {
		Identifier id = Identifier.tryParse(standIns.itemId(name));
		Item item = id == null ? Items.AIR : BuiltInRegistries.ITEM.getValue(id);
		return item == Items.AIR ? Items.PAPER : item;
	}
}

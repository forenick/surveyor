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

/**
 * Item stacks for the journal's loot icons. Only used while a screen is open, never at class load.
 *
 * <p>A known SkyBlock item gets its SkyBlock ID in {@code custom_data}, the same place Hypixel puts
 * it, so texture mods (Firmament, Catharsis) give it the player's pack texture. While Hypixel's own
 * resource pack is loaded it also gets Hypixel's model; otherwise the vanilla stand-in shows.
 */
final class ItemIcons {
	/** One cache per screen, so a resource pack loaded or unloaded since is picked up next time. */
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

	/** False when no loaded resource pack has this model, e.g. Hypixel's pack was declined. */
	private static boolean isLoaded(Identifier model) {
		return !(Minecraft.getInstance().getModelManager().getItemModel(model) instanceof MissingItemModel);
	}

	private Item standIn(String name) {
		Identifier id = Identifier.tryParse(standIns.itemId(name));
		Item item = id == null ? Items.AIR : BuiltInRegistries.ITEM.getValue(id);
		return item == Items.AIR ? Items.PAPER : item;
	}
}

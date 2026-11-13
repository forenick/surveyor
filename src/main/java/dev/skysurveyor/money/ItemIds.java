package dev.skysurveyor.money;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import dev.skysurveyor.data.SkyblockItems;
import dev.skysurveyor.detection.TextPatterns;

// chat item name -> skyblock id. skyblock_items.txt first, then hypixels item list
public final class ItemIds {
	private final Function<String, String> bundled;
	private final Map<String, String> fetched;

	public ItemIds(Function<String, String> bundled, Map<String, String> fetched) {
		this.bundled = bundled;
		this.fetched = fetched;
	}

	public static ItemIds bundled(Map<String, String> fetched) {
		SkyblockItems items = SkyblockItems.defaults();
		return new ItemIds(name -> {
			SkyblockItems.Info info = items.get(name);
			return info == null ? null : info.id();
		}, fetched);
	}

	public String id(String name) {
		String id = bundled.apply(name);
		return id != null ? id : fetched.get(name);
	}

	// v2/resources/skyblock/items. names w/ 2+ ids dropped, never price the wrong one
	public static Map<String, String> parseHypixelItems(JsonObject json) {
		Map<String, String> ids = new HashMap<>();
		Set<String> ambiguous = new HashSet<>();
		for (JsonElement element : json.getAsJsonArray("items")) {
			JsonObject item = element.getAsJsonObject();
			if (!item.has("name") || !item.has("id")) continue;
			String name = TextPatterns.strip(item.get("name").getAsString()).strip();
			if (ids.putIfAbsent(name, item.get("id").getAsString()) != null) ambiguous.add(name);
		}
		ambiguous.forEach(ids::remove);
		return ids;
	}

	public static Map<String, Double> parseNpcPrices(JsonObject json) {
		Map<String, Double> prices = new HashMap<>();
		for (JsonElement element : json.getAsJsonArray("items")) {
			JsonObject item = element.getAsJsonObject();
			if (!item.has("id") || !item.has("npc_sell_price")) continue;
			double price = item.get("npc_sell_price").getAsDouble();
			if (price > 0) prices.put(item.get("id").getAsString(), price);
		}
		return prices;
	}
}

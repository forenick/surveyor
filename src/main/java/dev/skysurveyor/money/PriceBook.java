package dev.skysurveyor.money;

import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;

// immutable, swapped whole on refresh
public final class PriceBook {
	public static final PriceBook EMPTY = new PriceBook(Map.of(), 0, Map.of(), 0, Map.of());

	private final Map<String, ItemPrice> bazaar;
	private final long bazaarAt;
	private final Map<String, Double> bins;
	private final long binsAt;
	private final Map<String, Double> npc;

	PriceBook(Map<String, ItemPrice> bazaar, long bazaarAt, Map<String, Double> bins, long binsAt, Map<String, Double> npc) {
		this.bazaar = bazaar;
		this.bazaarAt = bazaarAt;
		this.bins = bins;
		this.binsAt = binsAt;
		this.npc = npc;
	}

	public ItemPrice get(String id) {
		ItemPrice bz = bazaar.get(id);
		Double bin = bins.get(id);
		Double npcSell = npc.get(id);
		if (bz == null && bin == null && npcSell == null) return null;
		return new ItemPrice(bz == null ? null : bz.buyOrder(), bz == null ? null : bz.sellOffer(), bin, npcSell);
	}

	// npc prices alone dont count, theyre only a fallback
	public boolean isEmpty() {
		return bazaar.isEmpty() && bins.isEmpty();
	}

	public boolean hasBazaar() {
		return !bazaar.isEmpty();
	}

	public long bazaarAt() {
		return bazaarAt;
	}

	public long binsAt() {
		return binsAt;
	}

	public PriceBook withBazaar(Map<String, ItemPrice> prices, long at) {
		return new PriceBook(Map.copyOf(prices), at, bins, binsAt, npc);
	}

	public PriceBook withBins(Map<String, Double> prices, long at) {
		return new PriceBook(bazaar, bazaarAt, Map.copyOf(prices), at, npc);
	}

	// cached w/ the item names, not in prices.json
	public PriceBook withNpc(Map<String, Double> prices) {
		return new PriceBook(bazaar, bazaarAt, bins, binsAt, Map.copyOf(prices));
	}

	// v2/skyblock/bazaar. sell_summary = buy orders, buy_summary = sell offers (hypixels naming)
	public static Map<String, ItemPrice> parseBazaar(JsonObject json) {
		if (!json.has("success") || !json.get("success").getAsBoolean()) throw new JsonParseException("bazaar: no success");
		Map<String, ItemPrice> prices = new HashMap<>();
		for (Map.Entry<String, JsonElement> product : json.getAsJsonObject("products").entrySet()) {
			JsonObject p = product.getValue().getAsJsonObject();
			ItemPrice price = new ItemPrice(top(p, "sell_summary"), top(p, "buy_summary"), null);
			if (price.onBazaar()) prices.put(product.getKey(), price);
		}
		return prices;
	}

	private static Double top(JsonObject product, String side) {
		JsonArray orders = product.has(side) ? product.getAsJsonArray(side) : null;
		if (orders == null || orders.isEmpty()) return null;
		double price = orders.get(0).getAsJsonObject().get("pricePerUnit").getAsDouble();
		return price > 0 ? price : null;
	}

	// {"ID": price}, elite + hysky both
	public static Map<String, Double> parseBins(JsonObject json) {
		Map<String, Double> prices = new HashMap<>();
		for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
			if (entry.getValue() instanceof JsonPrimitive p && p.isNumber() && p.getAsDouble() > 0) {
				prices.put(entry.getKey(), p.getAsDouble());
			}
		}
		return prices;
	}

	// prices.json cache
	public JsonObject toJson() {
		JsonObject json = new JsonObject();
		json.addProperty("bazaarAt", bazaarAt);
		json.addProperty("binsAt", binsAt);
		JsonObject bz = new JsonObject();
		bazaar.forEach((id, price) -> {
			JsonArray pair = new JsonArray();
			pair.add(price.buyOrder() == null ? JsonNull.INSTANCE : new JsonPrimitive(price.buyOrder()));
			pair.add(price.sellOffer() == null ? JsonNull.INSTANCE : new JsonPrimitive(price.sellOffer()));
			bz.add(id, pair);
		});
		json.add("bazaar", bz);
		JsonObject bin = new JsonObject();
		bins.forEach(bin::addProperty);
		json.add("bins", bin);
		return json;
	}

	public static PriceBook fromJson(JsonObject json) {
		Map<String, ItemPrice> bazaar = new HashMap<>();
		if (json.has("bazaar")) {
			for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("bazaar").entrySet()) {
				JsonArray pair = entry.getValue().getAsJsonArray();
				ItemPrice price = new ItemPrice(number(pair.get(0)), number(pair.get(1)), null);
				if (price.onBazaar()) bazaar.put(entry.getKey(), price);
			}
		}
		Map<String, Double> bins = json.has("bins") ? parseBins(json.getAsJsonObject("bins")) : Map.of();
		long bazaarAt = json.has("bazaarAt") ? json.get("bazaarAt").getAsLong() : 0;
		long binsAt = json.has("binsAt") ? json.get("binsAt").getAsLong() : 0;
		return new PriceBook(Map.copyOf(bazaar), bazaarAt, Map.copyOf(bins), binsAt, Map.of());
	}

	private static Double number(JsonElement element) {
		return element == null || element.isJsonNull() ? null : element.getAsDouble();
	}
}

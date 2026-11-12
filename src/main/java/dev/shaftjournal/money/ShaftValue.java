package dev.shaftjournal.money;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

import dev.shaftjournal.detection.CorpseLoot;
import dev.shaftjournal.detection.ItemCount;
import dev.shaftjournal.detection.TextPatterns;
import dev.shaftjournal.journal.GemYield;
import dev.shaftjournal.journal.JournalEntry;
import dev.shaftjournal.money.MoneySettings.Crystals;
import dev.shaftjournal.money.MoneySettings.GemTier;
import dev.shaftjournal.money.MoneySettings.SellPrice;

// rules in docs/money-design.md
public record ShaftValue(List<Line> lines) {
	// key per corpse (research/01)
	private static final Map<String, String> KEYS = Map.of("Umber", "Umber Key", "Tungsten", "Tungsten Key",
			"Vanguard", "Skeleton Key");
	// portable campfire recipe (research/01)
	private static final int CAMPFIRE_MATCH_STICKS = 16;
	// perfect = 5 flawless + crystal (research/01)
	private static final int PERFECT_FLAWLESS = 5;

	public enum Part {
		MINED("Mined gems"),
		OTHER("Other drops"),
		CORPSES("Corpse loot"),
		SCRAP("Suspicious Scrap"),
		CRYSTALS("Crystals"),
		KEYS("Keys"),
		CAMPFIRES("Campfires");

		private final String displayName;

		Part(String displayName) {
			this.displayName = displayName;
		}

		public String displayName() {
			return displayName;
		}

		public boolean cost() {
			return this == KEYS || this == CAMPFIRES;
		}
	}

	// coins null = no price, costs negative
	public record Line(Part part, String item, double count, Double coins) {
	}

	public interface Prices {
		ItemPrice get(String id);
	}

	public double total(Part part) {
		return lines.stream().filter(l -> l.part == part && l.coins != null).mapToDouble(Line::coins).sum();
	}

	public double net() {
		return lines.stream().filter(l -> l.coins != null).mapToDouble(Line::coins).sum();
	}

	public List<Line> unpriced() {
		return lines.stream().filter(l -> l.coins == null).toList();
	}

	public boolean anyPriced() {
		return lines.stream().anyMatch(l -> l.coins != null);
	}

	// saved prices first, todays for anything they miss
	public static Prices layered(Map<String, ItemPrice> saved, Prices today) {
		if (saved == null || saved.isEmpty()) return today;
		return id -> {
			ItemPrice price = saved.get(id);
			return price != null ? price : today.get(id);
		};
	}

	public static ShaftValue of(JournalEntry entry, Prices prices, MoneySettings settings, ItemIds ids, TextPatterns patterns) {
		Valuer v = new Valuer(prices, settings, ids, patterns);
		List<Line> lines = new ArrayList<>();

		if (entry.gems() != null && entry.gems().fineEquivalent() > 0) {
			double fine = entry.gems().fineEquivalent();
			String gem = entry.gems().gem();
			boolean flawless = settings.gemTier() == GemTier.FLAWLESS;
			Double perFine = v.gemPerFine(gem);
			lines.add(new Line(Part.MINED, (flawless ? "Flawless " : "Fine ") + gem + " Gemstone",
					flawless ? fine / GemYield.RECIPE : fine, perFine == null ? null : perFine * fine));
		}

		otherDrops(entry, patterns).forEach((item, count) -> lines.add(v.line(Part.OTHER, item, count)));

		Map<String, Long> loot = new LinkedHashMap<>();
		Map<String, Long> keys = new LinkedHashMap<>();
		for (CorpseLoot corpse : entry.loots()) {
			for (ItemCount item : corpse.items()) loot.merge(item.item(), item.count(), Long::sum);
			String key = KEYS.get(corpse.type());
			if (key != null) keys.merge(key, 1L, Long::sum);
		}
		loot.forEach((item, count) -> lines.add(v.line(Part.CORPSES, item, count)));

		if (entry.scrapFound() > 0) lines.add(v.line(Part.SCRAP, "Suspicious Scrap", entry.scrapFound()));

		if (entry.crystals() != null) {
			Map<String, Long> crystals = new LinkedHashMap<>();
			for (String crystal : entry.crystals()) crystals.merge(crystal, 1L, Long::sum);
			crystals.forEach((item, count) -> lines.add(v.line(Part.CRYSTALS, item, count)));
		}

		keys.forEach((key, count) -> {
			Double each = v.buy(ids.id(key));
			lines.add(new Line(Part.KEYS, key, count, each == null ? null : -each * count));
		});

		int campfires = entry.campfiresAt().size();
		if (campfires > 0) {
			Double umber = v.buy("REFINED_UMBER");
			Double sticks = v.buy("MATCH_STICKS");
			Double each = umber == null || sticks == null ? null : umber + CAMPFIRE_MATCH_STICKS * sticks;
			lines.add(new Line(Part.CAMPFIRES, "Portable Campfire", campfires, each == null ? null : -each * campfires));
		}
		return new ShaftValue(List.copyOf(lines));
	}

	// every id the value could use, both gem tiers, so settings can change later
	public static Map<String, ItemPrice> snapshot(JournalEntry entry, Prices prices, MoneySettings settings, ItemIds ids,
			TextPatterns patterns) {
		Set<String> used = new HashSet<>();
		Prices recording = id -> {
			used.add(id);
			return prices.get(id);
		};
		for (GemTier tier : GemTier.values()) {
			of(entry, recording, settings.withGemTier(tier).withCrystals(Crystals.PERFECT_MINUS_FLAWLESS), ids, patterns);
		}
		Map<String, ItemPrice> saved = new HashMap<>();
		for (String id : used) {
			ItemPrice price = prices.get(id);
			if (price != null) saved.put(id, price);
		}
		return saved;
	}

	// pre 0.14.0 entries didnt save it: sacks minus all corpse loot, a bit low
	static Map<String, Long> otherDrops(JournalEntry entry, TextPatterns patterns) {
		if (entry.otherDrops() != null) return entry.otherDrops();
		Map<String, Long> drops = new LinkedHashMap<>(entry.sacksAdded());
		for (CorpseLoot corpse : entry.loots()) {
			for (ItemCount item : corpse.items()) drops.merge(item.item(), -item.count(), Long::sum);
		}
		String type = entry.shaftId() == null || entry.shaftId().length() < 4 ? null : entry.shaftId().substring(0, 4);
		drops.entrySet().removeIf(e -> e.getValue() <= 0 || GemYield.isShaftGem(e.getKey(), type, patterns));
		return drops;
	}

	private record Valuer(Prices prices, MoneySettings settings, ItemIds ids, TextPatterns patterns) {
		Line line(Part part, String item, long count) {
			Double each = each(item);
			return new Line(part, item, count, each == null ? null : each * count);
		}

		Double each(String item) {
			Matcher m = patterns.match("gem.item", item);
			if (m != null) {
				Double perFine = gemPerFine(m.group("gem"));
				int steps = switch (m.group("quality")) {
					case "Rough" -> 2;
					case "Flawed" -> 1;
					default -> 0;
				};
				return perFine == null ? null : perFine / Math.pow(GemYield.RECIPE, steps);
			}
			if ((m = patterns.match("crystal.item", item)) != null) return crystal(m.group("gem"));
			return sell(ids.id(item));
		}

		Double gemPerFine(String gem) {
			if (settings.gemTier() == GemTier.FINE) return sell(gemId("FINE", gem));
			Double flawless = sell(gemId("FLAWLESS", gem));
			return flawless == null ? null : flawless / GemYield.RECIPE;
		}

		Double crystal(String gem) {
			if (settings.crystals() == Crystals.ZERO) return 0.0;
			Double perfect = sell(gemId("PERFECT", gem));
			Double flawless = sell(gemId("FLAWLESS", gem));
			if (perfect == null || flawless == null) return null;
			return Math.max(0, perfect - PERFECT_FLAWLESS * flawless);
		}

		// bazaar w/ tax, else lowest bin, else npc (soulbound mob drops, empty order books)
		Double sell(String id) {
			if (id == null) return null;
			ItemPrice price = prices.get(id);
			if (price == null) return null;
			Double side = settings.sellPrice() == SellPrice.INSTA_SELL ? price.buyOrder() : price.sellOffer();
			if (side != null) return side * settings.taxFactor();
			if (!price.onBazaar() && price.lowestBin() != null) return price.lowestBin();
			return price.npcSell();
		}

		// insta-buy
		Double buy(String id) {
			if (id == null) return null;
			ItemPrice price = prices.get(id);
			if (price == null) return null;
			return price.onBazaar() ? price.sellOffer() : price.lowestBin();
		}

		private static String gemId(String tier, String gem) {
			return tier + "_" + gem.toUpperCase(Locale.ROOT) + "_GEM";
		}
	}
}

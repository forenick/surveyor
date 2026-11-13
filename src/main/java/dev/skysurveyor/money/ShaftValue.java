package dev.skysurveyor.money;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.skysurveyor.data.CorpseType;
import dev.skysurveyor.detection.CorpseLoot;
import dev.skysurveyor.detection.ItemCount;
import dev.skysurveyor.detection.TextPatterns;
import dev.skysurveyor.journal.GemYield;
import dev.skysurveyor.journal.JournalEntry;
import dev.skysurveyor.settings.MoneySettings;
import dev.skysurveyor.settings.MoneySettings.Crystals;
import dev.skysurveyor.settings.MoneySettings.GemTier;

// rules in docs/money-design.md
public record ShaftValue(List<Line> lines, List<CorpseValue> corpses) {
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

	}

	// coins null = no price, costs negative
	public record Line(Part part, String item, double count, Double coins) {
	}

	// per looted corpse, key negative, unpriced = items w/o a price (counted as 0)
	public record CorpseValue(String type, double loot, double key, int unpriced, List<Line> items) {
		public double net() {
			return loot + key;
		}
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

	// saved prices first, todays for anything they miss
	public static Prices layered(Map<String, ItemPrice> saved, Prices today) {
		if (saved == null || saved.isEmpty()) return today;
		return id -> {
			ItemPrice price = saved.get(id);
			return price != null ? price : today.get(id);
		};
	}

	public static ShaftValue of(JournalEntry entry, Prices prices, MoneySettings settings, ItemIds ids, TextPatterns patterns) {
		PriceRules v = new PriceRules(prices, settings, ids, patterns);
		List<Line> lines = new ArrayList<>();

		if (entry.gems() != null && entry.gems().fineEquivalent() > 0) {
			double fine = entry.gems().fineEquivalent();
			String gem = entry.gems().gem();
			boolean flawless = !settings.isIronman() && settings.gemTier() == GemTier.FLAWLESS;
			Double perFine = v.gemPerFine(gem);
			lines.add(new Line(Part.MINED, (flawless ? "Flawless " : "Fine ") + gem + " Gemstone",
					flawless ? fine / GemYield.RECIPE : fine, perFine == null ? null : perFine * fine));
		}

		otherDrops(entry, patterns).forEach((item, count) -> lines.add(line(v, Part.OTHER, item, count)));

		Map<String, Long> loot = new LinkedHashMap<>();
		Map<String, Long> keys = new LinkedHashMap<>();
		List<CorpseValue> corpses = new ArrayList<>();
		for (CorpseLoot corpse : entry.loots()) {
			double lootCoins = 0;
			int unpriced = 0;
			List<Line> items = new ArrayList<>();
			for (ItemCount item : corpse.items()) {
				loot.merge(item.item(), item.count(), Long::sum);
				Line line = line(v, Part.CORPSES, item.item(), item.count());
				items.add(line);
				if (line.coins() == null) unpriced++;
				else lootCoins += line.coins();
			}
			String key = CorpseType.key(corpse.type());
			Double keyCost = null;
			if (key != null) {
				keys.merge(key, 1L, Long::sum);
				keyCost = v.key(key);
				if (keyCost == null) unpriced++;
			}
			corpses.add(new CorpseValue(corpse.type(), lootCoins, keyCost == null ? 0 : -keyCost, unpriced, List.copyOf(items)));
		}
		loot.forEach((item, count) -> lines.add(line(v, Part.CORPSES, item, count)));

		if (entry.scrapFound() > 0) lines.add(line(v, Part.SCRAP, "Suspicious Scrap", entry.scrapFound()));

		if (entry.crystals() != null) {
			Map<String, Long> crystals = new LinkedHashMap<>();
			for (String crystal : entry.crystals()) crystals.merge(crystal, 1L, Long::sum);
			crystals.forEach((item, count) -> lines.add(line(v, Part.CRYSTALS, item, count)));
		}

		keys.forEach((key, count) -> {
			Double each = v.key(key);
			lines.add(new Line(Part.KEYS, key, count, each == null ? null : -each * count));
		});

		int campfires = entry.campfiresAt().size();
		if (campfires > 0) {
			Double each = v.campfireCost();
			lines.add(new Line(Part.CAMPFIRES, "Portable Campfire", campfires, each == null ? null : -each * campfires));
		}
		return new ShaftValue(List.copyOf(lines), List.copyOf(corpses));
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
			// ironman uses a subset of these ids
			of(entry, recording, settings.withGemTier(tier).withCrystals(Crystals.PERFECT_MINUS_FLAWLESS).withIronman(false), ids,
					patterns);
		}
		Map<String, ItemPrice> saved = new HashMap<>();
		for (String id : used) {
			ItemPrice price = prices.get(id);
			if (price != null) saved.put(id, price);
		}
		return saved;
	}

	// pre 0.14.0 entries didnt save it: sacks - all corpse loot (a bit low) - sacks removed
	// supercrafts werent saved, removed = their inputs, so only the result counts
	static Map<String, Long> otherDrops(JournalEntry entry, TextPatterns patterns) {
		if (entry.otherDrops() != null) return entry.otherDrops();
		Map<String, Long> drops = new LinkedHashMap<>(entry.sacksAdded());
		for (CorpseLoot corpse : entry.loots()) {
			for (ItemCount item : corpse.items()) drops.merge(item.item(), -item.count(), Long::sum);
		}
		if (entry.sacksRemoved() != null) entry.sacksRemoved().forEach((item, count) -> drops.merge(item, -count, Long::sum));
		String type = entry.shaftType();
		drops.entrySet().removeIf(e -> e.getValue() <= 0 || GemYield.isShaftGem(e.getKey(), type, patterns));
		return drops;
	}

	private static Line line(PriceRules v, Part part, String item, long count) {
		Double each = v.each(item);
		return new Line(part, item, count, each == null ? null : each * count);
	}
}

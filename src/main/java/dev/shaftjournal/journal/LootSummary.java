package dev.shaftjournal.journal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

import dev.shaftjournal.detection.CorpseLoot;
import dev.shaftjournal.detection.ItemCount;
import dev.shaftjournal.detection.TextPatterns;

// rough/flawed/fine merged into one fine group, flawless+ stay normal items (nicks call, 2026-11-11)
public record LootSummary(GemGroup gems, List<ItemCount> items) {
	public record GemGroup(double fine, List<ItemCount> parts) {
	}

	public static LootSummary of(List<CorpseLoot> loots, TextPatterns patterns) {
		Map<String, Long> gemCounts = new LinkedHashMap<>();
		Map<String, Long> itemCounts = new LinkedHashMap<>();
		double fine = 0;
		for (CorpseLoot loot : loots) {
			for (ItemCount item : loot.items()) {
				Matcher m = patterns.match("gem.item", item.item());
				if (m == null) {
					itemCounts.merge(item.item(), item.count(), Long::sum);
					continue;
				}
				gemCounts.merge(item.item(), item.count(), Long::sum);
				fine += switch (m.group("quality")) {
					case "Rough" -> item.count() / (double) (GemYield.RECIPE * GemYield.RECIPE);
					case "Flawed" -> item.count() / (double) GemYield.RECIPE;
					default -> item.count();
				};
			}
		}
		GemGroup gems = gemCounts.isEmpty() ? null : new GemGroup(fine, sorted(gemCounts));
		return new LootSummary(gems, sorted(itemCounts));
	}

	private static List<ItemCount> sorted(Map<String, Long> counts) {
		List<ItemCount> items = new ArrayList<>();
		counts.forEach((name, count) -> items.add(new ItemCount(name, count)));
		items.sort(Comparator.comparingLong(ItemCount::count).reversed());
		return List.copyOf(items);
	}
}

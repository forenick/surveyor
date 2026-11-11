package dev.shafttools.journal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

import dev.shafttools.detection.CorpseLoot;
import dev.shafttools.detection.ItemCount;
import dev.shafttools.detection.TextPatterns;

/**
 * A shaft's corpse loot for display: every Rough, Flawed and Fine gem (any gem type) merged into
 * one group counted in Fine, and every other item summed by name. Flawless and better gems are
 * ordinary items, so they keep their own entry (Nick's choice, 2026-11-11).
 *
 * @param gems  null when the loot had no gems below Flawless
 * @param items the other items, largest count first
 */
public record LootSummary(GemGroup gems, List<ItemCount> items) {
	/**
	 * @param fine  the gems converted to Fine at {@link GemYield#RECIPE}:1 per step
	 * @param parts each gem item and its count, largest count first
	 */
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

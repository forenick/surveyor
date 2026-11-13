package dev.shaftjournal.money;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import dev.shaftjournal.journal.SummaryText;
import dev.shaftjournal.money.MoneyStats.CorpseCoins;
import dev.shaftjournal.money.MoneyStats.ItemCoins;
import dev.shaftjournal.money.ShaftValue.Part;

// stats tooltips as §-coded lines, items cut until it fits maxLines
public final class CoinBreakdown {
	// display choice
	private static final int MOST_ITEMS = 5;

	private CoinBreakdown() {
	}

	public static List<String> detailed(MoneyStats money, Double perHour, long timeMillis, int maxLines) {
		List<String> lines = List.of();
		for (int items = MOST_ITEMS; items >= 0; items--) {
			lines = detailedWith(money, perHour, timeMillis, items);
			if (lines.size() <= maxLines) break;
		}
		return lines;
	}

	private static List<String> detailedWith(MoneyStats money, Double perHour, long timeMillis, int items) {
		List<String> lines = new ArrayList<>();
		lines.add("§fWhere your coins came from");
		double income = 0;
		double costs = 0;
		for (double coins : money.parts().values()) {
			if (coins > 0) income += coins;
			else costs += coins;
		}
		for (Part part : Part.values()) {
			Map<String, ItemCoins> partItems = money.items().getOrDefault(part, Map.of());
			if (partItems.isEmpty()) continue;
			Double total = money.parts().get(part);
			if (total != null && Math.round(total) == 0 && partItems.values().stream().allMatch(i -> i.coins() != null)) continue;
			String share = total != null && total > 0 && income > 0 ? " §8" + share(total / income) : "";
			lines.add("§7" + part.displayName() + " " + (total == null ? "§8no price" : coins(total)) + share);
			List<Row> rows = part == Part.CORPSES ? corpseRows(money) : itemRows(part, partItems);
			addRows(lines, rows, items, "  ");
		}
		lines.add("");
		if (Math.round(costs) != 0) {
			lines.add("§7Earned " + coins(income));
			lines.add("§7Spent " + coins(costs));
			lines.add("§7Net " + coins(income + costs));
		}
		if (money.perShaft() != null) {
			lines.add("§7Avg per shaft " + coins(money.perShaft()) + " §8(" + money.valued() + " shafts)");
		}
		if (perHour != null) {
			lines.add("§7Avg per hour shafting " + coins(perHour) + " §8(" + SummaryText.duration(timeMillis) + ")");
		}
		long unpriced = money.items().values().stream().flatMap(m -> m.values().stream()).filter(i -> i.coins() == null).count();
		if (unpriced > 0) lines.add("§c" + unpriced + (unpriced == 1 ? " item" : " items") + " without a price");
		lines.add("§8Hold Shift for the short version");
		return lines;
	}

	public static List<String> basic(MoneyStats money, Double perHour, long timeMillis) {
		List<String> lines = new ArrayList<>();
		lines.add("§fWhere your coins came from");
		money.parts().forEach((part, coins) -> {
			if (Math.round(coins) != 0) lines.add("§7" + part.displayName() + " " + coins(coins));
		});
		lines.add("");
		if (money.perShaft() != null) {
			lines.add("§7Avg per shaft " + coins(money.perShaft()) + " §8(" + money.valued() + " shafts)");
		}
		if (perHour != null) {
			lines.add("§7Avg per hour shafting " + coins(perHour) + " §8(" + SummaryText.duration(timeMillis) + ")");
		}
		return lines;
	}

	public static List<String> corpse(String type, int looted, CorpseCoins corpse, int maxLines) {
		List<String> lines = List.of();
		for (int items = MOST_ITEMS * 2; items >= 0; items--) {
			lines = corpseWith(type, looted, corpse, items);
			if (lines.size() <= maxLines) break;
		}
		return lines;
	}

	private static List<String> corpseWith(String type, int looted, CorpseCoins corpse, int items) {
		List<String> lines = new ArrayList<>();
		lines.add("§f" + type + " corpses");
		String counted = corpse.corpses() == looted ? corpse.corpses() + " corpses"
				: corpse.corpses() + " of " + looted + " with prices";
		lines.add("§7Total " + coins(corpse.net()) + " §8(" + counted + ")");
		lines.add("§7Avg " + coins(corpse.each()) + " §7each");
		if (Math.round(corpse.keys()) != 0) {
			lines.add("  §7Loot " + coins(corpse.loot() / corpse.corpses()) + " §7each");
			lines.add("  §7Key " + coins(corpse.keys() / corpse.corpses()) + " §7each");
		}
		if (!corpse.items().isEmpty() && items > 0) {
			lines.add("");
			lines.add("§fLoot");
			addRows(lines, itemRows(Part.CORPSES, corpse.items()), items, "");
		}
		if (corpse.unpriced() > 0) {
			lines.add("§c" + corpse.unpriced() + (corpse.unpriced() == 1 ? " item" : " items") + " without a price");
		}
		return lines;
	}

	private record Row(String text, Double coins) {
	}

	private static List<Row> itemRows(Part part, Map<String, ItemCoins> items) {
		List<Row> rows = new ArrayList<>();
		items.forEach((item, coins) -> rows.add(new Row(count(part, coins.count()) + " " + item, coins.coins())));
		return rows;
	}

	private static List<Row> corpseRows(MoneyStats money) {
		List<Row> rows = new ArrayList<>();
		money.corpses().forEach((type, corpse) -> rows.add(new Row(corpse.corpses() + " " + type
				+ (corpse.corpses() == 1 ? " corpse" : " corpses"), corpse.loot())));
		return rows;
	}

	// biggest first either way, no price last
	private static void addRows(List<String> lines, List<Row> rows, int items, String indent) {
		if (items == 0) return;
		List<Row> sorted = rows.stream().sorted(Comparator.comparing((Row r) -> r.coins() == null)
				.thenComparing(r -> r.coins() == null ? 0 : -Math.abs(r.coins()))).toList();
		// a "1 more" line would take the same room as the item
		int shown = sorted.size() <= items + 1 ? sorted.size() : items;
		for (Row row : sorted.subList(0, shown)) {
			lines.add(indent + "§7" + row.text() + " " + (row.coins() == null ? "§8no price" : coins(row.coins())));
		}
		if (shown == sorted.size()) return;
		Double rest = null;
		for (Row row : sorted.subList(shown, sorted.size())) rest = MoneyStats.plus(rest, row.coins());
		lines.add(indent + "§8" + (sorted.size() - shown) + " more " + (rest == null ? "" : coins(rest)));
	}

	private static String share(double fraction) {
		long percent = Math.round(100 * fraction);
		return percent == 0 ? "<1%" : percent + "%";
	}

	private static String count(Part part, double count) {
		if (part == Part.MINED) return "≈" + SummaryText.decimal(count);
		return SummaryText.number(Math.round(count));
	}

	public static String coins(double coins) {
		return (Math.round(coins) < 0 ? "§c" : "§6") + Coins.signed(coins);
	}
}

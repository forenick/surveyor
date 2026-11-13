package dev.shaftjournal.money;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import dev.shaftjournal.journal.JournalStats.Drops;
import dev.shaftjournal.journal.JournalStats.ItemDrop;
import dev.shaftjournal.journal.SummaryText;
import dev.shaftjournal.money.MoneyStats.CorpseCoins;
import dev.shaftjournal.money.MoneyStats.ItemCoins;
import dev.shaftjournal.money.MoneyStats.TypeCoins;
import dev.shaftjournal.money.ShaftValue.Part;

// stats tooltips as §-coded lines, "" = small gap, items cut until it fits maxHeight
public final class CoinBreakdown {
	public static final String SHIFT_HINT = "§7Hold §fShift §7for the short version";
	// vanilla tooltip line, 2 px under the title
	public static final int LINE_HEIGHT = 10;
	private static final int TITLE_GAP = 2;
	// display choice
	public static final int GAP_HEIGHT = 4;
	// display choice
	private static final int MOST_ITEMS = 5;

	private CoinBreakdown() {
	}

	public static List<String> detailed(MoneyStats money, Double perHour, long timeMillis, int maxHeight) {
		List<String> lines = List.of();
		for (int items = MOST_ITEMS; items >= 0; items--) {
			lines = detailedWith(money, perHour, timeMillis, items);
			if (height(lines) <= maxHeight) break;
		}
		return lines;
	}

	public static int height(List<String> lines) {
		int height = lines.size() > 1 ? TITLE_GAP : 0;
		for (String line : lines) height += line.isEmpty() ? GAP_HEIGHT : LINE_HEIGHT;
		return height;
	}

	private static void gap(List<String> lines) {
		if (!lines.isEmpty() && !lines.getLast().isEmpty()) lines.add("");
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
			gap(lines);
			lines.add("§f" + part.displayName() + " " + (total == null ? "§8no price" : coins(total)) + share);
			List<Row> rows = part == Part.CORPSES ? corpseRows(money) : itemRows(part, partItems);
			addRows(lines, rows, items, "  ", true);
		}
		gap(lines);
		if (Math.round(costs) != 0) {
			lines.add("§7Earned " + coins(income));
			lines.add("§7Spent " + coins(costs));
			lines.add("§fNet " + coins(income + costs));
			gap(lines);
		}
		if (money.perShaft() != null) {
			lines.add("§7Avg per shaft " + coins(money.perShaft()) + " §8(" + money.valued() + " shafts)");
		}
		if (perHour != null) {
			lines.add("§7Avg per hour shafting " + coins(perHour) + " §8(" + SummaryText.duration(timeMillis) + ")");
		}
		long unpriced = money.items().values().stream().flatMap(m -> m.values().stream()).filter(i -> i.coins() == null).count();
		if (unpriced > 0) lines.add("§c" + unpriced + (unpriced == 1 ? " item" : " items") + " without a price");
		gap(lines);
		lines.add(SHIFT_HINT);
		return lines;
	}

	public static List<String> basic(MoneyStats money, Double perHour, long timeMillis) {
		List<String> lines = new ArrayList<>();
		lines.add("§fWhere your coins came from");
		gap(lines);
		money.parts().forEach((part, coins) -> {
			if (Math.round(coins) != 0) lines.add("§7" + part.displayName() + " " + coins(coins));
		});
		gap(lines);
		if (money.perShaft() != null) {
			lines.add("§7Avg per shaft " + coins(money.perShaft()) + " §8(" + money.valued() + " shafts)");
		}
		if (perHour != null) {
			lines.add("§7Avg per hour shafting " + coins(perHour) + " §8(" + SummaryText.duration(timeMillis) + ")");
		}
		return lines;
	}

	// corpse null = no prices, drops null = no loot seen
	public static List<String> corpse(String type, int looted, CorpseCoins corpse, Drops drops, int maxHeight) {
		List<String> lines = List.of();
		for (int items = MOST_ITEMS * 2; items >= 0; items--) {
			lines = corpseWith(type, looted, corpse, drops, items);
			if (height(lines) <= maxHeight) break;
		}
		return lines;
	}

	private static List<String> corpseWith(String type, int looted, CorpseCoins corpse, Drops drops, int items) {
		List<String> lines = new ArrayList<>();
		lines.add("§f" + type + " corpses");
		gap(lines);
		if (corpse != null) {
			String counted = corpse.corpses() == looted ? corpse.corpses() + " corpses"
					: corpse.corpses() + " of " + looted + " with prices";
			lines.add("§7Total " + coins(corpse.net()) + " §8(" + counted + ")");
			lines.add("§7Avg " + coins(corpse.each()) + " §7each");
			if (Math.round(corpse.keys()) != 0) {
				lines.add("  §7Loot " + coins(corpse.loot() / corpse.corpses()) + " §7each");
				lines.add("  §7Key " + coins(corpse.keys() / corpse.corpses()) + " §7each");
			}
		} else {
			lines.add("§8No prices yet");
		}
		if (drops != null && !drops.items().isEmpty() && items > 0) {
			gap(lines);
			lines.add("§fLoot §8drop chance" + (drops.corpses() == looted ? "" : " (" + drops.corpses() + " corpses seen)"));
			List<Row> rows = new ArrayList<>();
			drops.items().entrySet().stream()
					.sorted(Comparator.comparingInt((Map.Entry<String, ItemDrop> e) -> -e.getValue().corpses()))
					.forEach(e -> {
						ItemCoins priced = corpse == null ? null : corpse.items().get(e.getKey());
						rows.add(new Row("§b" + chance(e.getValue().chance(drops.corpses())) + " §7"
								+ SummaryText.number(e.getValue().count()) + " " + e.getKey(), priced == null ? null : priced.coins()));
					});
			addRows(lines, rows, items, "", corpse != null);
		}
		if (corpse != null && corpse.unpriced() > 0) {
			lines.add("§c" + corpse.unpriced() + (corpse.unpriced() == 1 ? " item" : " items") + " without a price");
		}
		return lines;
	}

	public static List<String> shaftType(String name, TypeCoins type, Function<String, String> variantNames) {
		List<String> lines = new ArrayList<>();
		lines.add("§f" + name + " shafts");
		gap(lines);
		lines.add("§7Avg " + coins(type.each()) + " §7per shaft §8(" + type.shafts() + (type.shafts() == 1 ? " shaft)" : " shafts)"));
		lines.add("§7Best " + coins(type.best()));
		lines.add("§7Total " + coins(type.coins()));
		List<String> parts = new ArrayList<>();
		type.parts().forEach((part, coins) -> {
			if (Math.round(coins / type.shafts()) != 0) parts.add("§7" + part.displayName() + " " + coins(coins / type.shafts()));
		});
		if (!parts.isEmpty()) {
			gap(lines);
			lines.add("§fPer shaft");
			lines.addAll(parts);
		}
		if (type.variants().size() > 1) {
			gap(lines);
			lines.add("§fPer variant");
			type.variants().forEach((variant, avg) -> lines.add("§7" + variantNames.apply(variant) + " " + coins(avg.each())
					+ " §8(" + avg.shafts() + (avg.shafts() == 1 ? " shaft)" : " shafts)")));
		}
		return lines;
	}

	// under 10% w/ a decimal, rare drops matter
	static String chance(double fraction) {
		double percent = 100 * fraction;
		return percent >= 9.95 ? Math.round(percent) + "%" : String.format(Locale.ROOT, "%.1f%%", percent);
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
	private static void addRows(List<String> lines, List<Row> rows, int items, String indent, boolean withCoins) {
		if (items == 0) return;
		List<Row> sorted = rows.stream().sorted(Comparator.comparing((Row r) -> r.coins() == null)
				.thenComparing(r -> r.coins() == null ? 0 : -Math.abs(r.coins()))).toList();
		// a "1 more" line would take the same room as the item
		int shown = sorted.size() <= items + 1 ? sorted.size() : items;
		for (Row row : sorted.subList(0, shown)) {
			String coins = !withCoins ? "" : " " + (row.coins() == null ? "§8no price" : coins(row.coins()));
			lines.add(indent + "§7" + row.text() + coins);
		}
		if (shown == sorted.size()) return;
		Double rest = null;
		for (Row row : sorted.subList(shown, sorted.size())) rest = MoneyStats.plus(rest, row.coins());
		lines.add(indent + "§8" + (sorted.size() - shown) + " more" + (rest == null || !withCoins ? "" : " " + coins(rest)));
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

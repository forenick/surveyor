package dev.skysurveyor.money;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import dev.skysurveyor.journal.JournalEntry;
import dev.skysurveyor.journal.JournalStats;
import dev.skysurveyor.journal.JournalStats.SessionSummary;
import dev.skysurveyor.money.ShaftValue.CorpseValue;
import dev.skysurveyor.money.ShaftValue.Part;

// values null = no prices at all yet
// parts = all-time total per part, in Part order, items = the same per item
public record MoneyStats(Double coins, int valued, Map<Part, Double> parts, Map<String, CorpseCoins> corpses, Best bestShaft,
		BestRate bestRate, Map<Part, Map<String, ItemCoins>> items, Map<String, TypeCoins> types) {
	public record CorpseCoins(int corpses, double loot, double keys, int unpriced, Map<String, ItemCoins> items) {
		public double net() {
			return loot + keys;
		}

		public double each() {
			return net() / corpses;
		}
	}

	// per shaft type code, variants by variant letter
	public record TypeCoins(int shafts, double coins, double best, Map<Part, Double> parts, Map<String, Average> variants) {
		public double each() {
			return coins / shafts;
		}
	}

	public record Average(int shafts, double coins) {
		public double each() {
			return coins / shafts;
		}
	}

	// coins null = never had a price
	public record ItemCoins(double count, Double coins) {
		ItemCoins plus(ShaftValue.Line line) {
			return new ItemCoins(count + line.count(), MoneyStats.plus(coins, line.coins()));
		}
	}

	public record Best(JournalEntry entry, double coins) {
	}

	public record BestRate(SessionSummary session, double perHour) {
	}

	public Double perShaft() {
		return coins == null || valued == 0 ? null : coins / valued;
	}

	public static Double net(ShaftValue value) {
		return value == null ? null : value.net();
	}

	// null when none of them has a value
	public static Double coins(List<JournalEntry> entries, Function<JournalEntry, ShaftValue> values) {
		Double total = null;
		for (JournalEntry entry : entries) total = plus(total, net(values.apply(entry)));
		return total;
	}

	public static Double plus(Double a, Double b) {
		if (a == null) return b;
		return b == null ? a : a + b;
	}

	public static Double perHour(SessionSummary session, Function<JournalEntry, ShaftValue> values) {
		Double coins = coins(session.entries(), values);
		return coins == null ? null : session.perHour(coins, session.end());
	}

	public static MoneyStats of(List<JournalEntry> entries, List<SessionSummary> sessions,
			Function<JournalEntry, ShaftValue> values) {
		Double coins = null;
		int valued = 0;
		Map<Part, Double> parts = new EnumMap<>(Part.class);
		Map<String, CorpseCoins> corpses = new LinkedHashMap<>();
		Map<Part, Map<String, ItemCoins>> items = new EnumMap<>(Part.class);
		Map<String, TypeCoins> types = new LinkedHashMap<>();
		Best best = null;
		for (JournalEntry entry : entries) {
			ShaftValue value = values.apply(entry);
			if (value == null) continue;
			valued++;
			coins = plus(coins, value.net());
			for (ShaftValue.Line line : value.lines()) {
				if (line.coins() != null) parts.merge(line.part(), line.coins(), Double::sum);
				add(items.computeIfAbsent(line.part(), part -> new LinkedHashMap<>()), line);
			}
			for (CorpseValue corpse : value.corpses()) {
				CorpseCoins had = corpses.get(corpse.type());
				Map<String, ItemCoins> loot = had == null ? new LinkedHashMap<>() : had.items();
				for (ShaftValue.Line line : corpse.items()) add(loot, line);
				corpses.put(corpse.type(), had == null ? new CorpseCoins(1, corpse.loot(), corpse.key(), corpse.unpriced(), loot)
						: new CorpseCoins(had.corpses() + 1, had.loot() + corpse.loot(), had.keys() + corpse.key(),
								had.unpriced() + corpse.unpriced(), loot));
			}
			if (!value.lines().isEmpty() && (best == null || value.net() > best.coins())) best = new Best(entry, value.net());
			String type = JournalStats.type(entry);
			if (type != null) types.put(type, plus(types.get(type), JournalStats.variant(entry), value));
		}
		BestRate bestRate = null;
		for (SessionSummary session : sessions) {
			if (session.shafts() < JournalStats.MIN_SHAFTS_FOR_RATE_RECORD) continue;
			Double rate = perHour(session, values);
			if (rate != null && (bestRate == null || rate > bestRate.perHour())) bestRate = new BestRate(session, rate);
		}
		return new MoneyStats(coins, valued, parts, corpses, best, bestRate, items, types);
	}

	private static TypeCoins plus(TypeCoins had, String variant, ShaftValue value) {
		double net = value.net();
		Map<Part, Double> parts = had == null ? new EnumMap<>(Part.class) : had.parts();
		for (ShaftValue.Line line : value.lines()) {
			if (line.coins() != null) parts.merge(line.part(), line.coins(), Double::sum);
		}
		Map<String, Average> variants = had == null ? new LinkedHashMap<>() : had.variants();
		if (variant != null) {
			variants.merge(variant, new Average(1, net), (a, b) -> new Average(a.shafts() + 1, a.coins() + net));
		}
		if (had == null) return new TypeCoins(1, net, net, parts, variants);
		return new TypeCoins(had.shafts() + 1, had.coins() + net, Math.max(had.best(), net), parts, variants);
	}

	private static void add(Map<String, ItemCoins> items, ShaftValue.Line line) {
		items.merge(line.item(), new ItemCoins(line.count(), line.coins()), (had, one) -> had.plus(line));
	}
}

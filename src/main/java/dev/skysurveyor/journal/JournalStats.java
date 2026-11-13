package dev.skysurveyor.journal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

import dev.skysurveyor.detection.Corpse;
import dev.skysurveyor.detection.CorpseLoot;
import dev.skysurveyor.detection.ItemCount;
import dev.skysurveyor.detection.TextPatterns;

public final class JournalStats {
	// display choice, not a game number
	public static final long MIN_RATE_MILLIS = 5 * 60_000L;

	// so 1 lucky spawn isnt a record. display choice, not a game number
	public static final int MIN_TIMED_FOR_RECORD = 3;

	// nicks pick (2026-11-11), display choice, not a game number
	public static final int MIN_SHAFTS_FOR_RATE_RECORD = 5;

	// display order, not game data
	public static final List<String> CORPSE_ORDER = List.of("Lapis", "Umber", "Tungsten", "Vanguard");

	private JournalStats() {
	}

	// null under MIN_RATE_MILLIS
	public static Double perHour(double amount, long millis) {
		return millis < MIN_RATE_MILLIS ? null : amount * 3_600_000.0 / millis;
	}

	public record CorpseCount(int looted, int total) {
	}

	public record SessionSummary(long id, List<JournalEntry> entries) {
		public long start() {
			return id;
		}

		public long end() {
			JournalEntry last = entries.getLast();
			return Math.max(id, last.enteredAt() + last.durationMillis());
		}

		public long lengthMillis() {
			return end() - start();
		}

		public int shafts() {
			return entries.size();
		}

		public int timedSpawns() {
			return (int) entries.stream().filter(e -> e.searchMillis() != null).count();
		}

		public Long averageSearchMillis() {
			return averageSearch(entries);
		}

		public Double shaftsPerHour() {
			return shaftsPerHour(end());
		}

		public Double shaftsPerHour(long now) {
			return perHour(shafts(), now);
		}

		public Double perHour(double amount, long now) {
			return JournalStats.perHour(amount, Math.max(end(), now) - start());
		}

		public long powder() {
			return entries.stream().mapToLong(JournalStats::powder).sum();
		}

		public int looted(String type) {
			return entries.stream().mapToInt(e -> looted(e, type)).sum();
		}

		private static int looted(JournalEntry entry, String type) {
			return (int) entry.corpses().stream().filter(c -> c.looted() && c.type().equals(type)).count();
		}
	}

	public record Best(JournalEntry entry, long value) {
	}

	// corpses = with loot seen, item corpses = how many of those dropped it
	public record Drops(int corpses, Map<String, ItemDrop> items) {
	}

	public record ItemDrop(int corpses, long count) {
		public double chance(int of) {
			return corpses / (double) of;
		}
	}

	public record AllTime(
			int shafts,
			int sessions,
			long timeMillis,
			long powder,
			Map<String, Integer> looted,
			Long averageSearchMillis,
			JournalEntry fastest,
			List<SessionSummary> oldestFirst,
			Best mostPowder,
			Best mostLapis,
			SessionSummary bestRate,
			SessionSummary fastestSession,
			SessionSummary mostShafts,
			JournalEntry mostGems,
			Map<String, Map<String, Integer>> types,
			Map<String, Integer> corpsesSeen,
			Map<String, Integer> shaftsWithCorpse,
			Map<String, Drops> drops) {
		public int typedShafts() {
			return types.values().stream().flatMap(v -> v.values().stream()).mapToInt(Integer::intValue).sum();
		}
	}

	public static List<SessionSummary> sessions(List<JournalEntry> entries) {
		Map<Long, List<JournalEntry>> bySession = new LinkedHashMap<>();
		for (JournalEntry entry : entries) {
			bySession.computeIfAbsent(entry.sessionId(), id -> new ArrayList<>()).add(entry);
		}
		List<SessionSummary> sessions = new ArrayList<>();
		bySession.forEach((id, list) -> sessions.add(new SessionSummary(id, List.copyOf(list))));
		sessions.sort(Comparator.comparingLong(SessionSummary::start).reversed());
		return sessions;
	}

	public static long powder(JournalEntry entry) {
		return powder(entry.loots(), entry.exitPowder());
	}

	public static long powder(List<CorpseLoot> loots, long exitPowder) {
		return loots.stream().mapToLong(CorpseLoot::powder).sum() + exitPowder;
	}

	public static Map<String, CorpseCount> corpseCounts(List<Corpse> corpses) {
		Map<String, int[]> counts = new LinkedHashMap<>();
		for (Corpse corpse : corpses) {
			int[] count = counts.computeIfAbsent(corpse.type(), t -> new int[2]);
			if (corpse.looted()) count[0]++;
			count[1]++;
		}
		Map<String, CorpseCount> result = new LinkedHashMap<>();
		counts.forEach((type, count) -> result.put(type, new CorpseCount(count[0], count[1])));
		return result;
	}

	public static Map<String, CorpseCount> corpses(JournalEntry entry) {
		Map<String, CorpseCount> counts = corpseCounts(entry.corpses());
		Map<String, CorpseCount> ordered = new LinkedHashMap<>();
		for (String type : ordered(counts.keySet())) ordered.put(type, counts.get(type));
		return ordered;
	}

	public static String type(JournalEntry entry) {
		return idPart(entry, "type");
	}

	public static String variant(JournalEntry entry) {
		return idPart(entry, "variant");
	}

	private static String idPart(JournalEntry entry, String group) {
		if (entry.shaftId() == null) return null;
		Matcher m = TextPatterns.defaults().match("shaft.id", entry.shaftId());
		return m == null ? null : m.group(group);
	}

	public static AllTime allTime(List<JournalEntry> entries) {
		List<SessionSummary> sessions = sessions(entries);
		List<SessionSummary> oldestFirst = new ArrayList<>(sessions);
		oldestFirst.sort(Comparator.comparingLong(SessionSummary::start));

		Map<String, Integer> looted = new LinkedHashMap<>();
		Map<String, Integer> seen = new LinkedHashMap<>();
		Map<String, Map<String, Integer>> types = new LinkedHashMap<>();
		Map<String, Integer> shaftsWithCorpse = new LinkedHashMap<>();
		Map<String, Integer> lootSeen = new LinkedHashMap<>();
		Map<String, Map<String, ItemDrop>> dropped = new LinkedHashMap<>();
		JournalEntry fastest = null;
		Best mostPowder = null;
		Best mostLapis = null;
		JournalEntry mostGems = null;
		long powder = 0;
		for (JournalEntry entry : entries) {
			powder += powder(entry);
			for (Corpse corpse : entry.corpses()) {
				seen.merge(corpse.type(), 1, Integer::sum);
				if (corpse.looted()) looted.merge(corpse.type(), 1, Integer::sum);
			}
			entry.corpses().stream().map(Corpse::type).distinct()
					.forEach(type -> shaftsWithCorpse.merge(type, 1, Integer::sum));
			for (CorpseLoot loot : entry.loots()) {
				lootSeen.merge(loot.type(), 1, Integer::sum);
				Map<String, ItemDrop> items = dropped.computeIfAbsent(loot.type(), t -> new LinkedHashMap<>());
				Map<String, Long> once = new LinkedHashMap<>();
				for (ItemCount item : loot.items()) once.merge(item.item(), item.count(), Long::sum);
				once.forEach((item, count) -> items.merge(item, new ItemDrop(1, count),
						(had, one) -> new ItemDrop(had.corpses() + 1, had.count() + count)));
			}
			String type = type(entry);
			String variant = variant(entry);
			if (type != null && variant != null) {
				types.computeIfAbsent(type, t -> new LinkedHashMap<>()).merge(variant, 1, Integer::sum);
			}
			if (entry.searchMillis() != null && (fastest == null || entry.searchMillis() < fastest.searchMillis())) {
				fastest = entry;
			}
			long shaftPowder = powder(entry);
			if (mostPowder == null || shaftPowder > mostPowder.value()) mostPowder = new Best(entry, shaftPowder);
			long lapis = SessionSummary.looted(entry, "Lapis");
			if (lapis > 0 && (mostLapis == null || lapis > mostLapis.value())) mostLapis = new Best(entry, lapis);
			if (entry.gems() != null && entry.gems().fineEquivalent() > 0
					&& (mostGems == null || entry.gems().fineEquivalent() > mostGems.gems().fineEquivalent())) mostGems = entry;
		}

		SessionSummary bestRate = null;
		double bestRateValue = 0;
		SessionSummary fastestSession = null;
		long fastestAverage = 0;
		SessionSummary mostShafts = null;
		for (SessionSummary session : sessions) {
			Double rate = session.shaftsPerHour();
			if (rate != null && session.shafts() >= MIN_SHAFTS_FOR_RATE_RECORD && (bestRate == null || rate > bestRateValue)) {
				bestRate = session;
				bestRateValue = rate;
			}
			if (session.timedSpawns() >= MIN_TIMED_FOR_RECORD) {
				long average = session.averageSearchMillis();
				if (fastestSession == null || average < fastestAverage) {
					fastestSession = session;
					fastestAverage = average;
				}
			}
			if (mostShafts == null || session.shafts() > mostShafts.shafts()) mostShafts = session;
		}

		return new AllTime(entries.size(), sessions.size(),
				sessions.stream().mapToLong(SessionSummary::lengthMillis).sum(), powder,
				orderedMap(looted), averageSearch(entries), fastest, List.copyOf(oldestFirst),
				mostPowder, mostLapis, bestRate, fastestSession, mostShafts, mostGems, byTotal(types), orderedMap(seen), orderedMap(shaftsWithCorpse),
				drops(lootSeen, dropped));
	}

	private static Map<String, Drops> drops(Map<String, Integer> lootSeen, Map<String, Map<String, ItemDrop>> dropped) {
		Map<String, Drops> drops = new LinkedHashMap<>();
		for (String type : ordered(lootSeen.keySet())) drops.put(type, new Drops(lootSeen.get(type), dropped.get(type)));
		return drops;
	}

	public static Long recentAverage(List<SessionSummary> newestFirst, int count) {
		return averageSearch(newestFirst.stream().limit(count).flatMap(s -> s.entries().stream()).toList());
	}

	public static Long averageSearch(List<JournalEntry> entries) {
		long total = 0;
		int timed = 0;
		for (JournalEntry entry : entries) {
			if (entry.searchMillis() == null) continue;
			total += entry.searchMillis();
			timed++;
		}
		return timed == 0 ? null : total / timed;
	}

	private static List<String> ordered(Iterable<String> types) {
		List<String> known = new ArrayList<>();
		List<String> other = new ArrayList<>();
		for (String type : types) (CORPSE_ORDER.contains(type) ? known : other).add(type);
		known.sort(Comparator.comparingInt(CORPSE_ORDER::indexOf));
		other.sort(null);
		known.addAll(other);
		return known;
	}

	private static Map<String, Map<String, Integer>> byTotal(Map<String, Map<String, Integer>> types) {
		List<Map.Entry<String, Map<String, Integer>>> list = new ArrayList<>(types.entrySet());
		list.sort(Comparator.comparingInt((Map.Entry<String, Map<String, Integer>> e) ->
				e.getValue().values().stream().mapToInt(Integer::intValue).sum()).reversed());
		Map<String, Map<String, Integer>> sorted = new LinkedHashMap<>();
		for (Map.Entry<String, Map<String, Integer>> e : list) {
			Map<String, Integer> variants = new LinkedHashMap<>();
			e.getValue().keySet().stream().sorted(Comparator.comparingInt(v -> "12C".indexOf(v) < 0 ? 9 : "12C".indexOf(v)))
					.forEach(v -> variants.put(v, e.getValue().get(v)));
			sorted.put(e.getKey(), variants);
		}
		return sorted;
	}

	private static Map<String, Integer> orderedMap(Map<String, Integer> counts) {
		Map<String, Integer> ordered = new LinkedHashMap<>();
		for (String type : ordered(counts.keySet())) ordered.put(type, counts.get(type));
		return ordered;
	}
}

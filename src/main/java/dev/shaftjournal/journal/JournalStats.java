package dev.shaftjournal.journal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

import dev.shaftjournal.detection.Corpse;
import dev.shaftjournal.detection.CorpseLoot;
import dev.shaftjournal.detection.TextPatterns;

// session summaries and all time stats for the journal screen, from saved entries.
// every number here is counted from ur own journal, none of it is a game constant
public final class JournalStats {
	// shafts per hour only shows for sessions at least this long. display choice, not a game number
	public static final long MIN_RATE_MILLIS = 5 * 60_000L;

	// a session needs this many timed spawns to count for "fastest session", so one lucky spawn
	// isnt a record. display choice, not a game number
	public static final int MIN_TIMED_FOR_RECORD = 3;

	// a session needs this many shafts to count for "Shafts / h", so a short lucky session isnt a
	// record. my pick (2026-11-11), display choice, not a game number
	public static final int MIN_SHAFTS_FOR_RATE_RECORD = 5;

	// corpse types in the order the screen lists them, unknown ones come after (alphabetical)
	public static final List<String> CORPSE_ORDER = List.of("Lapis", "Umber", "Tungsten", "Vanguard");

	private JournalStats() {
	}

	public record CorpseCount(int looted, int total) {
	}

	// session id is its start time, entries are in the order they got saved
	public record SessionSummary(long id, List<JournalEntry> entries) {
		public long start() {
			return id;
		}

		// when the last shaft of the session was left
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

		// null when no spawn in the session got timed
		public Long averageSearchMillis() {
			return averageSearch(entries);
		}

		// null for sessions shorter than MIN_RATE_MILLIS
		public Double shaftsPerHour() {
			return shaftsPerHour(end());
		}

		// same but also counts the time since the last shaft up to now
		public Double shaftsPerHour(long now) {
			return perHour(shafts(), now);
		}

		// any amount per hour of session, counted up to now. null in the same cases as shaftsPerHour
		public Double perHour(double amount, long now) {
			long length = Math.max(end(), now) - start();
			return length < MIN_RATE_MILLIS ? null : amount * 3_600_000.0 / length;
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

	// a shaft that holds a record, plus the record value
	public record Best(JournalEntry entry, long value) {
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
			Map<String, Integer> shaftsWithCorpse) {
		// shafts w/ a known type
		public int typedShafts() {
			return types.values().stream().flatMap(v -> v.values().stream()).mapToInt(Integer::intValue).sum();
		}
	}

	// groups entries (in file order) into sessions, newest first
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

	// corpse powder + the powder for leaving
	public static long powder(JournalEntry entry) {
		return powder(entry.loots(), entry.exitPowder());
	}

	// Glacite Powder from corpses + the "BYE!" bonus, for a saved shaft or one ur still in
	public static long powder(List<CorpseLoot> loots, long exitPowder) {
		return loots.stream().mapToLong(CorpseLoot::powder).sum() + exitPowder;
	}

	// looted and seen corpses per type, in the order the types first show up (tab list order)
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

	// same but in CORPSE_ORDER
	public static Map<String, CorpseCount> corpses(JournalEntry entry) {
		Map<String, CorpseCount> counts = corpseCounts(entry.corpses());
		Map<String, CorpseCount> ordered = new LinkedHashMap<>();
		for (String type : ordered(counts.keySet())) ordered.put(type, counts.get(type));
		return ordered;
	}

	// the 4 letter type, f.e. "JASP". null w/o a shaft id
	public static String type(JournalEntry entry) {
		return idPart(entry, "type");
	}

	// the variant, f.e. "1", "2" or "C". null w/o a shaft id
	public static String variant(JournalEntry entry) {
		return idPart(entry, "variant");
	}

	// a named group of the shaft.id pattern, same id shape the scoreboard parser reads
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
				mostPowder, mostLapis, bestRate, fastestSession, mostShafts, mostGems, byTotal(types), orderedMap(seen), orderedMap(shaftsWithCorpse));
	}

	// avg spawn time over every timed shaft in the newest count sessions (list is newest first)
	public static Long recentAverage(List<SessionSummary> newestFirst, int count) {
		return averageSearch(newestFirst.stream().limit(count).flatMap(s -> s.entries().stream()).toList());
	}

	static Long averageSearch(List<JournalEntry> entries) {
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

	// types w/ the most shafts first, variants go 1, 2, C, then the rest
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

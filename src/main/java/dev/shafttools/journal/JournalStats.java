package dev.shafttools.journal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.shafttools.detection.Corpse;

/**
 * Session summaries and all-time stats for the journal screen, computed from saved entries.
 * Every number here is counted from the player's own journal; nothing is a game constant.
 */
public final class JournalStats {
	/** Shafts per hour is only shown for sessions at least this long. A display choice, not a game number. */
	public static final long MIN_RATE_MILLIS = 5 * 60_000L;

	/** Corpse types in the order the screen lists them. Unknown types follow, alphabetically. */
	public static final List<String> CORPSE_ORDER = List.of("Lapis", "Umber", "Tungsten", "Vanguard");

	private JournalStats() {
	}

	/** Corpses looted and seen, for one type. */
	public record CorpseCount(int looted, int total) {
	}

	/** One session. The session ID is its start time; entries are in the order they were saved. */
	public record SessionSummary(long id, List<JournalEntry> entries) {
		public long start() {
			return id;
		}

		/** When the last shaft of the session was left. */
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

		/** Null when no spawn in the session was timed. */
		public Long averageSearchMillis() {
			return averageSearch(entries);
		}

		/** Null for sessions shorter than {@link #MIN_RATE_MILLIS}. */
		public Double shaftsPerHour() {
			long length = lengthMillis();
			return length < MIN_RATE_MILLIS ? null : shafts() * 3_600_000.0 / length;
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

	/** A shaft that holds a record, and the record value. */
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
			Map<String, Integer> variants,
			Map<String, Integer> corpsesSeen) {
	}

	/** Groups entries (in file order) into sessions, newest first. */
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

	/** Corpse powder plus the powder for leaving. */
	public static long powder(JournalEntry entry) {
		return entry.corpsePowder() + entry.exitPowder();
	}

	/** Looted and seen corpses per type, in {@link #CORPSE_ORDER}. */
	public static Map<String, CorpseCount> corpses(JournalEntry entry) {
		Map<String, int[]> counts = new LinkedHashMap<>();
		for (Corpse corpse : entry.corpses()) {
			int[] count = counts.computeIfAbsent(corpse.type(), t -> new int[2]);
			if (corpse.looted()) count[0]++;
			count[1]++;
		}
		Map<String, CorpseCount> ordered = new LinkedHashMap<>();
		for (String type : ordered(counts.keySet())) {
			int[] count = counts.get(type);
			ordered.put(type, new CorpseCount(count[0], count[1]));
		}
		return ordered;
	}

	/** The variant after the underscore, e.g. "1", "2" or "C"; null without a shaft ID. */
	public static String variant(JournalEntry entry) {
		String id = entry.shaftId();
		if (id == null) return null;
		int underscore = id.lastIndexOf('_');
		return underscore < 0 ? null : id.substring(underscore + 1);
	}

	public static AllTime allTime(List<JournalEntry> entries) {
		List<SessionSummary> sessions = sessions(entries);
		List<SessionSummary> oldestFirst = new ArrayList<>(sessions);
		oldestFirst.sort(Comparator.comparingLong(SessionSummary::start));

		Map<String, Integer> looted = new LinkedHashMap<>();
		Map<String, Integer> seen = new LinkedHashMap<>();
		Map<String, Integer> variants = new LinkedHashMap<>();
		JournalEntry fastest = null;
		Best mostPowder = null;
		Best mostLapis = null;
		long powder = 0;
		for (JournalEntry entry : entries) {
			powder += powder(entry);
			for (Corpse corpse : entry.corpses()) {
				seen.merge(corpse.type(), 1, Integer::sum);
				if (corpse.looted()) looted.merge(corpse.type(), 1, Integer::sum);
			}
			String variant = variant(entry);
			if (variant != null) variants.merge(variant, 1, Integer::sum);
			if (entry.searchMillis() != null && (fastest == null || entry.searchMillis() < fastest.searchMillis())) {
				fastest = entry;
			}
			long shaftPowder = powder(entry);
			if (mostPowder == null || shaftPowder > mostPowder.value()) mostPowder = new Best(entry, shaftPowder);
			long lapis = SessionSummary.looted(entry, "Lapis");
			if (lapis > 0 && (mostLapis == null || lapis > mostLapis.value())) mostLapis = new Best(entry, lapis);
		}

		SessionSummary bestRate = null;
		for (SessionSummary session : sessions) {
			Double rate = session.shaftsPerHour();
			if (rate != null && (bestRate == null || rate > bestRate.shaftsPerHour())) bestRate = session;
		}

		return new AllTime(entries.size(), sessions.size(),
				sessions.stream().mapToLong(SessionSummary::lengthMillis).sum(), powder,
				orderedMap(looted), averageSearch(entries), fastest, List.copyOf(oldestFirst),
				mostPowder, mostLapis, bestRate, variants, orderedMap(seen));
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

	private static Map<String, Integer> orderedMap(Map<String, Integer> counts) {
		Map<String, Integer> ordered = new LinkedHashMap<>();
		for (String type : ordered(counts.keySet())) ordered.put(type, counts.get(type));
		return ordered;
	}
}

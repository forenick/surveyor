package dev.shaftjournal.hud;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.shaftjournal.detection.Corpse;
import dev.shaftjournal.detection.CorpseLoot;
import dev.shaftjournal.detection.Shaft;
import dev.shaftjournal.detection.TextPatterns;
import dev.shaftjournal.hud.HudValue.Range;
import dev.shaftjournal.journal.JournalEntry;
import dev.shaftjournal.journal.JournalStats;
import dev.shaftjournal.journal.JournalStats.SessionSummary;
import dev.shaftjournal.journal.LootSummary;
import dev.shaftjournal.screen.ShaftNames;

/**
 * One moment's values for the HUD, by {@link HudValue} and, for countable values, {@link Range}.
 * A missing value is null. Plain Java.
 */
public final class HudData {
	/** What the editor shows for values that aren't known right now (the mockup's numbers). */
	public static final HudData EXAMPLE = example();

	/** A value in one range; the range is null for values without ranges. */
	private record Key(HudValue value, Range range) {
	}

	private final Map<Key, Object> values = new HashMap<>();
	/** Values that are real but say nothing yet (a session's zeros before its first shaft); the editor shows examples. */
	private final Set<Key> placeholders = new HashSet<>();

	/** The value in its default range. */
	public Object get(HudValue value) {
		return get(value, value.defaultRange());
	}

	/** @param range ignored for values without ranges; null means the default range */
	public Object get(HudValue value, Range range) {
		return values.get(key(value, range));
	}

	/** This value, or the example when it's missing or only a placeholder. */
	public Object getOrExample(HudValue value, Range range) {
		Key key = key(value, range);
		Object v = values.get(key);
		return v != null && !placeholders.contains(key) ? v : EXAMPLE.values.get(key);
	}

	/**
	 * The value as a number for colour rules, or null when it's missing or not a number.
	 *
	 * @param example the editor: missing values and placeholders give the example's number
	 */
	public Double number(HudValue value, Range range, boolean example) {
		Key key = key(value, range);
		HudData from = example && (values.get(key) == null || placeholders.contains(key)) ? EXAMPLE : this;
		return from.values.get(key) instanceof Number n ? n.doubleValue() : null;
	}

	/** Leaves out ranges the value doesn't have, so a hand-edited file can't ask for one. */
	private static Key key(HudValue value, Range range) {
		return new Key(value, value.hasRange(range) ? range : value.defaultRange());
	}

	private HudData put(HudValue value, Object v) {
		return put(value, value.defaultRange(), v);
	}

	private HudData put(HudValue value, Range range, Object v) {
		if (v != null) values.put(key(value, range), v);
		return this;
	}

	/** Shafts, powder and Fine from corpses added up over some shafts. */
	public record Totals(long shafts, long powder, double fine) {
		public static final Totals NONE = new Totals(0, 0, 0);

		public Totals plus(Totals other) {
			return new Totals(shafts + other.shafts, powder + other.powder, fine + other.fine);
		}

		public static Totals of(List<JournalEntry> entries, TextPatterns patterns) {
			long powder = 0;
			double fine = 0;
			for (JournalEntry entry : entries) {
				powder += JournalStats.powder(entry);
				fine += fine(entry.loots(), patterns);
			}
			return new Totals(entries.size(), powder, fine);
		}

		/** The shaft the player is in, so far: corpse loot, plus the powder for leaving once it's known. */
		public static Totals of(Shaft shaft, TextPatterns patterns) {
			return new Totals(1, JournalStats.powder(shaft.loots(), shaft.exitPowder()), fine(shaft.loots(), patterns));
		}

		private static double fine(List<CorpseLoot> loots, TextPatterns patterns) {
			LootSummary loot = LootSummary.of(loots, patterns);
			return loot.gems() == null ? 0 : loot.gems().fine();
		}
	}

	/**
	 * A session's totals. They only change when a shaft is saved, so the HUD keeps them between
	 * refreshes instead of adding up every shaft again.
	 *
	 * @param summary null before the first shaft
	 */
	public record SessionTotals(Totals totals, SessionSummary summary) {
		public static final SessionTotals NONE = new SessionTotals(Totals.NONE, null);

		public long shafts() {
			return totals.shafts();
		}

		/** Null before a spawn was timed. */
		public Long averageSpawn() {
			return summary == null ? null : summary.averageSearchMillis();
		}

		/** @param entries the finished shafts of one session, in order */
		public static SessionTotals of(List<JournalEntry> entries, TextPatterns patterns) {
			if (entries.isEmpty()) return NONE;
			return new SessionTotals(Totals.of(entries, patterns), new SessionSummary(entries.getFirst().sessionId(), entries));
		}
	}

	/**
	 * @param session    the current session's totals
	 * @param allTime    every saved shaft's totals
	 * @param thisShaft  the shaft the player is in so far, else the last saved shaft; null when there's none
	 * @param spawnTimer the running or last timed spawn, in milliseconds; null when none
	 * @param shaft      the shaft the player is in, or null
	 * @param now        the same clock as the shaft's times
	 */
	public static HudData live(SessionTotals session, Totals allTime, Totals thisShaft, Long spawnTimer, Shaft shaft,
			long now, ShaftNames names) {
		HudData data = new HudData();
		Totals totals = session.totals();
		data.put(HudValue.SESSION_SHAFTS, totals.shafts());
		data.put(HudValue.SESSION_POWDER, totals.powder());
		data.put(HudValue.SESSION_CORPSE_FINE, totals.fine());
		// The HUD shows 0 shafts, but the editor previews the whole box with examples instead of mixing them in.
		if (session.summary() == null) data.placeholders(Range.SESSION);
		data.put(HudValue.SESSION_SHAFTS, Range.ALL_TIME, allTime.shafts());
		data.put(HudValue.SESSION_POWDER, Range.ALL_TIME, allTime.powder());
		data.put(HudValue.SESSION_CORPSE_FINE, Range.ALL_TIME, allTime.fine());
		if (allTime.shafts() == 0) data.placeholders(Range.ALL_TIME);
		if (thisShaft != null) {
			data.put(HudValue.SESSION_POWDER, Range.SHAFT, thisShaft.powder());
			data.put(HudValue.SESSION_CORPSE_FINE, Range.SHAFT, thisShaft.fine());
		}
		data.put(HudValue.AVERAGE_SPAWN, session.averageSpawn());
		if (session.summary() != null) {
			// Counted up to now, so the rates drop while a search drags on.
			SessionSummary summary = session.summary();
			data.put(HudValue.SHAFTS_PER_HOUR, summary.shaftsPerHour(now));
			Double powderPerHour = summary.perHour(totals.powder(), now);
			if (powderPerHour != null) data.put(HudValue.SESSION_POWDER, Range.PER_HOUR, Math.round(powderPerHour));
			data.put(HudValue.SESSION_CORPSE_FINE, Range.PER_HOUR, summary.perHour(totals.fine(), now));
		}
		data.put(HudValue.SPAWN_TIMER, spawnTimer);
		if (shaft != null) {
			if (shaft.id() != null) data.put(HudValue.SHAFT_TYPE, names.name(shaft.id().type()));
			data.put(HudValue.SHAFT_TIME, Math.max(0, now - shaft.enteredAt()));
			data.put(HudValue.SHAFT_CORPSES, corpses(shaft.corpses()));
			if (shaft.cold() != null) data.cold(shaft.cold());
			data.put(HudValue.SHAFT_CAMPFIRES, (long) shaft.campfiresPlaced().size());
		}
		return data;
	}

	/** Marks every value in this range as a placeholder. */
	private void placeholders(Range range) {
		for (HudValue value : HudValue.values()) {
			if (value.hasRange(range)) placeholders.add(key(value, range));
		}
	}

	/** "2/4 Lapis, Umber": looted out of all, then the types present. Null before the tab list shows them. */
	static String corpses(List<Corpse> corpses) {
		if (corpses.isEmpty()) return null;
		long looted = corpses.stream().filter(Corpse::looted).count();
		Set<String> types = new LinkedHashSet<>();
		for (String type : JournalStats.CORPSE_ORDER) {
			if (corpses.stream().anyMatch(c -> c.type().equals(type))) types.add(type);
		}
		corpses.forEach(c -> types.add(c.type()));
		return looted + "/" + corpses.size() + " " + String.join(", ", new ArrayList<>(types));
	}

	private static HudData example() {
		return new HudData()
				.put(HudValue.SESSION_SHAFTS, 6L)
				.put(HudValue.SESSION_SHAFTS, Range.ALL_TIME, 412L)
				.put(HudValue.SHAFTS_PER_HOUR, 19.4)
				.put(HudValue.AVERAGE_SPAWN, 85_000L)
				.put(HudValue.SESSION_POWDER, Range.SHAFT, 7_120L)
				.put(HudValue.SESSION_POWDER, 43_436L)
				.put(HudValue.SESSION_POWDER, Range.PER_HOUR, 137_166L)
				.put(HudValue.SESSION_POWDER, Range.ALL_TIME, 1_204_310L)
				.put(HudValue.SESSION_CORPSE_FINE, Range.SHAFT, 3.1)
				.put(HudValue.SESSION_CORPSE_FINE, 15.6)
				.put(HudValue.SESSION_CORPSE_FINE, Range.PER_HOUR, 49.3)
				.put(HudValue.SESSION_CORPSE_FINE, Range.ALL_TIME, 412.8)
				.put(HudValue.SPAWN_TIMER, 102_000L)
				.put(HudValue.SHAFT_TYPE, "Jade")
				.put(HudValue.SHAFT_TIME, 134_000L)
				.put(HudValue.SHAFT_CORPSES, "2/4 Lapis, Umber")
				.cold(-12)
				.put(HudValue.SHAFT_CAMPFIRES, 1L);
	}

	/** Package-private for tests. */
	HudData cold(int cold) {
		return put(HudValue.SHAFT_COLD, (long) cold);
	}
}

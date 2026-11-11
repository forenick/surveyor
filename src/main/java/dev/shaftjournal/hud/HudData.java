package dev.shaftjournal.hud;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.shaftjournal.detection.Corpse;
import dev.shaftjournal.detection.Shaft;
import dev.shaftjournal.detection.TextPatterns;
import dev.shaftjournal.journal.JournalEntry;
import dev.shaftjournal.journal.JournalStats;
import dev.shaftjournal.journal.JournalStats.SessionSummary;
import dev.shaftjournal.journal.LootSummary;
import dev.shaftjournal.screen.ShaftNames;

/** One moment's values for the HUD, by {@link HudValue}. A missing value is null. Plain Java. */
public final class HudData {
	/** What the editor shows for values that aren't known right now (the mockup's numbers). */
	public static final HudData EXAMPLE = example();

	private final Map<HudValue, Object> values = new EnumMap<>(HudValue.class);

	public Object get(HudValue value) {
		return values.get(value);
	}

	/** This value, or the example when it's missing. */
	public Object getOrExample(HudValue value) {
		Object v = values.get(value);
		return v != null ? v : EXAMPLE.values.get(value);
	}

	private HudData put(HudValue value, Object v) {
		if (v != null) values.put(value, v);
		return this;
	}

	/**
	 * A session's totals. They only change when a shaft is saved, so the HUD keeps them between
	 * refreshes instead of adding up every shaft again.
	 *
	 * @param summary null before the first shaft
	 */
	public record SessionTotals(long shafts, long powder, double fine, Long averageSpawn, SessionSummary summary) {
		public static final SessionTotals NONE = new SessionTotals(0, 0, 0, null, null);

		/** @param entries the finished shafts of one session, in order */
		public static SessionTotals of(List<JournalEntry> entries, TextPatterns patterns) {
			if (entries.isEmpty()) return NONE;
			long powder = 0;
			double fine = 0;
			for (JournalEntry entry : entries) {
				powder += JournalStats.powder(entry);
				LootSummary loot = LootSummary.of(entry.loots(), patterns);
				if (loot.gems() != null) fine += loot.gems().fine();
			}
			SessionSummary summary = new SessionSummary(entries.getFirst().sessionId(), entries);
			return new SessionTotals(entries.size(), powder, fine, summary.averageSearchMillis(), summary);
		}
	}

	/**
	 * @param session    the current session's totals
	 * @param spawnTimer the running or last timed spawn, in milliseconds; null when none
	 * @param shaft      the shaft the player is in, or null
	 * @param now        the same clock as the shaft's times
	 */
	public static HudData live(SessionTotals session, Long spawnTimer, Shaft shaft, long now, ShaftNames names) {
		HudData data = new HudData();
		data.put(HudValue.SESSION_SHAFTS, session.shafts());
		data.put(HudValue.SESSION_POWDER, session.powder());
		data.put(HudValue.SESSION_CORPSE_FINE, session.fine());
		data.put(HudValue.AVERAGE_SPAWN, session.averageSpawn());
		// Counted up to now, so the rate drops while a search drags on.
		if (session.summary() != null) data.put(HudValue.SHAFTS_PER_HOUR, session.summary().shaftsPerHour(now));
		data.put(HudValue.SPAWN_TIMER, spawnTimer);
		if (shaft != null) {
			if (shaft.id() != null) data.put(HudValue.SHAFT_TYPE, names.name(shaft.id().type()));
			data.put(HudValue.SHAFT_TIME, Math.max(0, now - shaft.enteredAt()));
			data.put(HudValue.SHAFT_CORPSES, corpses(shaft.corpses()));
			if (shaft.cold() != null) data.put(HudValue.SHAFT_COLD, shaft.cold() + "❄");
			data.put(HudValue.SHAFT_CAMPFIRES, (long) shaft.campfiresPlaced().size());
		}
		return data;
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
				.put(HudValue.SHAFTS_PER_HOUR, 19.4)
				.put(HudValue.AVERAGE_SPAWN, 85_000L)
				.put(HudValue.SESSION_POWDER, 43_436L)
				.put(HudValue.SESSION_CORPSE_FINE, 15.6)
				.put(HudValue.SPAWN_TIMER, 102_000L)
				.put(HudValue.SHAFT_TYPE, "Jade")
				.put(HudValue.SHAFT_TIME, 134_000L)
				.put(HudValue.SHAFT_CORPSES, "2/4 Lapis, Umber")
				.put(HudValue.SHAFT_COLD, "-12❄")
				.put(HudValue.SHAFT_CAMPFIRES, 1L);
	}
}

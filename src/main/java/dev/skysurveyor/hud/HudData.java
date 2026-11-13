package dev.skysurveyor.hud;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.skysurveyor.data.CorpseType;
import dev.skysurveyor.data.ShaftNames;
import dev.skysurveyor.detection.Corpse;
import dev.skysurveyor.detection.CorpseLoot;
import dev.skysurveyor.detection.Shaft;
import dev.skysurveyor.detection.TextPatterns;
import dev.skysurveyor.hud.HudValue.Range;
import dev.skysurveyor.journal.JournalEntry;
import dev.skysurveyor.journal.JournalStats;
import dev.skysurveyor.journal.JournalStats.SessionSummary;
import dev.skysurveyor.journal.LootSummary;

public final class HudData {
	// mockup numbers, not game data
	public static final HudData EXAMPLE = example();

	private record Key(HudValue value, Range range) {
	}

	private final Map<Key, Object> values = new HashMap<>();
	// real but empty (zeros before the first shaft), editor shows examples
	private final Set<Key> placeholders = new HashSet<>();
	private long now;
	private SessionTotals session = SessionTotals.NONE;

	public long now() {
		return now;
	}

	public Object get(HudValue value, Range range) {
		return values.get(key(value, range));
	}

	public Object getOrExample(HudValue value, Range range) {
		Key key = key(value, range);
		Object v = values.get(key);
		return v != null && !placeholders.contains(key) ? v : EXAMPLE.values.get(key);
	}

	public Double number(HudValue value, Range range, boolean example) {
		Key key = key(value, range);
		HudData from = example && (values.get(key) == null || placeholders.contains(key)) ? EXAMPLE : this;
		return from.values.get(key) instanceof Number n ? n.doubleValue() : null;
	}

	// hand edited files can ask for ranges a value doesnt have
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

		public static Totals of(Shaft shaft, TextPatterns patterns) {
			return new Totals(1, JournalStats.powder(shaft.loots(), shaft.exitPowder()), fine(shaft.loots(), patterns));
		}

		private static double fine(List<CorpseLoot> loots, TextPatterns patterns) {
			LootSummary loot = LootSummary.of(loots, patterns);
			return loot.gems() == null ? 0 : loot.gems().fine();
		}
	}

	public record SessionTotals(Totals totals, SessionSummary summary) {
		public static final SessionTotals NONE = new SessionTotals(Totals.NONE, null);

		public long shafts() {
			return totals.shafts();
		}

		public Long averageSpawn() {
			return summary == null ? null : summary.averageSearchMillis();
		}

		public static SessionTotals of(List<JournalEntry> entries, TextPatterns patterns) {
			if (entries.isEmpty()) return NONE;
			return new SessionTotals(Totals.of(entries, patterns), new SessionSummary(entries.getFirst().sessionId(), entries));
		}
	}

	// now = same clock as the shafts times
	public static HudData live(SessionTotals session, Totals allTime, Totals thisShaft, Long spawnTimer, Shaft shaft,
			long now, ShaftNames names) {
		HudData data = new HudData();
		data.now = now;
		data.session = session;
		Totals totals = session.totals();
		data.put(HudValue.SESSION_SHAFTS, totals.shafts());
		data.put(HudValue.SESSION_POWDER, totals.powder());
		data.put(HudValue.SESSION_CORPSE_FINE, totals.fine());
		// so the editor shows all examples, not mixed w/ zeros
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

	// net coins, null = no prices yet. shaft = live or the last one
	public record Money(Double shaft, Double session, Double allTime, long allTimeShafts) {
	}

	public HudData money(Money money) {
		put(HudValue.COINS, Range.SHAFT, money.shaft());
		put(HudValue.COINS, Range.SESSION, money.session());
		put(HudValue.COINS, Range.ALL_TIME, money.allTime());
		if (money.session() != null && session.shafts() > 0) {
			put(HudValue.COINS_PER_SHAFT, Range.SESSION, money.session() / session.shafts());
			if (session.summary() != null) put(HudValue.COINS, Range.PER_HOUR, session.summary().perHour(money.session(), now));
		}
		if (money.allTime() != null && money.allTimeShafts() > 0) {
			put(HudValue.COINS_PER_SHAFT, Range.ALL_TIME, money.allTime() / money.allTimeShafts());
		}
		return this;
	}

	// null = not known right now
	public record Game(Integer fps, Long ping, Double tps, String clock, String modVersion, String minecraftVersion) {
	}

	public HudData game(Game game) {
		put(HudValue.FPS, game.fps() == null ? null : game.fps().longValue());
		put(HudValue.PING, game.ping());
		put(HudValue.TPS, game.tps());
		put(HudValue.CLOCK, game.clock());
		put(HudValue.MOD_VERSION, game.modVersion());
		return put(HudValue.MINECRAFT_VERSION, game.minecraftVersion());
	}

	private void placeholders(Range range) {
		for (HudValue value : HudValue.values()) {
			if (value.hasRange(range)) placeholders.add(key(value, range));
		}
	}

	// null until the tab list shows them
	static String corpses(List<Corpse> corpses) {
		if (corpses.isEmpty()) return null;
		long looted = corpses.stream().filter(Corpse::looted).count();
		Set<String> types = new LinkedHashSet<>();
		for (String type : CorpseType.ORDER) {
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
				.put(HudValue.SHAFT_CAMPFIRES, 1L)
				.put(HudValue.COINS, Range.SHAFT, 2_431_000.0)
				.put(HudValue.COINS, 14_820_000.0)
				.put(HudValue.COINS, Range.PER_HOUR, 46_800_000.0)
				.put(HudValue.COINS, Range.ALL_TIME, 612_300_000.0)
				.put(HudValue.COINS_PER_SHAFT, 2_470_000.0)
				.put(HudValue.COINS_PER_SHAFT, Range.ALL_TIME, 1_486_000.0)
				.put(HudValue.FPS, 144L)
				.put(HudValue.PING, 42L)
				.put(HudValue.TPS, 19.9);
	}

	// package private for tests
	HudData cold(int cold) {
		return put(HudValue.SHAFT_COLD, (long) cold);
	}
}

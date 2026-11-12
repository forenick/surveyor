package dev.shaftjournal.money;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.shaftjournal.journal.JournalEntry;
import dev.shaftjournal.journal.JournalStats;
import dev.shaftjournal.journal.JournalStats.SessionSummary;
import dev.shaftjournal.money.ShaftValue.CorpseValue;
import dev.shaftjournal.money.ShaftValue.Line;
import dev.shaftjournal.money.ShaftValue.Part;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

// made up coins, not real ones
class MoneyStatsTest {
	private static JournalEntry entry(long session, long entered) {
		return new JournalEntry(JournalEntry.VERSION, session, "JADE_1", entered, 60_000, "BYE", 0, null, List.of(), 0,
				List.of(), List.of(), List.of(), null, 0, 0, Map.of(), Map.of(), Map.of(), null, List.of(), Map.of(), null);
	}

	private static ShaftValue worth(double net, CorpseValue... corpses) {
		return new ShaftValue(List.of(new Line(Part.MINED, "Flawless Jade Gemstone", 1, net)), List.of(corpses));
	}

	@Test
	void totalsBestsAndCorpses() {
		List<JournalEntry> entries = new ArrayList<>();
		Map<JournalEntry, ShaftValue> values = new HashMap<>();
		// session 1: 5 shafts, 2 min apart, 1 min each, 1M each
		for (int i = 0; i < 5; i++) {
			JournalEntry entry = entry(0, i * 120_000L);
			entries.add(entry);
			values.put(entry, worth(1_000_000, new CorpseValue("Umber", 500_000, -300_000, 0)));
		}
		// session 2: 1 rich shaft, too few for the rate record
		JournalEntry rich = entry(10_000_000, 10_000_000);
		entries.add(rich);
		values.put(rich, worth(9_000_000, new CorpseValue("Umber", 100_000, -300_000, 1), new CorpseValue("Lapis", 50_000, 0, 0)));
		// no prices for this one
		entries.add(entry(10_000_000, 10_100_000));
		// left right away, worth nothing
		JournalEntry empty = entry(10_000_000, 10_200_000);
		entries.add(empty);
		values.put(empty, new ShaftValue(List.of(), List.of()));

		List<SessionSummary> sessions = JournalStats.sessions(entries);
		MoneyStats stats = MoneyStats.of(entries, sessions, values::get);
		assertEquals(14_000_000, stats.coins(), 1e-6);
		assertEquals(7, stats.valued(), "the one without prices is left out, the empty one counts");
		assertEquals(2_000_000, stats.perShaft(), 1e-6);
		assertEquals(Map.of(Part.MINED, 14_000_000.0), stats.parts(), "lines summed per part");
		assertSame(rich, stats.bestShaft().entry());
		// 5M over 9 min (start to last shafts end)
		assertSame(sessions.get(1), stats.bestRate().session());
		assertEquals(5_000_000 * 60 / 9.0, stats.bestRate().perHour(), 1e-6);

		MoneyStats.CorpseCoins umber = stats.corpses().get("Umber");
		assertEquals(6, umber.corpses());
		assertEquals(2_600_000, umber.loot(), 1e-6);
		assertEquals(-1_800_000, umber.keys(), 1e-6);
		assertEquals(800_000 / 6.0, umber.each(), 1e-6);
		assertEquals(1, umber.unpriced());
		assertEquals(50_000, stats.corpses().get("Lapis").each(), 1e-6);
	}

	@Test
	void noPricesAtAll() {
		List<JournalEntry> entries = List.of(entry(0, 0), entry(0, 120_000));
		MoneyStats stats = MoneyStats.of(entries, JournalStats.sessions(entries), e -> null);
		assertNull(stats.coins());
		assertNull(stats.perShaft());
		assertNull(stats.bestShaft());
		assertNull(stats.bestRate());
		assertNull(MoneyStats.coins(entries, e -> null));
		assertEquals(3.0, MoneyStats.plus(null, 3.0));
		assertNull(MoneyStats.plus(null, null));
	}
}

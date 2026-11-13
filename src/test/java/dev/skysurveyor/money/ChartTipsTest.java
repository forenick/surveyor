package dev.skysurveyor.money;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.skysurveyor.journal.JournalEntry;
import dev.skysurveyor.journal.JournalStats;
import dev.skysurveyor.journal.JournalStats.SessionSummary;
import dev.skysurveyor.money.ChartTips.SessionCoins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// made up spawns and coins
class ChartTipsTest {
	private static JournalEntry entry(long session, long entered, long search) {
		return new JournalEntry(JournalEntry.VERSION, session, "JADE_1", entered, 60_000, "BYE", 0, search, List.of(), 0,
				List.of(), List.of(), List.of(), null, 0, 0, Map.of(), Map.of(), Map.of(), null, List.of(), Map.of(), null);
	}

	@Test
	void spawnLine() {
		List<JournalEntry> entries = new ArrayList<>();
		// session 1: 60, 80 s, session 2: 100, 120 s
		entries.add(entry(0, 0, 60_000));
		entries.add(entry(0, 120_000, 80_000));
		entries.add(entry(1_000_000, 1_000_000, 100_000));
		entries.add(entry(1_000_000, 1_120_000, 120_000));
		List<SessionSummary> oldestFirst = JournalStats.sessions(entries).reversed();
		List<String> lines = ChartTips.spawn(oldestFirst);
		assertEquals("§fAvg spawn §e1m 30s", lines.getFirst());
		assertTrue(lines.contains("§7From §f4 §7timed shafts in §f2 §7sessions"), lines.toString());
		assertTrue(lines.contains("§7Median shaft §f1m 40s"), "upper middle of 4");
		assertTrue(lines.contains("§7Middle half §f1m 20s §7to §f2m 0s"), lines.toString());
		assertTrue(lines.contains("§7Sessions faster than avg §f1 of 2 §8(50%)"));
		assertTrue(lines.contains("§7Shafts faster than avg §f50%"));
		// latest 1m 50s vs 1m 30s avg
		assertEquals("§7Latest session §f1m 50s §7(§c22% slower§7)", lines.getLast());
	}

	@Test
	void coinLine() {
		// 1 h each: +10M, +30M, -2M, then a 2 min one w/o a rate
		List<SessionCoins> sessions = List.of(new SessionCoins(10e6, 3_600_000, 10e6), new SessionCoins(30e6, 3_600_000, 30e6),
				new SessionCoins(-2e6, 3_600_000, -2e6), new SessionCoins(2e6, 120_000, null));
		List<String> lines = ChartTips.coins(sessions, 3 * 3_600_000 + 120_000);
		// 40M over 3h 2m
		assertEquals("§fAvg coins / h §6+13.2M", lines.getFirst());
		assertTrue(lines.contains("§7Median session §6+10.0M §7/ h"), lines.toString());
		assertTrue(lines.contains("§7Best session §6+30.0M §7/ h, worst §c-2.0M §7/ h"));
		assertTrue(lines.contains("§7Sessions above avg §f1 of 3 §8(33%)"));
		assertTrue(lines.contains("§7Sessions that made coins §f3 of 4 §8(75%)"));
		assertTrue(lines.contains("§7Best session made §f75% §7of all coins"));
		assertEquals("§7Latest session §c-2.0M §7/ h (§c-115%§7 vs avg)", lines.getLast());
	}

	@Test
	void noPricesNoTip() {
		assertEquals(List.of(), ChartTips.coins(List.of(new SessionCoins(null, 3_600_000, null)), 3_600_000));
	}
}

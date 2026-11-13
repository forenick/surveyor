package dev.shaftjournal.money;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.shaftjournal.journal.JournalEntry;
import dev.shaftjournal.journal.JournalStats;
import dev.shaftjournal.money.ShaftValue.CorpseValue;
import dev.shaftjournal.money.ShaftValue.Line;
import dev.shaftjournal.money.ShaftValue.Part;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// made up coins, not real ones
class CoinBreakdownTest {
	private static JournalEntry entry(long entered) {
		return new JournalEntry(JournalEntry.VERSION, 0, "JADE_1", entered, 60_000, "BYE", 0, null, List.of(), 0, List.of(),
				List.of(), List.of(), null, 0, 0, Map.of(), Map.of(), Map.of(), null, List.of(), Map.of(), null);
	}

	private static MoneyStats stats() {
		List<Line> loot = List.of(new Line(Part.CORPSES, "Bejeweled Handle", 1, 600_000.0),
				new Line(Part.CORPSES, "Goblin Egg", 4, null), new Line(Part.CORPSES, "Enchanted Umber", 2, 2_000.0));
		ShaftValue value = new ShaftValue(List.of(new Line(Part.MINED, "Fine Jade Gemstone", 100.5, 3_000_000.0),
				new Line(Part.OTHER, "Coal", 640, 1_000.0), new Line(Part.OTHER, "Mithril", 10, 2_000.0),
				new Line(Part.OTHER, "Glacite", 50, 500.0), new Line(Part.CORPSES, "Bejeweled Handle", 1, 600_000.0),
				new Line(Part.CORPSES, "Goblin Egg", 4, null), new Line(Part.CORPSES, "Enchanted Umber", 2, 2_000.0),
				new Line(Part.KEYS, "Umber Key", 1, -400_000.0)),
				List.of(new CorpseValue("Umber", 602_000, -400_000, 1, loot)));
		List<JournalEntry> entries = List.of(entry(0), entry(120_000));
		return MoneyStats.of(entries, JournalStats.sessions(entries), e -> value);
	}

	@Test
	void itemsSummedOverShafts() {
		MoneyStats stats = stats();
		assertEquals(201, stats.items().get(Part.MINED).get("Fine Jade Gemstone").count(), 1e-9);
		assertEquals(null, stats.items().get(Part.CORPSES).get("Goblin Egg").coins());
		MoneyStats.CorpseCoins umber = stats.corpses().get("Umber");
		assertEquals(2, umber.items().get("Bejeweled Handle").count(), 1e-9);
		assertEquals(1_200_000, umber.items().get("Bejeweled Handle").coins(), 1e-6);
	}

	@Test
	void detailedSharesAndCosts() {
		List<String> lines = CoinBreakdown.detailed(stats(), null, 0, 1000);
		// income 3M + 3.5k + 602k per shaft
		assertTrue(lines.contains("§fMined gems §6+6.0M §883%"), lines.toString());
		assertTrue(lines.contains("  §7≈201.0 Fine Jade Gemstone §6+6.0M"), lines.toString());
		assertTrue(lines.contains("§fOther drops §6+7.0k §8<1%"), lines.toString());
		assertTrue(lines.contains("  §72 Umber corpses §6+1.2M"), lines.toString());
		assertTrue(lines.contains("§fKeys §c-800.0k"), "no share on costs");
		assertTrue(lines.contains("§7Spent §c-800.0k"));
		assertTrue(lines.contains("§c1 item without a price"));
		assertEquals(CoinBreakdown.SHIFT_HINT, lines.getLast());
		assertEquals("", lines.get(lines.size() - 2), "gap before the hint");
	}

	@Test
	void itemsCutToFit() {
		MoneyStats stats = stats();
		List<String> all = CoinBreakdown.detailed(stats, null, 0, 1000);
		assertTrue(all.contains("  §71,280 Coal §6+2.0k"), "3 other drops all shown");
		List<String> cut = CoinBreakdown.detailed(stats, null, 0, CoinBreakdown.height(all) - 1);
		assertTrue(CoinBreakdown.height(cut) < CoinBreakdown.height(all));
		assertTrue(cut.stream().anyMatch(l -> l.startsWith("  §8") && l.contains(" more ")), cut.toString());
	}

	@Test
	void corpseKeepsTotal() {
		MoneyStats stats = stats();
		List<String> lines = CoinBreakdown.corpse("Umber", 3, stats.corpses().get("Umber"), 1000);
		assertTrue(lines.contains("§7Total §6+404.0k §8(2 of 3 with prices)"), lines.toString());
		assertTrue(lines.contains("§7Avg §6+202.0k §7each"));
		assertTrue(lines.contains("  §7Key §c-400.0k §7each"));
		assertEquals("§72 Bejeweled Handle §6+1.2M", lines.get(lines.indexOf("§fLoot") + 1), "biggest first");
		assertTrue(lines.contains("§78 Goblin Egg §8no price"), "no price last");
		List<String> tiny = CoinBreakdown.corpse("Umber", 3, stats.corpses().get("Umber"), 60);
		assertFalse(tiny.contains("§fLoot"));
	}
}

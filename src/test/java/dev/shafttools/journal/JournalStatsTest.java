package dev.shafttools.journal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.shafttools.detection.Corpse;
import dev.shafttools.detection.CorpseLoot;
import dev.shafttools.detection.ItemCount;
import dev.shafttools.detection.TextPatterns;

/** Uses Nick's real journal from 2026-11-11 (recording 4's six shafts, saved by 0.2.0). */
class JournalStatsTest {
	private static List<JournalEntry> realJournal() throws IOException, URISyntaxException {
		Path path = Path.of(JournalStatsTest.class.getResource("/journal-2026-11-11.jsonl").toURI());
		return new JournalFile(path).read();
	}

	@Test
	void summarisesTheRealSession() throws Exception {
		List<JournalStats.SessionSummary> sessions = JournalStats.sessions(realJournal());
		assertEquals(1, sessions.size());
		JournalStats.SessionSummary session = sessions.getFirst();
		assertEquals(6, session.shafts());
		// Corpses 14,527 + 10,861 + 4,684 + 5,574, plus exits 1,000 + 1,000 + 1,760 + 1,520 + 1,250 + 1,260.
		assertEquals(43_436, session.powder());
		assertEquals(7, session.looted("Lapis"));
		// 17:16:29 (first block hit) to leaving ONYX_1 12.6 s after 17:34:51.
		assertEquals(18 * 60 + 34, session.lengthMillis() / 1000);
		assertEquals(19.4, session.shaftsPerHour(), 0.05);
	}

	@Test
	void mergesGemsBelowFlawlessIntoFine() throws Exception {
		JournalEntry amethyst = realJournal().get(2);
		assertEquals("AMET_1", amethyst.shaftId());
		LootSummary loot = LootSummary.of(amethyst.loots(), TextPatterns.defaults());
		// 80 + 60 + 20 Flawed = 2 Fine, plus 2 Fine Aquamarine.
		assertEquals(4.0, loot.gems().fine(), 1e-9);
		assertEquals(new ItemCount("Flawed Peridot Gemstone", 80), loot.gems().parts().getFirst());
		assertEquals(4, loot.gems().parts().size());
		assertEquals(List.of(
				new ItemCount("Red Goblin Egg", 8),
				new ItemCount("Enchanted Tungsten", 7),
				new ItemCount("Goblin Egg", 1),
				new ItemCount("Enchanted Umber", 1),
				new ItemCount("Suspicious Scrap", 1)), loot.items());
	}

	@Test
	void flawlessGemsKeepTheirOwnSlot() {
		CorpseLoot loot = new CorpseLoot("Vanguard", false, List.of(
				new ItemCount("Rough Jade Gemstone", 3200),
				new ItemCount("Flawless Jade Gemstone", 1)), 0);
		LootSummary summary = LootSummary.of(List.of(loot), TextPatterns.defaults());
		assertEquals(0.5, summary.gems().fine(), 1e-9);
		assertEquals(List.of(new ItemCount("Flawless Jade Gemstone", 1)), summary.items());
		assertNull(LootSummary.of(List.of(), TextPatterns.defaults()).gems());
	}

	@Test
	void countsCorpsesPerTypeInDisplayOrder() throws Exception {
		JournalEntry titanium = realJournal().get(3);
		assertEquals(Map.of(
				"Lapis", new JournalStats.CorpseCount(2, 2),
				"Umber", new JournalStats.CorpseCount(0, 1),
				"Tungsten", new JournalStats.CorpseCount(0, 1)), JournalStats.corpses(titanium));
		assertEquals(List.of("Lapis", "Umber", "Tungsten"), List.copyOf(JournalStats.corpses(titanium).keySet()));
	}

	@Test
	void allTimeStatsOverTwoSessions() throws Exception {
		List<JournalEntry> entries = new ArrayList<>(realJournal());
		long later = 1_794_500_000_000L;
		entries.add(entry(later, later + 60_000, "JADE_C", 30_000L, 50_000,
				List.of(new Corpse("Lapis", true), new Corpse("Lapis", true), new Corpse("Vanguard", true))));
		JournalStats.AllTime stats = JournalStats.allTime(entries);
		assertEquals(7, stats.shafts());
		assertEquals(2, stats.sessions());
		assertEquals(43_436 + 50_000, stats.powder());
		assertEquals(Map.of("Lapis", 9, "Vanguard", 1), stats.looted());
		assertEquals("JADE_C", stats.mostPowder().entry().shaftId());
		assertEquals(3, stats.mostLapis().value());
		assertEquals("AMET_1", stats.mostLapis().entry().shaftId());
		// The real file still has 0.2.0's times; PERI_1 (8.9 s) is the fastest.
		assertEquals("PERI_1", stats.fastest().shaftId());
		assertEquals(Map.of("1", 5, "C", 2), stats.variants());
		assertEquals(9, stats.corpsesSeen().get("Lapis"));
		assertEquals(List.of("Lapis", "Umber", "Tungsten", "Vanguard"), List.copyOf(stats.corpsesSeen().keySet()));
		// The one-minute session is too short for a rate, so the real session is the best.
		assertEquals(realJournal().getFirst().sessionId(), stats.bestRate().id());
		assertEquals(realJournal().getFirst().sessionId(), stats.oldestFirst().getFirst().id());
	}

	private static JournalEntry entry(long session, long entered, String id, Long search, long exitPowder, List<Corpse> corpses) {
		return new JournalEntry(JournalEntry.VERSION, session, id, entered, 20_000, "BYE", exitPowder, search,
				corpses, 0, List.of(), List.of(), List.of(), null, 0, 0, Map.of(), Map.of(), Map.of(), null);
	}
}

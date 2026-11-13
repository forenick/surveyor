package dev.shaftjournal.journal;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.shaftjournal.detection.Corpse;
import dev.shaftjournal.detection.CorpseLoot;
import dev.shaftjournal.detection.ItemCount;
import dev.shaftjournal.detection.TextPatterns;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

// nicks real journal 2026-11-11 (recording 4, saved by 0.2.0)
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
		// corpses 14,527+10,861+4,684+5,574, exits 1,000+1,000+1,760+1,520+1,250+1,260
		assertEquals(43_436, session.powder());
		assertEquals(7, session.looted("Lapis"));
		// 17:16:29 first hit to ONYX_1 exit (17:34:51 + 12.6 s)
		assertEquals(18 * 60 + 34, session.lengthMillis() / 1000);
		assertEquals(19.4, session.shaftsPerHour(), 0.05);
	}

	@Test
	void dropChancesFromTheRealSession() throws Exception {
		JournalStats.Drops lapis = JournalStats.allTime(realJournal()).drops().get("Lapis");
		// 7 lapis loot messages in the fixture, umber in 3, peridot 80+60
		assertEquals(7, lapis.corpses());
		assertEquals(new JournalStats.ItemDrop(3, 3), lapis.items().get("Enchanted Umber"));
		assertEquals(new JournalStats.ItemDrop(2, 140), lapis.items().get("Flawed Peridot Gemstone"));
		assertEquals(new JournalStats.ItemDrop(1, 1), lapis.items().get("Bejeweled Handle"));
		assertEquals(3 / 7.0, lapis.items().get("Enchanted Umber").chance(lapis.corpses()), 1e-9);
	}

	@Test
	void sameItemTwiceInOneCorpseCountsOnce() {
		CorpseLoot loot = new CorpseLoot("Umber", false, List.of(new ItemCount("Enchanted Umber", 2),
				new ItemCount("Enchanted Umber", 3)), 0);
		JournalEntry entry = new JournalEntry(JournalEntry.VERSION, 0, "JADE_1", 0, 60_000, "BYE", 0, null, List.of(), 0,
				List.of(loot), List.of(), List.of(), null, 0, 0, Map.of(), Map.of(), Map.of(), null, List.of(), Map.of(), null);
		JournalStats.Drops umber = JournalStats.allTime(List.of(entry)).drops().get("Umber");
		assertEquals(new JournalStats.ItemDrop(1, 5), umber.items().get("Enchanted Umber"));
	}

	@Test
	void mergesGemsBelowFlawlessIntoFine() throws Exception {
		JournalEntry amethyst = realJournal().get(2);
		assertEquals("AMET_1", amethyst.shaftId());
		LootSummary loot = LootSummary.of(amethyst.loots(), TextPatterns.defaults());
		// 80+60+20 Flawed = 2 Fine, + 2 Fine Aquamarine
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
		// file still has 0.2.0 times
		assertEquals("PERI_1", stats.fastest().shaftId());
		assertEquals(Map.of("1", 1, "C", 1), stats.types().get("JADE"));
		assertEquals(Map.of("C", 1), stats.types().get("AQUA"));
		assertEquals(7, stats.typedShafts());
		assertEquals(List.of("JADE", "PERI", "AMET", "TITA", "AQUA", "ONYX"), List.copyOf(stats.types().keySet()));
		assertEquals(5, stats.shaftsWithCorpse().get("Lapis"));
		assertEquals(4, stats.shaftsWithCorpse().get("Umber"));
		assertEquals(9, stats.corpsesSeen().get("Lapis"));
		assertEquals(List.of("Lapis", "Umber", "Tungsten", "Vanguard"), List.copyOf(stats.corpsesSeen().keySet()));
		// 1 min session too short for a rate
		assertEquals(realJournal().getFirst().sessionId(), stats.bestRate().id());
		// added session: 1 timed spawn, too few
		assertEquals(realJournal().getFirst().sessionId(), stats.fastestSession().id());
		assertEquals(6, stats.mostShafts().shafts());
		assertNull(stats.mostGems());
		List<JournalStats.SessionSummary> sessions = JournalStats.sessions(entries);
		assertEquals(30_000L, JournalStats.recentAverage(sessions, 1));
		assertEquals(JournalStats.averageSearch(entries), JournalStats.recentAverage(sessions, 5));
		assertEquals(realJournal().getFirst().sessionId(), stats.oldestFirst().getFirst().id());
	}

	@Test
	void shortLuckySessionsDontHoldTheRateRecord() throws Exception {
		List<JournalEntry> entries = new ArrayList<>(realJournal());
		long later = 1_794_500_000_000L;
		// 40/h beats 19.4/h but too few shafts
		for (int i = 0; i < 4; i++) {
			entries.add(entry(later, later + 60_000 + i * 90_000L, "JADE_1", 30_000L, 1_000, List.of()));
		}
		assertEquals(realJournal().getFirst().sessionId(), JournalStats.allTime(entries).bestRate().id());
		entries.add(entry(later, later + 340_000, "JADE_1", 30_000L, 1_000, List.of()));
		assertEquals(later, JournalStats.allTime(entries).bestRate().id());
	}

	private static JournalEntry entry(long session, long entered, String id, Long search, long exitPowder, List<Corpse> corpses) {
		return new JournalEntry(JournalEntry.VERSION, session, id, entered, 20_000, "BYE", exitPowder, search,
				corpses, 0, List.of(), List.of(), List.of(), null, 0, 0, Map.of(), Map.of(), Map.of(), null, null, null, null);
	}
}

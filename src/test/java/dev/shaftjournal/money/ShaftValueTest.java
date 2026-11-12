package dev.shaftjournal.money;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import dev.shaftjournal.detection.CorpseLoot;
import dev.shaftjournal.detection.ItemCount;
import dev.shaftjournal.detection.TextPatterns;
import dev.shaftjournal.journal.GemYield;
import dev.shaftjournal.journal.JournalEntry;
import dev.shaftjournal.journal.SummaryText;
import dev.shaftjournal.money.MoneySettings.Crystals;
import dev.shaftjournal.money.MoneySettings.GemTier;
import dev.shaftjournal.money.MoneySettings.OldShafts;
import dev.shaftjournal.money.MoneySettings.SellPrice;
import dev.shaftjournal.money.ShaftValue.Line;
import dev.shaftjournal.money.ShaftValue.Part;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// round made up prices so every number can be worked by hand, not real ones
class ShaftValueTest {
	private static final double TAX = 1 - 1.125 / 100;
	private static final Map<String, ItemPrice> PRICES = Map.ofEntries(
			Map.entry("FLAWLESS_JADE_GEM", bz(2_400_000, 2_600_000)),
			Map.entry("FINE_JADE_GEM", bz(28_000, 30_000)),
			Map.entry("FLAWLESS_ONYX_GEM", bz(2_000_000, 2_200_000)),
			Map.entry("PERFECT_JADE_GEM", bz(15_000_000, 16_000_000)),
			Map.entry("GOBLIN_EGG", bz(3_000, 4_000)),
			Map.entry("ENCHANTED_TUNGSTEN", bz(10_000, 11_000)),
			Map.entry("SUSPICIOUS_SCRAP", bz(140_000, 146_000)),
			Map.entry("TUNGSTEN_KEY", bz(2_600_000, 2_800_000)),
			Map.entry("UMBER_KEY", bz(2_900_000, 3_000_000)),
			Map.entry("REFINED_UMBER", bz(2_400_000, 2_500_000)),
			Map.entry("MATCH_STICKS", bz(30, 900)),
			Map.entry("GLOSSY_GEMSTONE", bz(50_000, 55_000)),
			Map.entry("CAGED_WISP", new ItemPrice(null, null, 60_000_000.0)),
			// no buy orders, like redstone on 2026-11-12
			Map.entry("REDSTONE", new ItemPrice(null, 4.0, null, 1.0)),
			Map.entry("PIXIE_DUST", new ItemPrice(null, null, null, 5.0)));
	private static final ItemIds IDS = ItemIds.bundled(Map.of("Glossy Gemstone", "GLOSSY_GEMSTONE", "Redstone Dust", "REDSTONE",
			"Pixie Dust", "PIXIE_DUST"));
	private static final TextPatterns PATTERNS = TextPatterns.defaults();

	private static ItemPrice bz(double buyOrder, double sellOffer) {
		return new ItemPrice(buyOrder, sellOffer, null);
	}

	// 800 flawed = 10 fine jade
	private static JournalEntry entry() {
		List<CorpseLoot> loots = List.of(
				new CorpseLoot("Lapis", false, List.of(new ItemCount("Flawed Onyx Gemstone", 20), new ItemCount("Goblin Egg", 4)), 5_000),
				new CorpseLoot("Tungsten", false, List.of(new ItemCount("Enchanted Tungsten", 8), new ItemCount("Caged Wisp", 1)), 9_000));
		Map<String, Long> other = new HashMap<>();
		other.put("Rough Sapphire Gemstone", 6_400L);
		other.put("Glossy Gemstone", 2L);
		return new JournalEntry(JournalEntry.VERSION, 0, "JADE_1", 0, 600_000, "BYE", 1_000, null, List.of(), 0, loots,
				List.of(), List.of(300_000L), null, 2, 0, Map.of(), Map.of(), Map.of(),
				new GemYield("Jade", 0, 800, 0, 0, 0), List.of("Jade Crystal"), other, null);
	}

	private static ShaftValue value(MoneySettings settings) {
		return ShaftValue.of(entry(), PRICES::get, settings, IDS, PATTERNS);
	}

	@Test
	void everyPartByHand() {
		ShaftValue value = value(MoneySettings.DEFAULTS);
		// 10 fine = 1/8 flawless
		assertEquals(300_000 * TAX, value.total(Part.MINED), 1e-6);
		// glossy only, rough sapphire has no flawless price
		assertEquals(100_000 * TAX, value.total(Part.OTHER), 1e-6);
		// 20 flawed onyx = 1/4 fine = 6,250, eggs 12k, tungsten 80k taxed, wisp = lowest bin w/o tax
		assertEquals((6_250 + 12_000 + 80_000) * TAX + 60_000_000, value.total(Part.CORPSES), 1e-6);
		assertEquals(280_000 * TAX, value.total(Part.SCRAP), 1e-6);
		// 15M perfect - 5 x 2.4M flawless
		assertEquals(3_000_000 * TAX, value.total(Part.CRYSTALS), 1e-6);
		// insta-buy, no tax
		assertEquals(-2_800_000, value.total(Part.KEYS), 1e-6);
		// refined umber + 16 match-sticks at insta-buy
		assertEquals(-(2_500_000 + 16 * 900), value.total(Part.CAMPFIRES), 1e-6);
		assertEquals(58_421_344.6875, value.net(), 1e-6);

		assertEquals(List.of(new Line(Part.OTHER, "Rough Sapphire Gemstone", 6_400, null)), value.unpriced());
		assertEquals(new Line(Part.MINED, "Flawless Jade Gemstone", 0.125, 300_000 * TAX), value.lines().getFirst());
	}

	@Test
	void settingsChangeTheValue() {
		MoneySettings fine = MoneySettings.DEFAULTS.withGemTier(GemTier.FINE);
		assertEquals(280_000 * TAX, value(fine).total(Part.MINED), 1e-6);
		assertTrue(value(fine).unpriced().stream().anyMatch(l -> l.item().equals("Flawed Onyx Gemstone")), "no fine onyx price");

		MoneySettings offer = new MoneySettings(SellPrice.SELL_OFFER, GemTier.FLAWLESS, 1.125, OldShafts.THAT_DAY,
				Crystals.PERFECT_MINUS_FLAWLESS);
		assertEquals(325_000 * TAX, value(offer).total(Part.MINED), 1e-6);
		assertEquals(-2_800_000, value(offer).total(Part.KEYS), 1e-6, "keys stay insta-buy");

		MoneySettings noTax = new MoneySettings(SellPrice.INSTA_SELL, GemTier.FLAWLESS, 0.0, OldShafts.THAT_DAY,
				Crystals.ZERO);
		assertEquals(300_000, value(noTax).total(Part.MINED), 1e-6);
		assertEquals(0, value(noTax).total(Part.CRYSTALS), 1e-6);
		assertFalse(value(noTax).unpriced().stream().anyMatch(l -> l.part() == Part.CRYSTALS));
	}

	@Test
	void npcPriceOnlyWhenNothingElse() {
		JournalEntry e = entry();
		Map<String, Long> drops = Map.of("Redstone Dust", 100L, "Pixie Dust", 2L);
		JournalEntry withDrops = new JournalEntry(e.version(), e.sessionId(), e.shaftId(), e.enteredAt(), e.durationMillis(),
				e.exit(), e.exitPowder(), null, e.corpses(), 0, List.of(), List.of(), List.of(), null, 0, 0, Map.of(), Map.of(),
				Map.of(), null, List.of(), drops, null);
		ShaftValue value = ShaftValue.of(withDrops, PRICES::get, MoneySettings.DEFAULTS, IDS, PATTERNS);
		// npc, no bazaar tax
		assertEquals(100 + 10, value.total(Part.OTHER), 1e-6);
		MoneySettings offer = new MoneySettings(SellPrice.SELL_OFFER, GemTier.FLAWLESS, 1.125, OldShafts.THAT_DAY,
				Crystals.ZERO);
		assertEquals(400 * TAX + 10, ShaftValue.of(withDrops, PRICES::get, offer, IDS, PATTERNS).total(Part.OTHER), 1e-6);
	}

	@Test
	void savedPricesWinOverToday() {
		JournalEntry entry = entry();
		Map<String, ItemPrice> saved = ShaftValue.snapshot(entry, PRICES::get, MoneySettings.DEFAULTS.withCrystals(Crystals.ZERO), IDS, PATTERNS);
		// both gem tiers + perfect even w/ crystals off, nothing the shaft didnt use
		for (String id : List.of("FINE_JADE_GEM", "FLAWLESS_JADE_GEM", "PERFECT_JADE_GEM", "FLAWLESS_ONYX_GEM", "TUNGSTEN_KEY",
				"REFINED_UMBER", "MATCH_STICKS", "SUSPICIOUS_SCRAP", "GOBLIN_EGG", "ENCHANTED_TUNGSTEN", "CAGED_WISP",
				"GLOSSY_GEMSTONE")) {
			assertTrue(saved.containsKey(id), id);
		}
		assertFalse(saved.containsKey("UMBER_KEY"));
		assertEquals(12, saved.size());

		Map<String, ItemPrice> older = Map.of("FLAWLESS_JADE_GEM", bz(4_000_000, 4_100_000));
		ShaftValue value = ShaftValue.of(entry, ShaftValue.layered(older, PRICES::get), MoneySettings.DEFAULTS, IDS, PATTERNS);
		assertEquals(500_000 * TAX, value.total(Part.MINED), 1e-6);
		assertEquals(-2_800_000, value.total(Part.KEYS), 1e-6, "missing from saved = todays");
	}

	@Test
	void oldEntriesGuessOtherDrops() {
		JournalEntry e = entry();
		Map<String, Long> sacks = Map.of("Rough Jade Gemstone", 1_000L, "Flawed Onyx Gemstone", 20L, "Glossy Gemstone", 2L);
		JournalEntry old = new JournalEntry(e.version(), e.sessionId(), e.shaftId(), e.enteredAt(), e.durationMillis(), e.exit(),
				e.exitPowder(), null, e.corpses(), 0, e.loots(), e.mayhemBuffs(), e.campfiresAt(), null, 2, 0, Map.of(), sacks,
				Map.of(), e.gems(), null, null, null);
		ShaftValue value = ShaftValue.of(old, PRICES::get, MoneySettings.DEFAULTS, IDS, PATTERNS);
		assertEquals(List.of(new Line(Part.OTHER, "Glossy Gemstone", 2, 100_000 * TAX)),
				value.lines().stream().filter(l -> l.part() == Part.OTHER).toList());
		assertTrue(value.lines().stream().noneMatch(l -> l.part() == Part.CRYSTALS));
	}

	@Test
	void oldEntriesDropSupercraftInputs() {
		JournalEntry e = entry();
		// 160 glacite supercrafted into 1 enchanted, made up counts
		Map<String, Long> added = Map.of("Glacite", 160L, "Enchanted Glacite", 1L, "Glossy Gemstone", 2L);
		Map<String, Long> removed = Map.of("Glacite", 160L);
		JournalEntry old = new JournalEntry(e.version(), e.sessionId(), e.shaftId(), e.enteredAt(), e.durationMillis(), e.exit(),
				e.exitPowder(), null, e.corpses(), 0, List.of(), e.mayhemBuffs(), e.campfiresAt(), null, 2, 0, Map.of(), added,
				removed, e.gems(), null, null, null);
		assertEquals(Map.of("Enchanted Glacite", 1L, "Glossy Gemstone", 2L), ShaftValue.otherDrops(old, PATTERNS));
	}

	@Test
	void onlyGemCrystalsUseTheCrystalSetting() {
		JournalEntry e = entry();
		// made up item + price
		ItemIds ids = ItemIds.bundled(Map.of("Frozen Crystal", "FROZEN_CRYSTAL"));
		Map<String, ItemPrice> prices = new HashMap<>(PRICES);
		prices.put("FROZEN_CRYSTAL", bz(1_000, 1_100));
		JournalEntry withDrops = new JournalEntry(e.version(), e.sessionId(), e.shaftId(), e.enteredAt(), e.durationMillis(),
				e.exit(), e.exitPowder(), null, e.corpses(), 0, List.of(), List.of(), List.of(), null, 0, 0, Map.of(), Map.of(),
				Map.of(), null, List.of(), Map.of("Frozen Crystal", 3L), null);
		MoneySettings zero = MoneySettings.DEFAULTS.withCrystals(Crystals.ZERO);
		assertEquals(3_000 * TAX, ShaftValue.of(withDrops, prices::get, zero, ids, PATTERNS).total(Part.OTHER), 1e-6);
	}

	@Test
	void snapshotsNeedFreshBazaarPrices() {
		long at = 1_000_000_000L;
		PriceBook bazaar = PriceBook.EMPTY.withBazaar(Map.of("FINE_JADE_GEM", bz(28_000, 30_000)), at);
		assertTrue(LiveMoney.freshForSnapshot(bazaar, at + 30 * 60_000L));
		assertFalse(LiveMoney.freshForSnapshot(bazaar, at + LiveMoney.SNAPSHOT_MAX_AGE + 1), "refresh failed, cached prices");
		assertFalse(LiveMoney.freshForSnapshot(PriceBook.EMPTY.withBins(Map.of("CAGED_WISP", 60_000_000.0), at), at),
				"no bazaar = gems at npc price");
		assertFalse(LiveMoney.freshForSnapshot(PriceBook.EMPTY, at));
	}

	@Test
	void pricesSaveAsShortArrays() {
		Gson gson = new Gson();
		assertEquals("[2400000,2600000.5]", gson.toJson(bz(2_400_000, 2_600_000.5)));
		assertEquals("[null,null,null,5]", gson.toJson(new ItemPrice(null, null, null, 5.0)));
		assertEquals(new ItemPrice(null, null, 60_000_000.0), gson.fromJson("[null,null,60000000]", ItemPrice.class));
		// 0.14.0 + 0.14.1 journal entries
		assertEquals(new ItemPrice(2_400_000.0, 2_600_000.0, null, 1.0),
				gson.fromJson("{\"buyOrder\":2400000.0,\"sellOffer\":2600000.0,\"npcSell\":1.0}", ItemPrice.class));
		Map<String, ItemPrice> saved = Map.of("FINE_JADE_GEM", bz(28_000, 30_000));
		String json = gson.toJson(entry().withPrices(saved));
		assertTrue(json.contains("\"prices\":{\"FINE_JADE_GEM\":[28000,30000]}"), json);
		assertEquals(saved, gson.fromJson(json, JournalEntry.class).prices());
	}

	@Test
	void chatSummaryLine() {
		List<String> lines = SummaryText.lines(entry(), null, value(MoneySettings.DEFAULTS));
		String coins = lines.stream().filter(l -> l.contains("Coins")).findFirst().orElseThrow();
		assertEquals(" §7Coins §6+58.4M §8(gems 296.6k, other 98.9k, corpses 60.1M, scrap 276.9k, crystals 3.0M, keys -2.8M,"
				+ " campfires -2.5M) §c1 item without a price", coins);
		assertNull(lines.stream().filter(l -> l.contains("no prices")).findFirst().orElse(null));
	}
}

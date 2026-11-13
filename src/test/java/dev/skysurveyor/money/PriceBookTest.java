package dev.skysurveyor.money;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import dev.skysurveyor.settings.MoneySettings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// fixtures = real api answers from 2026-11-12, trimmed
class PriceBookTest {
	static JsonObject json(String resource) throws IOException {
		try (InputStream in = PriceBookTest.class.getResourceAsStream(resource)) {
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		}
	}

	@Test
	void bazaarTopOfTheOrderBook() throws IOException {
		Map<String, ItemPrice> bazaar = PriceBook.parseBazaar(json("/bazaar-2026-11-12.json"));
		// sell_summary = buy orders, buy_summary = sell offers
		assertEquals(new ItemPrice(47420.4, 50872.0, null), bazaar.get("FINE_JASPER_GEM"));
		assertEquals(new ItemPrice(28.4, 902.5, null), bazaar.get("MATCH_STICKS"));
		assertEquals(new ItemPrice(null, 902.5, null), bazaar.get("EMPTY_SIDE"), "no buy orders = no insta-sell");
	}

	@Test
	void lowestBinsAndCacheRoundTrip() throws IOException {
		PriceBook book = PriceBook.EMPTY
				.withBazaar(PriceBook.parseBazaar(json("/bazaar-2026-11-12.json")), 1_000)
				.withBins(PriceBook.parseBins(json("/lowestbin-2026-11-12.json")), 2_000);
		assertEquals(new ItemPrice(null, null, 3_499_000.0), book.get("PORTABLE_CAMPFIRE"));
		assertNull(book.get("JASPER_CRYSTAL"));
		assertFalse(book.isEmpty());

		PriceBook loaded = PriceBook.fromJson(book.toJson());
		assertEquals(book.get("FINE_JASPER_GEM"), loaded.get("FINE_JASPER_GEM"));
		assertEquals(book.get("EMPTY_SIDE"), loaded.get("EMPTY_SIDE"));
		assertEquals(book.get("CAGED_WISP"), loaded.get("CAGED_WISP"));
		assertEquals(1_000, loaded.bazaarAt());
		assertEquals(2_000, loaded.binsAt());
	}

	@Test
	void hypixelItemNamesSkipAmbiguousOnes() {
		JsonObject json = JsonParser.parseString("""
				{"success":true,"items":[
				 {"name":"Glossy Gemstone","id":"GLOSSY_GEMSTONE"},
				 {"name":"§dPoem of True Love","id":"POEM_OF_TRUE_LOVE"},
				 {"name":"Hay Bale","id":"HAY_BALE"},{"name":"Hay Bale","id":"HAY_BLOCK"}]}
				""").getAsJsonObject();
		Map<String, String> ids = ItemIds.parseHypixelItems(json);
		assertEquals("GLOSSY_GEMSTONE", ids.get("Glossy Gemstone"));
		assertEquals("POEM_OF_TRUE_LOVE", ids.get("Poem of True Love"));
		assertFalse(ids.containsKey("Hay Bale"));
		assertTrue(ids.size() == 2);
	}

	@Test
	void npcPricesFromTheItemList() {
		JsonObject json = JsonParser.parseString("""
				{"success":true,"items":[
				 {"name":"Pixie Dust","id":"PIXIE_DUST","npc_sell_price":5,"soulbound":"SOLO"},
				 {"name":"Suspicious Scrap","id":"SUSPICIOUS_SCRAP"}]}
				""").getAsJsonObject();
		PriceBook book = PriceBook.EMPTY.withNpc(ItemIds.parseNpcPrices(json));
		assertEquals(new ItemPrice(null, null, null, 5.0), book.get("PIXIE_DUST"));
		assertNull(book.get("SUSPICIOUS_SCRAP"));
		assertTrue(book.isEmpty(), "npc alone isnt real prices yet");
	}

	@Test
	void settingsRepairHandEdits() {
		MoneySettings broken = new MoneySettings(null, MoneySettings.GemTier.FINE, -3.0, null, null, null);
		MoneySettings repaired = broken.repaired();
		assertEquals(MoneySettings.GemTier.FINE, repaired.gemTier());
		assertEquals(MoneySettings.DEFAULTS.sellPrice(), repaired.sellPrice());
		assertEquals(1.125, repaired.bazaarTax());
		assertEquals(MoneySettings.DEFAULTS.crystals(), repaired.crystals());
	}

	@Test
	void coinFormats() {
		assertEquals("999", Coins.format(999.4));
		assertEquals("1.0k", Coins.format(999.5));
		assertEquals("1.0M", Coins.format(999_950));
		assertEquals("568.0M", Coins.format(568_000_000));
		assertEquals("1.2B", Coins.format(1_234_000_000));
		assertEquals("+12.3M", Coins.signed(12_340_000));
		assertEquals("-2.8M", Coins.signed(-2_800_000));
		assertEquals("0", Coins.signed(0.2));
		assertEquals("1M", Coins.whole(1_000_000));
		assertEquals("150k", Coins.whole(150_000));
		assertEquals("1.5M", Coins.whole(1_500_000));
		assertEquals("-5k", Coins.whole(-5_000));
		assertEquals("100", Coins.whole(100));
	}
}
